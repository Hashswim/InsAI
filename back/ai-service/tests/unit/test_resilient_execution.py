from concurrent.futures import ThreadPoolExecutor
from threading import Barrier
from unittest.mock import Mock

import pytest
from conftest import FakeProvider
from pydantic import ValidationError

from app.application.resilient_execution import ExecutionFailure, ResilientExecutor
from app.core.config import Settings
from app.core.errors import (
    CircuitOpen,
    InvalidProviderResponse,
    ProviderFailure,
    ProviderTimeout,
    TransientProviderFailure,
)
from app.providers.circuit_breaker import CircuitBreaker
from app.providers.routing_policy import route_candidates


class Clock:
    def __init__(self):
        self.now = 0
        self.delays = []

    def __call__(self):
        return self.now

    def sleep(self, delay):
        self.delays.append(delay)
        self.now += delay


@pytest.fixture
def harness(catalog_entry, catalog_path, sample_request):
    def create(*, errors=None, **changes):
        path = catalog_path([catalog_entry(model="first"), catalog_entry(model="second")])
        config = Settings(
            **{
                "app_env": "local",
                "jev_provider": "gemini",
                "jev_service_token": "synthetic-token",
                "google_api_key": "synthetic-key",
                "gemini_model": "first",
                "jev_model_catalog_path": path,
                "jev_routing_policy": "priority",
                "jev_model_priority": ("first", "second"),
                **changes,
            }
        )
        providers = {}
        result = FakeProvider().analyze(sample_request)
        for model in ("first", "second"):
            provider = FakeProvider()
            provider.model = model
            provider.analyze = Mock(return_value=result)
            if errors and model in errors:
                provider.analyze.side_effect = errors[model]
            providers[model] = provider
        factory = Mock(side_effect=lambda route: providers[route.profile.model])
        clock = Clock()
        executor = ResilientExecutor(
            config,
            route_candidates(config),
            providers["first"],
            factory,
            clock=clock,
            sleeper=clock.sleep,
            jitter=lambda low, high: high,
        )
        return executor, providers, factory, clock

    return create


def test_default_does_not_replay_or_fallback(harness, sample_request):
    executor, providers, factory, clock = harness(errors={"first": TransientProviderFailure()})
    with pytest.raises(ExecutionFailure) as caught:
        executor.execute(sample_request)
    assert caught.value.stats.attempts == 1
    assert caught.value.route.profile.model == "first"
    providers["first"].analyze.assert_called_once()
    providers["second"].analyze.assert_not_called()
    factory.assert_not_called()
    assert clock.delays == []


@pytest.mark.parametrize("stage", ["EvidenceChain", "VerificationChain"])
def test_local_stage_failure_cannot_replay_a_paid_j_call(
    monkeypatch, harness, sample_request, stage
):
    local = Mock()
    local.run.side_effect = TransientProviderFailure(retry_after=0)
    monkeypatch.setattr(f"app.jev.orchestrator.{stage}", Mock(return_value=local))
    executor, providers, factory, _ = harness(
        jev_max_attempts=3, jev_retry_attempts=3, jev_fallback_enabled=True
    )
    with pytest.raises(ExecutionFailure) as error:
        executor.execute(sample_request)
    assert isinstance(error.value.error, InvalidProviderResponse)
    assert error.value.stats.attempts == 1
    assert error.value.stats.orchestration.failed.value == (
        "E" if stage == "EvidenceChain" else "V"
    )
    providers["first"].analyze.assert_called_once()
    providers["second"].analyze.assert_not_called()
    factory.assert_not_called()


def test_bounded_retry_uses_backoff_and_same_request(harness, sample_request):
    result = FakeProvider().analyze(sample_request)
    executor, providers, _, clock = harness(
        errors={"first": [TransientProviderFailure(), result]},
        jev_max_attempts=3,
        jev_retry_attempts=3,
    )
    outcome = executor.execute(sample_request)
    assert outcome.stats.attempts == 2 and outcome.stats.retries == 1
    assert outcome.stats.fallbacks == 0
    assert clock.delays == [0.25]
    requests = [call.args[0] for call in providers["first"].analyze.call_args_list]
    assert all(request.request_id == sample_request.request_id for request in requests)


