from app.core.config import Settings
from app.providers.gemini_provider import GeminiProvider


def build_provider(config: Settings):
    if config.jev_provider != "gemini":
        raise ValueError("Only Gemini is configured")
    return GeminiProvider(config)
