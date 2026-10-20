from hashlib import sha256
from importlib.resources import files

from app.chains.release import ACTIVE_RELEASE


def load_jev_prompt(version: str = ACTIVE_RELEASE.prompt_version) -> str:
    # Resolve only a code-reviewed release, never a caller-controlled file path.
    if version != ACTIVE_RELEASE.prompt_version:
        raise ValueError("Unknown JEV prompt version")
    try:
        raw = files("app").joinpath(f"chains/prompts/{version}.txt").read_bytes()
        if sha256(raw).hexdigest() != ACTIVE_RELEASE.prompt_sha256:
            raise ValueError()
        prompt = raw.decode("utf-8")
        if not prompt.strip():
            raise ValueError()
    except Exception:
        raise ValueError("JEV prompt release is unavailable or invalid") from None
    return prompt
