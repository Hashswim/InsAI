from collections.abc import Callable
from dataclasses import dataclass
from threading import Lock
from time import monotonic


@dataclass(frozen=True)
class Permit:
    key: tuple[str, str]
    generation: int
    probe: bool


@dataclass
class _State:
    failures: int = 0
    open_until: float | None = None
    probe_inflight: bool = False
    generation: int = 0


class CircuitBreaker:
    """Process-local, per-model breaker; permits fence stale in-flight results."""

    def __init__(
        self,
        threshold: int,
        cooldown: float,
        *,
        enabled: bool = True,
        clock: Callable[[], float] = monotonic,
    ) -> None:
        self.threshold = threshold
        self.cooldown = cooldown
        self.enabled = enabled
        self.clock = clock
        self._states: dict[tuple[str, str], _State] = {}
        self._lock = Lock()

    def acquire(self, key: tuple[str, str]) -> Permit | None:
        if not self.enabled:
            return Permit(key, 0, False)
        with self._lock:
            state = self._states.setdefault(key, _State())
            if state.open_until is not None:
                if self.clock() < state.open_until or state.probe_inflight:
                    return None
                state.probe_inflight = True
                return Permit(key, state.generation, True)
            return Permit(key, state.generation, False)

    def complete(self, permit: Permit, *, failed: bool) -> None:
        if not self.enabled:
            return
        with self._lock:
            state = self._states[permit.key]
            if state.generation != permit.generation:
                return
            if not failed:
                state.failures = 0
                if permit.probe:
                    state.open_until = None
                    state.probe_inflight = False
                    state.generation += 1
                return
            state.failures += 1
            if permit.probe or state.failures >= self.threshold:
                state.open_until = self.clock() + self.cooldown
                state.probe_inflight = False
                state.generation += 1

    def state(self, key: tuple[str, str]) -> str:
        with self._lock:
            state = self._states.get(key)
            if not self.enabled or state is None or state.open_until is None:
                return "CLOSED"
            return "HALF_OPEN" if state.probe_inflight else "OPEN"

    def abandon(self, permit: Permit) -> None:
        if not self.enabled:
            return
        with self._lock:
            state = self._states[permit.key]
            if state.generation == permit.generation and permit.probe:
                state.probe_inflight = False
