from app.core.config import Settings
from app.providers.base import Provider
from app.providers.gemini_provider import GeminiProvider
from app.providers.openai_provider import OpenAIProvider
from app.providers.stub_provider import StubProvider


def build_provider(config: Settings) -> Provider:
    if config.jev_provider == "stub":
        return StubProvider()
    if config.jev_provider == "openai":
        return OpenAIProvider(config)
    return GeminiProvider(config)
