"""MCP server `insai-policy`: RAG over the 실손의료보험 standard terms (pgvector).

`search_clauses` embeds the query and searches pgvector; clause text is the public FSS
standard terms. Run locally over stdio (`python -m app.mcp_servers.policy`).
"""

from functools import lru_cache

from mcp.server.mcpserver import MCPServer

from app.core.config import settings
from app.core.resources import load_prompt_section
from app.rag.chunking import load_clauses
from app.rag.store import Retriever, configured_retriever

server = MCPServer(
    name="insai-policy",
    instructions="실손의료보험 표준약관(금융감독원)에서 관련 조항을 찾습니다. 보상 여부는 판단하지 않습니다.",
)


@lru_cache
def retriever() -> Retriever:
    found = configured_retriever(settings())
    if found is None:
        raise RuntimeError("JEV_RAG_DATABASE_URL is not configured")
    return found


@server.tool(description="질의와 의미가 가까운 표준약관 조항 검색 (pgvector 코사인 유사도)")
def search_clauses(query: str, k: int = 5) -> list[dict]:
    hits = retriever().search(query[:500], max(1, min(k, 10)))
    return [
        {
            "clause_id": h.clause.id,
            "product": h.clause.product,
            "article": h.clause.article,
            "title": h.clause.title,
            "excerpt": " ".join(h.clause.text.split())[:400],
            "score": round(h.score, 4),
        }
        for h in hits
    ]


@server.resource("insai://terms/{clause_id}", mime_type="text/plain")
def clause_text(clause_id: str) -> str:
    """조항 원문 (clause_id는 search_clauses 결과의 값)."""
    clause = {c.id: c for c in load_clauses()}.get(clause_id)
    if clause is None:
        raise ValueError("Unknown clause")
    return f"[{clause.product}] {clause.article}({clause.title})\n{clause.text}"


@server.prompt(description="검색된 조항 중 확인할 조항을 고르는 약관 검색 에이전트 프롬프트")
def policy_review() -> str:
    return load_prompt_section("POLICY")


if __name__ == "__main__":
    server.run("stdio")
