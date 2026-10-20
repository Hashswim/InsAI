from app.core.config import Settings
from app.providers.base import Provider
from app.providers.catalog import load_catalog, selected_profile
from app.providers.gemini_provider import GeminiProvider
from app.providers.openai_provider import OpenAIProvider
from app.providers.stub_provider import StubProvider


def build_provider(config: Settings) -> Provider:
    catalog = load_catalog(config.jev_model_catalog_path)
    model = config.openai_model if config.jev_provider == "openai" else config.gemini_model
    selected_profile(catalog, config.jev_provider, model)
    if config.jev_provider == "stub":
        return StubProvider()
    if config.jev_provider == "openai":
        return OpenAIProvider(config)
    return GeminiProvider(config)
