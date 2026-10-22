"""Process-local admission and conservative USD reservation; no business IDs."""

from __future__ import annotations

import math
from collections import deque
from collections.abc import Callable
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from decimal import ROUND_UP, Decimal, DecimalException, localcontext
from functools import lru_cache
from threading import Lock
from time import monotonic
from typing import TYPE_CHECKING

from app.core.errors import UsageLimitExceeded, UsagePolicyUnavailable
from app.providers.base import ProviderResult
from app.providers.catalog import JEV_OUTPUT_TOKENS, ModelPricing, ModelProfile
from app.providers.routing_policy import RouteDecision

if TYPE_CHECKING:
    from app.core.config import Settings


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def validate_cost_profiles(
    config: Settings, routes: tuple[RouteDecision, ...], today: date | None = None
) -> None:
    if not config.jev_cost_guard_enabled:
        return
    today = today or utcnow().date()
    for route in routes if config.jev_fallback_enabled else routes[:1]:
        profile = route.profile
        if profile.pricing is None or profile.context_window_tokens is None:
            raise UsagePolicyUnavailable()
        age = (today - profile.pricing.reviewed_on).days
        if (
            age < 0
            or age > config.jev_pricing_max_age_days
            or not JEV_OUTPUT_TOKENS <= profile.context_window_tokens <= 10000000
        ):
            raise UsagePolicyUnavailable()


def estimate_usd(pricing: ModelPricing, input_tokens: int, output_tokens: int) -> Decimal:
    with localcontext() as context:
        context.prec = 50
        amount = (
            Decimal(input_tokens) * pricing.input_usd_per_million
            + Decimal(output_tokens) * pricing.output_usd_per_million
        ) / Decimal(1000000)
        return amount.quantize(Decimal("0.000000001"), rounding=ROUND_UP)


@dataclass
class CostAccount:
    total: Decimal = Decimal(0)
    uncertain: bool = False


@dataclass
class _Day:
    total: Decimal = Decimal(0)
    pending: int = 0


@dataclass
class Admission:
    released: bool = False


@dataclass
class Reservation:
    day: date
    account: CostAccount
    amount: Decimal
    profile: ModelProfile
    charged: bool
    settled: bool = False


class UsageGuard:
    def __init__(
        self,
        config: Settings,
        *,
        clock: Callable[[], float] = monotonic,
        wallclock: Callable[[], datetime] = utcnow,
    ) -> None:
        self.config = config
        self.clock, self.wallclock = clock, wallclock
        self._lock = Lock()
        self._requests: deque[float] = deque()
        self._attempts: deque[float] = deque()
        self._active_requests = self._active_calls = 0
        self._days: dict[date, _Day] = {}
        self._cost_fault = False

    @staticmethod
    def _expire(queue: deque[float], now: float) -> None:
        while queue and queue[0] <= now - 60:
            queue.popleft()

    def admit(self) -> Admission:
        with self._lock:
            now = self.clock()
            self._expire(self._requests, now)
            if self.config.jev_limits_enabled:
                if self._active_requests >= self.config.jev_max_concurrent_requests:
                    raise UsageLimitExceeded(retry_after=1)
                if len(self._requests) >= self.config.jev_requests_per_minute:
                    raise UsageLimitExceeded(
                        retry_after=max(1, math.ceil(self._requests[0] + 60 - now))
                    )
                self._requests.append(now)
            self._active_requests += 1
            return Admission()

    def release(self, admission: Admission) -> None:
        with self._lock:
            if not admission.released:
                admission.released = True
                self._active_requests -= 1

    def reserve(self, profile: ModelProfile, account: CostAccount) -> Reservation:
        today = self.wallclock().astimezone(timezone.utc).date()
        charged = self.config.jev_cost_guard_enabled
        amount = Decimal(0)
        if charged:
            validate_cost_profiles(self.config, (RouteDecision(profile, "pinned"),), today)
            assert profile.pricing is not None and profile.context_window_tokens is not None
            amount = estimate_usd(profile.pricing, profile.context_window_tokens, JEV_OUTPUT_TOKENS)
        with self._lock:
            now = self.clock()
            self._expire(self._attempts, now)
            if self.config.jev_limits_enabled:
                if self._active_calls >= self.config.jev_max_concurrent_calls:
                    raise UsageLimitExceeded(retry_after=1)
                if len(self._attempts) >= self.config.jev_attempts_per_minute:
                    raise UsageLimitExceeded(
                        retry_after=max(1, math.ceil(self._attempts[0] + 60 - now))
                    )
            if charged:
                if self._cost_fault:
                    raise UsagePolicyUnavailable()
                for old in tuple(self._days):
                    if old < today - timedelta(days=2) and not self._days[old].pending:
                        del self._days[old]
                ledger = self._days.setdefault(today, _Day())
                assert (
                    self.config.jev_request_budget_usd is not None
                    and self.config.jev_daily_budget_usd is not None
                )
                if account.total + amount > self.config.jev_request_budget_usd:
                    raise UsageLimitExceeded()
                if ledger.total + amount > self.config.jev_daily_budget_usd:
                    raise UsageLimitExceeded()
                account.total += amount
                ledger.total += amount
                ledger.pending += 1
            if self.config.jev_limits_enabled:
                self._attempts.append(now)
            self._active_calls += 1
            return Reservation(today, account, amount, profile, charged)

    def finish(
        self,
        reservation: Reservation,
        result: ProviderResult | None = None,
        *,
        invoked: bool = True,
    ) -> bool:
        """Unknown/failed usage keeps the full charge. A bound breach blocks future calls."""
        with self._lock:
            if reservation.settled:
                return False
            reservation.settled = True
            self._active_calls -= 1
            if not reservation.charged:
                return False
            ledger = self._days[reservation.day]
            ledger.pending -= 1
            profile = reservation.profile
            assert profile.pricing is not None and profile.context_window_tokens is not None
            if not invoked:
                ledger.total -= reservation.amount
                reservation.account.total -= reservation.amount
                return False
            if result is None:
                reservation.account.uncertain = True
                return False
            breached = (
                result.input_tokens > profile.context_window_tokens
                or result.output_tokens > JEV_OUTPUT_TOKENS
            )
            if breached:
                self._cost_fault = True
                reservation.account.uncertain = True
            if not breached and (
                not result.usage_complete or result.input_tokens == 0 or result.output_tokens == 0
            ):
                reservation.account.uncertain = True
                return False
            try:
                actual = estimate_usd(profile.pricing, result.input_tokens, result.output_tokens)
            except DecimalException:
                self._cost_fault = True
                reservation.account.uncertain = True
                return True
            if breached:
                actual = max(actual, reservation.amount)
            delta = actual - reservation.amount
            ledger.total += delta
            reservation.account.total += delta
            return breached

    def daily_estimate(self, day: date | None = None) -> Decimal:
        day = day or self.wallclock().astimezone(timezone.utc).date()
        with self._lock:
            return self._days[day].total if day in self._days else Decimal(0)

    def policy_ready(self) -> bool:
        with self._lock:
            return not self._cost_fault


@lru_cache
def usage_guard() -> UsageGuard:
    from app.core.config import settings

    return UsageGuard(settings())