def test_retry_exhaustion_falls_back_once_with_global_cap(harness, sample_request):
    executor, providers, factory, clock = harness(
        errors={"first": TransientProviderFailure()},
        jev_max_attempts=3,
        jev_retry_attempts=2,
        jev_fallback_enabled=True,
    )
    outcome = executor.execute(sample_request)
    assert outcome.route.profile.model == "second"
    assert (
        outcome.stats.attempts == 3 and outcome.stats.retries == 1 and outcome.stats.fallbacks == 1
    )
    assert providers["first"].analyze.call_count == 2
    providers["second"].analyze.assert_called_once()
    factory.assert_called_once()
    assert clock.delays == [0.25, 0.5]


def test_total_attempt_cap_is_not_per_model(harness, sample_request):
    executor, providers, _, _ = harness(
        errors={"first": TransientProviderFailure()},
        jev_max_attempts=2,
        jev_retry_attempts=2,
        jev_fallback_enabled=True,
    )
    with pytest.raises(ExecutionFailure) as caught:
        executor.execute(sample_request)
    assert caught.value.stats.attempts == 2
    assert caught.value.route.profile.model == "first"
    providers["second"].analyze.assert_not_called()


@pytest.mark.parametrize(
    "error",
    [
        ProviderFailure("SECRET"),
        ProviderTimeout("SECRET"),
        InvalidProviderResponse("SECRET"),
        ValueError("SECRET"),
        RuntimeError("SECRET"),
    ],
)
def test_terminal_failures_do_not_retry_or_fallback(harness, sample_request, error):
    executor, providers, _, clock = harness(
        errors={"first": error}, jev_max_attempts=3, jev_retry_attempts=2, jev_fallback_enabled=True
    )
    with pytest.raises(ExecutionFailure) as caught:
        executor.execute(sample_request)
    assert caught.value.stats.attempts == 1
    assert "SECRET" not in str(caught.value.error)
    providers["second"].analyze.assert_not_called()
    assert clock.delays == []


def test_evidence_validation_failure_is_never_replayed(harness, sample_request):
    result = FakeProvider().analyze(sample_request)
    result.findings.items[0].evidence[0].page = 99
    executor, providers, _, _ = harness(
        errors={"first": [result]},
        jev_max_attempts=2,
        jev_retry_attempts=2,
        jev_fallback_enabled=True,
    )
    with pytest.raises(ExecutionFailure) as caught:
        executor.execute(sample_request)
    assert isinstance(caught.value.error, InvalidProviderResponse)
    providers["second"].analyze.assert_not_called()


def test_retry_after_is_respected_and_long_delays_defer(harness, sample_request):
    result = FakeProvider().analyze(sample_request)
    executor, _, _, clock = harness(
        errors={"first": [TransientProviderFailure(retry_after=1), result]},
        jev_max_attempts=2,
        jev_retry_attempts=2,
    )
    assert executor.execute(sample_request).stats.attempts == 2
    assert clock.delays == [1]
    executor, providers, _, clock = harness(
        errors={"first": TransientProviderFailure(retry_after=60)},
        jev_max_attempts=3,
        jev_retry_attempts=2,
        jev_fallback_enabled=True,
    )
    with pytest.raises(ExecutionFailure):
        executor.execute(sample_request)
    providers["first"].analyze.assert_called_once()
    providers["second"].analyze.assert_not_called()
    assert clock.delays == []


def test_budget_exhaustion_stops_before_another_call(harness, sample_request):
    executor, providers, _, clock = harness(jev_max_attempts=3, jev_retry_attempts=3)

    def fail_slowly(request):
        clock.now += 21
        raise TransientProviderFailure()

    providers["first"].analyze.side_effect = fail_slowly
    with pytest.raises(ExecutionFailure) as caught:
        executor.execute(sample_request)
    assert isinstance(caught.value.error, ProviderTimeout)
    assert caught.value.stats.attempts == 1
    assert clock.delays == []


def test_circuit_trip_fast_fail_and_single_probe(harness, sample_request):
    executor, providers, _, clock = harness(
        errors={"first": TransientProviderFailure()}, jev_circuit_failure_threshold=1
    )
    with pytest.raises(ExecutionFailure):
        executor.execute(sample_request)
    with pytest.raises(ExecutionFailure) as caught:
        executor.execute(sample_request)
    assert isinstance(caught.value.error, CircuitOpen)
    assert caught.value.stats.attempts == 0 and caught.value.stats.circuit_skips == 1
    providers["first"].analyze.assert_called_once()
    clock.now += 30
    providers["first"].analyze.side_effect = None
    assert executor.execute(sample_request).stats.attempts == 1
    assert executor.breaker.state(("gemini", "first")) == "CLOSED"


