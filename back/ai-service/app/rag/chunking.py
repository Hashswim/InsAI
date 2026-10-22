"""Article-level chunks of the standard terms; long articles split at line boundaries."""

import re
from dataclasses import dataclass
from hashlib import sha256
from importlib.resources import files

MAX_CHUNK_CHARS = 1200
# (corpus file, product markers). A marker line starting with the prefix opens that product.
SOURCES = (
    (
        "rag/data/silson_standard_terms.txt",
        (
            ("□ 기본형 실손의료보험", "basic", "표준약관 기본형(급여)"),
            ("□ 실손의료보험 특별약관1", "rider1", "표준약관 특약1(중증 비급여)"),
            ("□ 실손의료보험 특별약관2", "rider2", "표준약관 특약2(비중증 비급여)"),
        ),
    ),
    (
        "rag/data/samsung_direct_silson.txt",
        (
            ("□ 삼성화재 다이렉트 실손 기본형", "ssbasic", "삼성화재 다이렉트 실손 기본형(급여)"),
            (
                "□ 삼성화재 다이렉트 실손 특약1",
                "ssrider1",
                "삼성화재 다이렉트 실손 특약1(중증 비급여)",
            ),
            (
                "□ 삼성화재 다이렉트 실손 특약2",
                "ssrider2",
                "삼성화재 다이렉트 실손 특약2(비중증 비급여)",
            ),
        ),
    ),
    (
        "rag/data/hanwha_e_silson.txt",
        (
            ("□ 한화생명 e실손 주계약", "hwmain", "한화생명 e실손 주계약(급여)"),
            ("□ 한화생명 e실손 급여 특약", "hwrider", "한화생명 e실손 급여 특약"),
            ("□ 한화생명 비급여 실손 특약", "hwnoncov", "한화생명 비급여 실손 특약"),
        ),
    ),
)
# 제N조(제목) · 제N조[제목] · 제 N 조 제목 (insurer PDFs put the title on its own line).
ARTICLE = re.compile(
    r"^(제\s*(\d+)\s*조(?:\s*의\s*(\d+))?)\s*"
    r"(?:\(([^)]*)\)|\[([^\]]*)\]|([^\s(①-⑳][^①-⑳]{0,38}?)\s*$)"
)
APPENDIX = re.compile(r"^<(붙임(\d+))>\s*([^<]+)")


@dataclass(frozen=True)
class Clause:
    id: str
    product: str
    article: str
    title: str
    text: str


def corpus_text() -> str:
    return "\n".join(files("app").joinpath(path).read_text(encoding="utf-8") for path, _ in SOURCES)


def corpus_sha256() -> str:
    return sha256(corpus_text().encode("utf-8")).hexdigest()


def _split(lines: list[str]) -> list[str]:
    parts, current = [], ""
    for line in lines:
        if current and len(current) + len(line) + 1 > MAX_CHUNK_CHARS:
            parts.append(current)
            current = ""
        current = f"{current}\n{line}" if current else line
    if current:
        parts.append(current)
    return parts


def load_clauses() -> list[Clause]:
    clauses: list[Clause] = []
    for path, products in SOURCES:
        clauses += _source_clauses(
            files("app").joinpath(path).read_text(encoding="utf-8"), products
        )
    return clauses


def _source_clauses(text: str, products: tuple[tuple[str, str, str], ...]) -> list[Clause]:
    clauses: list[Clause] = []
    product_key = product_name = None
    heading: tuple[str, str, str] | None = None  # (id stem, article label, title)
    body: list[str] = []
    seen: set[str] = set()

    def flush() -> None:
        if heading is None or product_key is None or not body:
            return
        stem, article, title = heading
        for index, part in enumerate(_split(body), start=1):
            clauses.append(
                Clause(f"{product_key}:{stem}:{index}", product_name, article, title, part)
            )

    def open_heading(stem: str, article: str, title: str) -> tuple[str, str, str]:
        unique, n = stem, 2
        while unique in seen:  # a repeated article number in one product gets its own id
            unique, n = f"{stem}_{n}", n + 1
        seen.add(unique)
        return unique, article, title

    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        product = next((p for p in products if line.startswith(p[0])), None)
        if product:
            flush()
            _, product_key, product_name = product
            heading, body, seen = None, [], set()
            continue
        if (match := ARTICLE.match(line)) and product_key:
            title = (match.group(4) or match.group(5) or match.group(6) or "").strip()
            if title:
                flush()
                stem = f"a{match.group(2)}" + (f"_{match.group(3)}" if match.group(3) else "")
                label = re.sub(r"\s+", "", match.group(1))
                heading, body = open_heading(stem, label, title[:120]), [line]
                continue
        if (match := APPENDIX.match(line)) and product_key:
            flush()
            title = re.sub(r"\s*<.*$", "", match.group(3)).strip()
            heading, body = open_heading(f"app{match.group(2)}", match.group(1), title), [line]
            continue
        if heading is not None:
            body.append(line)
    flush()
    return clauses
