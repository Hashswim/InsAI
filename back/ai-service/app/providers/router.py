from app.core.config import Settings
from app.providers.base import Provider
from app.providers.gemini_provider import GeminiProvider
from app.providers.openai_provider import OpenAIProvider
from app.providers.routing_policy import RouteDecision, select_route
from app.providers.stub_provider import StubProvider


def build_provider(config: Settings) -> Provider:
    return build_routed_provider(config)[0]


def build_routed_provider(config: Settings) -> tuple[Provider, RouteDecision]:
    route = select_route(config)
    field = "openai_model" if config.jev_provider == "openai" else "gemini_model"
    selected = config
    if config.jev_provider != "stub" and getattr(config, field) != route.profile.model:
        # Only the exact model ID changes; credentials and environment are retained.
        selected = config.model_copy(update={field: route.profile.model})
    if config.jev_provider == "stub":
        return StubProvider(), route
    if config.jev_provider == "openai":
        return OpenAIProvider(selected), route
    return GeminiProvider(selected), route
