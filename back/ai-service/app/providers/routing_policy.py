from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING, Literal

from app.providers.catalog import CatalogError, ModelProfile, load_catalog

if TYPE_CHECKING:
    from app.core.config import Settings

RoutingPolicy = Literal["pinned", "priority"]
ROUTER_VERSION = "jev-router-v1"


class RouteUnavailable(CatalogError):
    pass


@dataclass(frozen=True)
class RouteDecision:
    profile: ModelProfile
    policy: RoutingPolicy
    priority_index: int | None = None

    @property
    def reason(self) -> str:
        if self.profile.provider == "stub":
            return "OFFLINE_STUB"
        return "PINNED_MODEL" if self.policy == "pinned" else "FIRST_COMPATIBLE_PRIORITY"

    def metadata(self) -> dict[str, str | int]:
        # No source text, catalog path, rejected IDs or user/document data.
        result: dict[str, str | int] = {
            "routing_policy": self.policy,
            "routing_reason": self.reason,
            "router_version": ROUTER_VERSION,
        }
        if self.priority_index is not None:
            result["routing_priority_index"] = self.priority_index
        return result


def select_route(config: Settings) -> RouteDecision:
    catalog = load_catalog(config.jev_model_catalog_path)
    if config.jev_routing_policy == "pinned":
        model = (
            "stub-v1"
            if config.jev_provider == "stub"
            else config.openai_model
            if config.jev_provider == "openai"
            else config.gemini_model
        )
        return RouteDecision(catalog.require(config.jev_provider, model), "pinned")
    # Explicit order, within one approved provider. This is preflight selection,
    # not failure-triggered fallback. Never consider unlisted catalog entries.
    for index, model in enumerate(config.jev_model_priority):
        try:
            profile = catalog.require(config.jev_provider, model)
        except CatalogError:
            continue
        return RouteDecision(profile, "priority", index)
    raise RouteUnavailable("No compatible model satisfies the routing policy")
