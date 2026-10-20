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
    prompt_version="jev-v1",
    prompt_sha256="994f8ed30f350b9655e10f6ff4a18d247d72563dffd7e42a509f3730e79454e5",
    chain_version="jev-chain-v2",
)
