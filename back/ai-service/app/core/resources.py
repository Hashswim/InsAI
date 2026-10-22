from hashlib import sha256
from importlib.resources import files

from app.jev.release import ACTIVE_RELEASE


def load_jev_prompt(version: str = ACTIVE_RELEASE.prompt_version) -> str:
    # Resolve only a code-reviewed release, never a caller-controlled file path.
    if version != ACTIVE_RELEASE.prompt_version:
        raise ValueError("Unknown JEV prompt version")
    try:
        raw = files("app").joinpath(f"jev/prompts/{version}.txt").read_bytes()
        if sha256(raw).hexdigest() != ACTIVE_RELEASE.prompt_sha256:
            raise ValueError()
        prompt = raw.decode("utf-8")
        if not prompt.strip():
            raise ValueError()
    except Exception:
        raise ValueError("JEV prompt release is unavailable or invalid") from None
    return prompt


PROMPT_SECTIONS = ("SINGLE", "REVIEWER", "RISK", "POLICY", "SUMMARY")


def load_prompt_section(name: str) -> str:
    """One role's prompt from the pinned bundle: SINGLE (one-call providers) or an agent's."""
    if name not in PROMPT_SECTIONS:
        raise ValueError("Unknown JEV prompt section")
    sections: dict[str, list[str]] = {}
    current = None
    for line in load_jev_prompt().splitlines():
        if line.startswith("### ") and line[4:] in PROMPT_SECTIONS:
            current = line[4:]
            sections[current] = []
        elif current is not None:
            sections[current].append(line)
    text = "\n".join(sections.get(name, [])).strip()
    if not text:
        raise ValueError("JEV prompt release is unavailable or invalid")
    return text
