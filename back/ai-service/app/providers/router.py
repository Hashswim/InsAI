from app.core.config import Settings
from app.providers.base import Provider
from app.providers.gemini_provider import GeminiProvider


def build_provider(config: Settings) -> Provider:
    if config.jev_provider != "gemini":
        raise ValueError("Only Gemini is configured")
    return GeminiProvider(config)
