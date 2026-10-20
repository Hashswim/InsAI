from app.providers.base import ProviderResult
from app.schemas.jev import Findings, Item, JevRequest


class StubProvider:
    """Offline connectivity only: no document interpretation or real judgment."""

    name = "stub"
    model = "stub-v1"

    def analyze(self, request: JevRequest) -> ProviderResult:
        return ProviderResult(
            Findings(
                items=[
                    Item(
                        item=f"{document.kind.value} 작성 상태",
                        judgment="UNVERIFIABLE",
                        evidence=[],
                        confidence=0,
                        verification_reason="스텁 모드: 실제 문서 내용을 분석하지 않았습니다.",
                        supplement="실제 판정을 받으려면 Gemini 모드를 설정한 뒤 다시 분석하세요.",
                    )
                    for document in request.documents
                ]
            )
        )
