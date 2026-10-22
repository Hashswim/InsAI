"""MCP server `insai-documents`: the quick check as standard tools.

Tools take PDFs as base64 and return only the extracted, pseudonymized text form or gaps;
run it locally over stdio (`python -m app.mcp_servers.documents`).
"""

import json
from uuid import uuid4

from mcp.server.mcpserver import MCPServer
from pydantic import BaseModel

from app.core.resources import load_prompt_section
from app.jev.precheck import KIND_NAMES, KIND_REQUIRED, REQUIRED, Precheck, precheck
from app.schemas.jev import DocumentKind, JevRequest

server = MCPServer(
    name="insai-documents",
    instructions="의료 청구 서류 PDF에서 키워드를 추출하고 누락을 검사합니다. 판정은 하지 않습니다.",
)


class DocumentInput(BaseModel):
    kind: DocumentKind
    content_base64: str


def _bundle(documents: list[DocumentInput]) -> Precheck:
    request = JevRequest.model_validate(
        {
            "request_id": str(uuid4()),
            "documents": [
                {"document_id": str(uuid4()), "kind": d.kind, "content_base64": d.content_base64}
                for d in documents
            ],
        }
    )
    return precheck(request)


@server.tool(
    description="PDF 3종에서 진단명·질병코드·날짜·병원명·금액을 추출한 텍스트 폼(가명 처리)"
)
def extract_fields(documents: list[DocumentInput]) -> dict:
    check = _bundle(documents)
    return json.loads(check.form().removeprefix("JEV_TEXT_FORM="))


@server.tool(
    description="필수 항목 누락·판독 불가·서류 종류 오류. complete가 true면 손해사정사 검토로 넘길 수 있음"
)
def check_missing(documents: list[DocumentInput]) -> dict:
    check = _bundle(documents)
    if check.complete:
        return {"complete": True, "gaps": [], "server_flags": list(check.flags)}
    findings = check.claimant_findings()
    gaps = [
        {"item": i.item, "supplement": i.supplement}
        for i in findings.items
        if i.judgment != "SUITABLE"
    ]
    return {"complete": False, "gaps": gaps, "summary": findings.summary}


@server.resource("insai://required-fields", mime_type="application/json")
def required_fields() -> str:
    """서류 종류별 필수 항목(공통 + 종류별)."""
    return json.dumps(
        {
            kind.value: {
                "name": KIND_NAMES[kind],
                "required": [label for label, _ in {**REQUIRED, **KIND_REQUIRED[kind]}.values()],
            }
            for kind in DocumentKind
        },
        ensure_ascii=False,
    )


@server.prompt(description="J·E·V 단일 검토 프롬프트 (버전·SHA-256 고정본)")
def jev_review() -> str:
    return load_prompt_section("SINGLE")


if __name__ == "__main__":
    server.run("stdio")
