from app.core.config import Settings
from app.providers.base import Provider
from app.providers.gemini_provider import GeminiProvider
from app.providers.stub_provider import StubProvider


def build_provider(config: Settings) -> Provider:
    if config.jev_provider == "stub":
        return StubProvider()
    return GeminiProvider(config)
