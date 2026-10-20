from importlib.resources import files


def load_jev_prompt() -> str:
    prompt = files("app").joinpath("chains/prompts/jev.txt").read_text(encoding="utf-8")
    if not prompt.strip():
        raise ValueError("JEV prompt is unavailable")
    return prompt
