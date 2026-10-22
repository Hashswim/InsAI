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
    prompt_version="jev-v7",
    prompt_sha256="8f8e9b75a1247b62834adecdea39c9ae22e11a9a35d919d00df881563787d129",
    chain_version="jev-chain-v12",
)
