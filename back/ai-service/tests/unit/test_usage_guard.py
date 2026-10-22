from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from threading import Barrier

import pytest
from conftest import FakeProvider
from pydantic import ValidationError

from app.core.config import Settings
from app.core.errors import UsageLimitExceeded, UsagePolicyUnavailable
from app.core.usage import CostAccount, UsageGuard, estimate_usd, validate_cost_profiles
from app.providers.base import ProviderResult
from app.providers.catalog import ModelProfile
from app.providers.routing_policy import RouteDecision


class Clock:
    def __init__(self):
        self.now = 0.0
        self.date = datetime(2026, 10, 20, 23, 59, tzinfo=timezone.utc)

    def __call__(self):
        return self.now

    def utcnow(self):
        return self.date


def config(**changes):
    return Settings(
        **{
            "app_env": "local",
            "jev_provider": "gemini",
            "jev_service_token": "synthetic",
            "google_api_key": "synthetic-key",
            "gemini_model": "test-model",
            **changes,
        }
    )


@pytest.fixture
def priced_profile(catalog_entry):
    def make(**changes):
        return ModelProfile.model_validate(
            catalog_entry(
                context_window_tokens=20000,
                pricing={
                    "input_usd_per_million": "1",
                    "output_usd_per_million": "2",
                    "reviewed_on": "2026-10-20",
                    "source": "synthetic test prices; not real rates",
                },
                **changes,
            )
        )

    return make


def funded(**changes):
    return config(
        **{
            "jev_cost_guard_enabled": True,
            "jev_request_budget_usd": "0.1",
            "jev_daily_budget_usd": "1",
            **changes,
        }
    )


def test_request_window_and_idempotent_release():
    clock = Clock()
    guard = UsageGuard(
        config(jev_requests_per_minute=2, jev_max_concurrent_requests=1), clock=clock
    )
    lease = guard.admit()
    with pytest.raises(UsageLimitExceeded):
        guard.admit()
    guard.release(lease)
    guard.release(lease)
    guard.release(guard.admit())
    with pytest.raises(UsageLimitExceeded) as caught:
        guard.admit()
    assert caught.value.retry_after == 60
    clock.now = 60
    guard.release(guard.admit())


def test_attempt_limit_and_concurrency_are_independent(catalog_entry):
    clock = Clock()
    guard = UsageGuard(config(jev_attempts_per_minute=2, jev_max_concurrent_calls=1), clock=clock)
    profile = ModelProfile.model_validate(catalog_entry())
    first = guard.reserve(profile, CostAccount())
    with pytest.raises(UsageLimitExceeded):
        guard.reserve(profile, CostAccount())
    guard.finish(first)
    guard.finish(first)
    guard.finish(guard.reserve(profile, CostAccount()))
    with pytest.raises(UsageLimitExceeded):
        guard.reserve(profile, CostAccount())
    clock.now = 60
    guard.finish(guard.reserve(profile, CostAccount()))


def test_unknown_prices_are_not_implicitly_free(catalog_entry):
    route = RouteDecision(ModelProfile.model_validate(catalog_entry()), "pinned")
    with pytest.raises(UsagePolicyUnavailable):
        validate_cost_profiles(funded(), (route,))


def test_reservation_reconciliation_and_unknown_usage(priced_profile, sample_request):
    clock = Clock()
    guard = UsageGuard(funded(), clock=clock, wallclock=clock.utcnow)
    profile = priced_profile()
    account = CostAccount()
    reservation = guard.reserve(profile, account)
    assert account.total == guard.daily_estimate() == Decimal("0.052")
    findings = FakeProvider().analyze(sample_request).findings
    assert not guard.finish(reservation, ProviderResult(findings, 1000, 500, True))
    assert account.total == guard.daily_estimate() == Decimal("0.002")
    unknown = guard.reserve(profile, account)
    guard.finish(unknown, ProviderResult(findings, 1000, 500))
    assert account.total == Decimal("0.054")
    assert account.uncertain


def test_failure_keeps_full_reservation_and_initialization_cancel_refunds(priced_profile):
    clock = Clock()
    guard = UsageGuard(funded(), wallclock=clock.utcnow)
    profile = priced_profile()
    account = CostAccount()
    guard.finish(guard.reserve(profile, account), invoked=False)
    assert account.total == 0
    assert not account.uncertain
    guard.finish(guard.reserve(profile, account))
    assert account.total == Decimal("0.052") and account.uncertain


def test_request_budget_is_shared_across_attempts(priced_profile):
    clock = Clock()
    guard = UsageGuard(funded(), wallclock=clock.utcnow)
    account = CostAccount()
    guard.finish(guard.reserve(priced_profile(), account))
    with pytest.raises(UsageLimitExceeded):
        guard.reserve(priced_profile(), account)


