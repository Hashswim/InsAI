import asyncio
import base64
import json
import sys
from pathlib import Path

from conftest import FORM_PAGES, text_pdf
from mcp import StdioServerParameters
from mcp.client.session import ClientSession
from mcp.client.stdio import stdio_client

from app.mcp_servers import policy
from app.rag.chunking import load_clauses
from app.rag.store import InMemoryRetriever
from tests.unit.test_agent_team import BigramEmbedding

KINDS = ("ITEMIZED_BILL", "INITIAL_VISIT_RECORD", "MEDICAL_RECEIPT")


def bundle(**pages):
    lines = {k: pages.get(k, FORM_PAGES[k]) for k in KINDS}
    return {
        "documents": [
            {"kind": k, "content_base64": base64.b64encode(text_pdf(lines[k])).decode()}
            for k in KINDS
        ]
    }


def test_documents_server_over_stdio_reports_gaps_and_masks_identity():
    async def run():
        params = StdioServerParameters(
            command=sys.executable,
            args=["-m", "app.mcp_servers.documents"],
            cwd=str(Path(__file__).resolve().parents[2]),
        )
        async with stdio_client(params) as (read, write), ClientSession(read, write) as session:
            await session.initialize()
            tools = {t.name for t in (await session.list_tools()).tools}
            prompts = {p.name for p in (await session.list_prompts()).prompts}
            missing = bundle(INITIAL_VISIT_RECORD=FORM_PAGES["INITIAL_VISIT_RECORD"][:-1])
            gaps = await session.call_tool("check_missing", missing)
            form = await session.call_tool("extract_fields", bundle())
            required = await session.read_resource("insai://required-fields")
            return tools, prompts, gaps.content[0].text, form.content[0].text, required

    tools, prompts, gaps, form, required = asyncio.run(run())
    assert tools == {"extract_fields", "check_missing"} and prompts == {"jev_review"}
    assert json.loads(gaps)["complete"] is False and "질병분류기호" in gaps
    assert "환자-A" in form
    for secret in ("테스트-김하늘", "1234567", "JVBER"):
        assert secret not in form
    assert "질병분류기호" in required.contents[0].text


def test_policy_server_searches_and_reads_clauses(monkeypatch):
    monkeypatch.setattr(
        policy, "retriever", lambda: InMemoryRetriever(load_clauses(), BigramEmbedding())
    )
    hits = policy.search_clauses("보험금의 청구 서류", k=3)
    assert len(hits) == 3 and {"clause_id", "article", "title", "excerpt", "score"} <= set(hits[0])
    assert policy.clause_text("basic:a7:1").startswith(
        "[표준약관 기본형(급여)] 제7조(보험금의 청구)"
    )
    assert "약관 검색 에이전트" in policy.policy_review()
