"""Embed the 실손의료보험 standard terms and load them into pgvector.

Usage: python -m scripts.ingest_terms  (reads JEV_RAG_DATABASE_URL, OPENAI_API_KEY from .env)
"""

from app.core.config import settings
from app.rag.chunking import corpus_sha256, load_clauses
from app.rag.store import ingest, openai_embeddings


def main() -> None:
    config = settings()
    dsn = config.jev_rag_database_url.get_secret_value()
    if not dsn:
        raise SystemExit("JEV_RAG_DATABASE_URL is not set")
    embeddings = openai_embeddings(
        config.jev_embedding_model, config.openai_api_key.get_secret_value()
    )
    count = ingest(dsn, embeddings, load_clauses())
    print(f"ingested {count} clauses (corpus sha256 {corpus_sha256()[:12]})")


if __name__ == "__main__":
    main()