def test_daily_reservation_is_atomic_under_concurrency(priced_profile):
    clock = Clock()
    policy = funded(
        jev_request_budget_usd="1", jev_daily_budget_usd="0.052", jev_max_concurrent_calls=8
    )
    guard = UsageGuard(policy, wallclock=clock.utcnow)
    profile = priced_profile()
    barrier = Barrier(8)

    def reserve(_):
        barrier.wait(timeout=5)
        try:
            return guard.reserve(profile, CostAccount())
        except UsageLimitExceeded:
            return None

    with ThreadPoolExecutor(max_workers=8) as pool:
        reservations = list(pool.map(reserve, range(8)))
    assert sum(value is not None for value in reservations) == 1
    assert guard.daily_estimate() == Decimal("0.052")
    for value in reservations:
        if value:
            guard.finish(value)


def test_settlement_after_midnight_does_not_refund_new_day(priced_profile, sample_request):
    clock = Clock()
    guard = UsageGuard(funded(), wallclock=clock.utcnow)
    yesterday = clock.date.date()
    old = guard.reserve(priced_profile(), CostAccount())
    clock.date += timedelta(minutes=2)
    current = guard.reserve(priced_profile(), CostAccount())
    findings = FakeProvider().analyze(sample_request).findings
    guard.finish(old, ProviderResult(findings, 1000, 500, True))
    assert guard.daily_estimate(yesterday) == Decimal("0.002")
    assert guard.daily_estimate() == Decimal("0.052")
    guard.finish(current)


def test_reported_usage_above_bound_blocks_further_calls(priced_profile, sample_request):
    clock = Clock()
    guard = UsageGuard(funded(), wallclock=clock.utcnow)
    profile = priced_profile()
    findings = FakeProvider().analyze(sample_request).findings
    assert guard.finish(
        guard.reserve(profile, CostAccount()), ProviderResult(findings, 21000, 16000, True)
    )
    assert guard.daily_estimate() == Decimal("0.053")
    with pytest.raises(UsagePolicyUnavailable):
        guard.reserve(profile, CostAccount())


@pytest.mark.parametrize(
    "changes",
    [{"pricing": None}, {"context_window_tokens": None}, {"context_window_tokens": 10000001}],
)
def test_missing_or_unsupported_cost_bounds(catalog_entry, changes):
    entry = catalog_entry(
        context_window_tokens=20000,
        pricing={
            "input_usd_per_million": "1",
            "output_usd_per_million": "2",
            "reviewed_on": "2026-10-20",
            "source": "test",
        },
    )
    entry.update(changes)
    route = RouteDecision(ModelProfile.model_validate(entry), "pinned")
    with pytest.raises(UsagePolicyUnavailable):
        validate_cost_profiles(funded(), (route,), datetime(2026, 10, 20).date())


def test_stale_and_future_prices_are_rejected(priced_profile):
    route = RouteDecision(priced_profile(), "pinned")
    for today in (datetime(2026, 10, 19).date(), datetime(2026, 11, 20).date()):
        with pytest.raises(UsagePolicyUnavailable):
            validate_cost_profiles(funded(), (route,), today)


def test_decimal_cost_rounds_up_not_down(priced_profile):
    profile = priced_profile()
    assert estimate_usd(profile.pricing, 1, 1) == Decimal("0.000003")
    tiny = profile.pricing.model_copy(
        update={
            "input_usd_per_million": Decimal("0.00000001"),
            "output_usd_per_million": Decimal(0),
        }
    )
    assert estimate_usd(tiny, 1, 0) == Decimal("0.000000001")


def test_zero_usage_is_not_treated_as_free(priced_profile, sample_request):
    clock = Clock()
    guard = UsageGuard(funded(), wallclock=clock.utcnow)
    account = CostAccount()
    result = ProviderResult(FakeProvider().analyze(sample_request).findings, 0, 0, True)
    guard.finish(guard.reserve(priced_profile(), account), result)
    assert account.total == Decimal("0.052")
    assert account.uncertain


@pytest.mark.parametrize(
    "changes",
    [
        {"input_usd_per_million": "-1"},
        {"output_usd_per_million": "NaN"},
        {"currency": "KRW"},
        {"source": "   "},
        {"extra": "unsupported"},
    ],
)
def test_invalid_price_declarations_are_rejected(catalog_entry, changes):
    price = {
        "input_usd_per_million": "1",
        "output_usd_per_million": "2",
        "reviewed_on": "2026-10-20",
        "source": "synthetic test",
        **changes,
    }
    with pytest.raises(ValidationError):
        ModelProfile.model_validate(catalog_entry(pricing=price))


@pytest.mark.parametrize(
    "changes",
    [
        {"jev_requests_per_minute": 0},
        {"jev_max_concurrent_calls": 0},
        {"jev_cost_guard_enabled": True},
        {"jev_request_budget_usd": "1"},
        {"jev_request_budget_usd": "NaN"},
        {"jev_pricing_max_age_days": 0},
    ],
)
def test_invalid_limit_configuration_is_rejected(changes):
    with pytest.raises(ValidationError):
        config(**changes)