def test_open_primary_can_use_explicit_same_provider_fallback(harness, sample_request):
    executor, providers, _, _ = harness(
        errors={"first": TransientProviderFailure()},
        jev_max_attempts=2,
        jev_fallback_enabled=True,
        jev_circuit_failure_threshold=1,
    )
    assert executor.execute(sample_request).route.profile.model == "second"
    outcome = executor.execute(sample_request)
    assert outcome.stats.circuit_skips == 1 and outcome.stats.attempts == 1
    assert outcome.route.profile.model == "second"
    providers["first"].analyze.assert_called_once()


def test_circuit_generation_fences_stale_inflight_success():
    clock = Clock()
    breaker = CircuitBreaker(1, 30, clock=clock)
    key = ("gemini", "model")
    first, stale = breaker.acquire(key), breaker.acquire(key)
    breaker.complete(first, failed=True)
    breaker.complete(stale, failed=False)
    assert breaker.acquire(key) is None
    clock.now = 30
    probe = breaker.acquire(key)
    assert probe.probe and breaker.state(key) == "HALF_OPEN"
    assert breaker.acquire(key) is None
    breaker.complete(probe, failed=True)
    assert breaker.state(key) == "OPEN"
    clock.now = 60
    probe = breaker.acquire(key)
    breaker.abandon(probe)
    assert breaker.acquire(key).probe


def test_only_one_concurrent_half_open_probe():
    clock = Clock()
    breaker = CircuitBreaker(1, 30, clock=clock)
    key = ("gemini", "model")
    breaker.complete(breaker.acquire(key), failed=True)
    clock.now = 30
    barrier = Barrier(8)

    def enter(_):
        barrier.wait(timeout=5)
        return breaker.acquire(key)

    with ThreadPoolExecutor(max_workers=8) as pool:
        permits = list(pool.map(enter, range(8)))
    assert sum(permit is not None for permit in permits) == 1


def test_success_resets_failures_and_models_are_isolated():
    breaker = CircuitBreaker(2, 30)
    first, second = ("gemini", "first"), ("gemini", "second")
    breaker.complete(breaker.acquire(first), failed=True)
    breaker.complete(breaker.acquire(first), failed=False)
    breaker.complete(breaker.acquire(first), failed=True)
    assert breaker.state(first) == "CLOSED"
    breaker.complete(breaker.acquire(first), failed=True)
    assert breaker.state(first) == "OPEN"
    assert breaker.acquire(second) is not None


def test_disabled_circuit_never_blocks():
    breaker = CircuitBreaker(1, 30, enabled=False)
    key = ("gemini", "first")
    breaker.complete(breaker.acquire(key), failed=True)
    assert breaker.acquire(key) is not None
    assert breaker.state(key) == "CLOSED"


def test_late_result_is_not_returned_as_success(harness, sample_request):
    executor, providers, _, clock = harness()

    def slow(request):
        clock.now = 111
        return FakeProvider().analyze(request)

    providers["first"].analyze.side_effect = slow
    with pytest.raises(ExecutionFailure) as caught:
        executor.execute(sample_request)
    assert isinstance(caught.value.error, ProviderTimeout)


@pytest.mark.parametrize(
    "changes",
    [
        {"jev_max_attempts": 0},
        {"jev_max_attempts": 6},
        {"jev_retry_attempts": 2},
        {"jev_fallback_enabled": True},
        {"jev_attempt_timeout_seconds": 101},
        {"jev_execution_budget_seconds": 10},
        {"jev_retry_base_delay_seconds": 3},
        {"jev_circuit_failure_threshold": 0},
        {"jev_circuit_cooldown_seconds": 0},
    ],
)
def test_invalid_resilience_settings_are_rejected(changes):
    with pytest.raises(ValidationError):
        Settings(
            app_env="local",
            jev_provider="gemini",
            jev_service_token="synthetic-token",
            google_api_key="synthetic-key",
            gemini_model="fake",
            **changes,
        )
