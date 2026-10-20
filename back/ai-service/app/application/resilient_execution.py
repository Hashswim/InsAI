from collections.abc import Callable
from dataclasses import dataclass, replace
from random import uniform
from threading import Lock
from time import monotonic, sleep

from httpx import TimeoutException

from app.chains.jev_workflow import run_workflow
from app.core.config import Settings
from app.core.errors import (
    CircuitOpen,
    InvalidProviderResponse,
    ProviderFailure,
    ProviderTimeout,
    TransientProviderFailure,
    UsageLimitExceeded,
    UsagePolicyUnavailable,
)
from app.core.usage import CostAccount, UsageGuard, validate_cost_profiles
from app.providers.base import Provider, ProviderResult
from app.providers.circuit_breaker import CircuitBreaker
from app.providers.routing_policy import RouteDecision
from app.schemas.jev import JevRequest


@dataclass(frozen=True)
class ExecutionStats:
    attempts: int = 0
    retries: int = 0
    fallbacks: int = 0
    circuit_skips: int = 0
    cost_estimate_usd: str | None = None
    cost_accounting: str = "DISABLED"

    def metadata(self) -> dict[str, int | str]:
        result: dict[str, int | str] = {
            "execution_attempts": self.attempts,
            "execution_retries": self.retries,
            "execution_fallbacks": self.fallbacks,
            "execution_circuit_skips": self.circuit_skips,
        }
        if self.cost_estimate_usd is not None:
            result.update(
                cost_estimate_usd=self.cost_estimate_usd, cost_accounting=self.cost_accounting
            )
        return result


@dataclass(frozen=True)
class ExecutionOutcome:
    result: ProviderResult
    route: RouteDecision
    stats: ExecutionStats


class ExecutionFailure(Exception):
    def __init__(self, error: ProviderFailure, route: RouteDecision, stats: ExecutionStats):
        super().__init__("Model execution failed")
        self.error = error
        self.route = route
        self.stats = stats


def safe_failure(error: Exception) -> ProviderFailure:
    if isinstance(error, UsageLimitExceeded):
        return UsageLimitExceeded(retry_after=error.retry_after)
    if isinstance(error, UsagePolicyUnavailable):
        return UsagePolicyUnavailable()
    if isinstance(error, (ProviderTimeout, TimeoutError, TimeoutException)):
        return ProviderTimeout()
    if isinstance(error, CircuitOpen):
        return CircuitOpen()
    if isinstance(error, (InvalidProviderResponse, ValueError)):
        return InvalidProviderResponse()
    if isinstance(error, TransientProviderFailure):
        return TransientProviderFailure(retry_after=error.retry_after)
    return ProviderFailure()


