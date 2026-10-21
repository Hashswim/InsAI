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
    prompt_version="jev-v5",
    prompt_sha256="c6b1357d3dbcb7e071f942090af022422a87e49334e232998084f90a425d4a0f",
    chain_version="jev-chain-v10",
)
