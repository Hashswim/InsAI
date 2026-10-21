"""Code-reviewed release identity; never selected by request data or environment."""

from dataclasses import dataclass


@dataclass(frozen=True)
class JevRelease:
    prompt_version: str
    prompt_sha256: str
    chain_version: str

    def metadata(self) -> dict[str, str]:
        return {
            "prompt_version": self.prompt_version,
            "prompt_sha256": self.prompt_sha256,
            "chain_version": self.chain_version,
        }


ACTIVE_RELEASE = JevRelease(
    prompt_version="jev-v2",
    prompt_sha256="8ae41a49b5bf062a781f102f0202cdc2ed6c89e26bf277fc1d218ee58d7fe08b",
    chain_version="jev-chain-v5",
)