class ResilientExecutor:
    def __init__(
        self,
        config: Settings,
        routes: tuple[RouteDecision, ...],
        primary: Provider,
        factory: Callable[[RouteDecision], Provider],
        *,
        clock: Callable[[], float] = monotonic,
        sleeper: Callable[[float], None] = sleep,
        jitter: Callable[[float, float], float] = uniform,
        usage: UsageGuard | None = None,
    ) -> None:
        if not routes or any(r.profile.provider != config.jev_provider for r in routes):
            raise ValueError("Execution routes must remain within the configured provider")
        self.config = config
        self.routes = routes if config.jev_fallback_enabled else routes[:1]
        validate_cost_profiles(config, self.routes)
        self.usage = usage if usage is not None else UsageGuard(config, clock=clock)
        self.factory = factory
        self.clock, self.sleeper, self.jitter = clock, sleeper, jitter
        self.breaker = CircuitBreaker(
            config.jev_circuit_failure_threshold,
            config.jev_circuit_cooldown_seconds,
            enabled=config.jev_circuit_enabled and config.jev_provider != "stub",
            clock=clock,
        )
        self._providers = {routes[0].profile.model: primary}
        self._lock = Lock()

    def _provider(self, route: RouteDecision) -> Provider:
        with self._lock:
            if route.profile.model not in self._providers:
                self._providers[route.profile.model] = self.factory(route)
            provider = self._providers[route.profile.model]
        if (provider.name, provider.model) != (route.profile.provider, route.profile.model):
            raise ValueError("Provider does not match execution route")
        return provider

    def execute(self, request: JevRequest) -> ExecutionOutcome:
        deadline = self.clock() + self.config.jev_execution_budget_seconds
        attempts = retries = fallbacks = skips = 0
        last: ProviderFailure = CircuitOpen()
        route = self.routes[0]
        observed_route = route
        pending_delay = 0.0
        account = CostAccount()

        def stats() -> ExecutionStats:
            return ExecutionStats(
                attempts,
                retries,
                fallbacks,
                skips,
                str(account.total) if self.config.jev_cost_guard_enabled else None,
                (
                    "DISABLED"
                    if not self.config.jev_cost_guard_enabled
                    else "RESERVED_BOUND"
                    if account.uncertain
                    else "USAGE_ESTIMATE"
                ),
            )

        def fail(error: ProviderFailure) -> ExecutionFailure:
            return ExecutionFailure(error, observed_route, stats())

        for route_index, route in enumerate(self.routes):
            if route_index > 0:
                route = replace(route, fallback=True)
            for model_attempt in range(self.config.jev_retry_attempts):
                if attempts >= self.config.jev_max_attempts:
                    raise fail(last)
                # A long provider delay defers the request; never retry sooner.
                if pending_delay > 2:
                    raise fail(last)
                if (
                    self.clock() + pending_delay + self.config.jev_attempt_timeout_seconds
                    > deadline
                ):
                    raise fail(ProviderTimeout())
                if pending_delay:
                    self.sleeper(pending_delay)
                    pending_delay = 0
                permit = self.breaker.acquire((route.profile.provider, route.profile.model))
                if permit is None:
                    skips += 1
                    last = CircuitOpen()
                    break
                observed_route = route
                reservation = None
                try:
                    reservation = self.usage.reserve(route.profile, account)
                    provider = self._provider(route)
                except (UsageLimitExceeded, UsagePolicyUnavailable) as guard_error:
                    if reservation is not None:
                        self.usage.finish(reservation, invoked=False)
                    self.breaker.abandon(permit)
                    raise fail(safe_failure(guard_error)) from None
                except Exception:
                    if reservation is not None:
                        self.usage.finish(reservation, invoked=False)
                    self.breaker.abandon(permit)
                    raise fail(ProviderFailure()) from None
                except BaseException:
                    if reservation is not None:
                        self.usage.finish(reservation, invoked=False)
                    self.breaker.abandon(permit)
                    raise
                if self.clock() + self.config.jev_attempt_timeout_seconds > deadline:
                    self.usage.finish(reservation, invoked=False)
                    self.breaker.abandon(permit)
                    raise fail(ProviderTimeout())
                attempts += 1
                retries += int(model_attempt > 0)
                fallbacks += int(route_index > 0 and model_attempt == 0)
                try:
                    result = run_workflow(request, provider)
                except Exception as raw_error:
                    self.usage.finish(reservation)
                    error = safe_failure(raw_error)
                    self.breaker.complete(
                        permit,
                        failed=isinstance(error, (TransientProviderFailure, ProviderTimeout)),
                    )
                    if not isinstance(error, TransientProviderFailure):
                        raise fail(error) from None
                    last = error
                    delay = min(2.0, self.config.jev_retry_base_delay_seconds * 2 ** (attempts - 1))
                    pending_delay = max(self.jitter(delay / 2, delay), error.retry_after or 0)
                except BaseException:
                    self.usage.finish(reservation)
                    self.breaker.abandon(permit)
                    raise
                else:
                    cost_breach = self.usage.finish(reservation, result)
                    self.breaker.complete(permit, failed=False)
                    if cost_breach:
                        raise fail(UsagePolicyUnavailable())
                    if self.clock() > deadline:
                        raise fail(ProviderTimeout())
                    return ExecutionOutcome(result, route, stats())
        raise fail(last)
