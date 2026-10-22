"""Vector search over clause chunks: pgvector in Postgres, or in memory for tests."""

import math
from dataclasses import dataclass
from typing import TYPE_CHECKING, Protocol

from langchain_core.embeddings import Embeddings

from app.rag.chunking import Clause, corpus_sha256

if TYPE_CHECKING:
    from app.core.config import Settings

TABLE = "rag_policy_chunks"


@dataclass(frozen=True)
class Hit:
    clause: Clause
    score: float


class Retriever(Protocol):
    def search(self, query: str, k: int) -> list[Hit]: ...


def embed_text(clause: Clause) -> str:
    # Split parts lose their heading; repeat it so every chunk carries its article context.
    return f"[{clause.product}] {clause.article}({clause.title})\n{clause.text}"


def _cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b, strict=True))
    norm = math.sqrt(sum(x * x for x in a)) * math.sqrt(sum(y * y for y in b))
    return dot / norm if norm else 0.0


class InMemoryRetriever:
    def __init__(self, clauses: list[Clause], embeddings: Embeddings) -> None:
        self.clauses = clauses
        self.embeddings = embeddings
        self.vectors = embeddings.embed_documents([embed_text(c) for c in clauses])

    def search(self, query: str, k: int) -> list[Hit]:
        target = self.embeddings.embed_query(query)
        scored = [
            Hit(c, _cosine(target, v)) for c, v in zip(self.clauses, self.vectors, strict=True)
        ]
        return sorted(scored, key=lambda hit: hit.score, reverse=True)[:k]


def _vector(values: list[float]) -> str:
    return "[" + ",".join(f"{v:.7f}" for v in values) + "]"


class PgVectorRetriever:
    """Reads chunks written by `ingest`; one short connection per search."""

    def __init__(self, dsn: str, embeddings: Embeddings) -> None:
        self.dsn = dsn
        self.embeddings = embeddings

    def search(self, query: str, k: int) -> list[Hit]:
        import psycopg

        vector = _vector(self.embeddings.embed_query(query))
        with psycopg.connect(self.dsn, connect_timeout=5) as conn:
            rows = conn.execute(
                f"SELECT id, product, article, title, text, 1 - (embedding <=> %s::vector) "
                f"FROM {TABLE} ORDER BY embedding <=> %s::vector LIMIT %s",
                (vector, vector, k),
            ).fetchall()
        return [Hit(Clause(*row[:5]), float(row[5])) for row in rows]


def ingest(dsn: str, embeddings: Embeddings, clauses: list[Clause]) -> int:
    """Replace the stored chunks with the current corpus (idempotent)."""
    import psycopg

    vectors = embeddings.embed_documents([embed_text(c) for c in clauses])
    corpus = corpus_sha256()
    with psycopg.connect(dsn, connect_timeout=5) as conn:
        conn.execute("CREATE EXTENSION IF NOT EXISTS vector")
        conn.execute(
            f"CREATE TABLE IF NOT EXISTS {TABLE} (id text PRIMARY KEY, product text NOT NULL, "
            f"article text NOT NULL, title text NOT NULL, text text NOT NULL, "
            f"embedding vector({len(vectors[0])}) NOT NULL, corpus_sha256 text NOT NULL)"
        )
        conn.execute(f"DELETE FROM {TABLE}")
        with conn.cursor() as cur:
            cur.executemany(
                f"INSERT INTO {TABLE} VALUES (%s, %s, %s, %s, %s, %s::vector, %s)",
                [
                    (c.id, c.product, c.article, c.title, c.text, _vector(v), corpus)
                    for c, v in zip(clauses, vectors, strict=True)
                ],
            )
    return len(clauses)


def openai_embeddings(model: str, api_key: str) -> Embeddings:
    from langchain_openai import OpenAIEmbeddings

    return OpenAIEmbeddings(model=model, api_key=api_key, max_retries=1, timeout=20)  # type: ignore[arg-type]


def configured_retriever(config: "Settings") -> Retriever | None:
    """Retriever from settings, or None when RAG is not configured."""
    dsn = config.jev_rag_database_url.get_secret_value()
    if not dsn:
        return None
    return PgVectorRetriever(
        dsn, openai_embeddings(config.jev_embedding_model, config.openai_api_key.get_secret_value())
    )
