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
    prompt_version="jev-v4",
    prompt_sha256="d3a3c1b20ac9764ecda7cd4d37de8a93dabdd12e7f165057715d47fb81762d31",
    chain_version="jev-chain-v9",
)
