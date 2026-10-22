"""Draw explicitly fictional, image-only Korean medical documents; never real forms/seals."""

import argparse
import json
import random
from datetime import date, timedelta
from pathlib import Path
from typing import Any
from uuid import NAMESPACE_URL, uuid5

KINDS = ("ITEMIZED_BILL", "INITIAL_VISIT_RECORD", "MEDICAL_RECEIPT")
TITLES = ("진료비 세부산정내역서", "초진기록지", "진료비 계산서·영수증")


def sample(seed: int, scenario: str = "consistent") -> dict:
    rng = random.Random(seed)
    name = (
        "테스트-"
        + rng.choice(["김", "이", "박", "최"])
        + rng.choice(["하늘", "도윤", "지우", "서연"])
    )
    visit = date(2026, 9, 1) + timedelta(days=rng.randrange(30))
    rows: list[dict[str, Any]] = []
    for index, label in enumerate(["초진 진찰료", "검사료", "처치료", "비급여 검사"]):
        unit, quantity = rng.randrange(20, 180) * 100, rng.randrange(1, 4)
        total = unit * quantity
        patient = total * 3 // 10 if index < 2 else 0
        rows.append(
            {
                "name": label,
                "code": f"SYN{index + 1:03}",
                "unit": unit,
                "quantity": quantity,
                "days": 1,
                "total": total,
                "covered_patient": patient,
                "insurer": total - patient if index < 2 else 0,
                "noncovered": total if index >= 2 else 0,
            }
        )
    covered = sum(row["covered_patient"] for row in rows)
    insurer = sum(row["insurer"] for row in rows)
    noncovered = sum(row["noncovered"] for row in rows)
    return {
        "is_synthetic": True,
        "seed": seed,
        "scenario": scenario,
        "patient": name,
        "receipt_patient": name if scenario != "patient_mismatch" else "테스트-다른환자",
        "patient_id": f"SYN-{seed:08d}",
        "visit_date": visit.isoformat(),
        "onset_date": (visit - timedelta(days=3)).isoformat(),
        "issue_date": (visit + timedelta(days=1)).isoformat(),
        "birth_date": f"{rng.randrange(1970, 2005)}-03-15",
        "institution": "가상 테스트의원",
        "diagnosis_code": ""
        if scenario == "missing_code"
        else f"{rng.choice('JMK')}{rng.randrange(10, 90)}.{rng.randrange(10)}",
        "rows": rows,
        "covered_patient": covered,
        "insurer": insurer,
        "noncovered": noncovered,
        "gross": covered + insurer + noncovered,
        "patient_due": covered + noncovered,
        "receipt_due": covered + noncovered + (10000 if scenario == "amount_mismatch" else 0),
        "expected_checks": {
            "patient_match": scenario != "patient_mismatch",
            "amount_match": scenario != "amount_mismatch",
            "diagnosis_code_present": scenario != "missing_code",
        },
    }


def generate(
    output: Path, *, seed: int = 42, scenario: str = "consistent", font_path: Path
) -> dict:
    from PIL import Image, ImageDraw, ImageFont
    from reportlab.lib.utils import ImageReader
    from reportlab.pdfgen.canvas import Canvas

    if output.exists() and any(output.iterdir()):
        raise ValueError("Output directory must be empty; refusing to overwrite a dataset")
    output.mkdir(parents=True, exist_ok=True)
    data = sample(seed, scenario)
    regular = ImageFont.truetype(str(font_path), 28)
    small = ImageFont.truetype(str(font_path), 23)
    title_font = ImageFont.truetype(str(font_path), 48)
    documents = []
    for kind, title in zip(KINDS, TITLES, strict=True):
        image = Image.new("RGB", (1654, 2339), "white")
        draw = ImageDraw.Draw(image)
        draw.rectangle((45, 45, 1609, 2294), outline="#b6202a", width=4)
        draw.text(
            (80, 70), "합성 테스트 전용 / 실제 보험 청구 사용 불가", font=regular, fill="#b6202a"
        )
        draw.text((80, 145), title, font=title_font, fill="#162b42")
        draw.text(
            (80, 235),
            "실제 기관·의사·환자와 관계없는 랜덤 생성 자료 / 공식 서식 아님",
            font=small,
            fill="#555555",
        )
        y = 315

        def line(label: str, value: object) -> None:
            nonlocal y
            draw.rectangle((75, y - 8, 1579, y + 53), outline="#b9c4ce", width=1)
            draw.text((92, y), label, font=regular, fill="#304860")
            draw.text((470, y), str(value), font=regular, fill="#111111")
            y += 70

        line("환자 성명", data["receipt_patient"] if kind == "MEDICAL_RECEIPT" else data["patient"])
        line("합성 환자등록번호", data["patient_id"])
        line("생년월일 / 진료구분", data["birth_date"] + " / 외래")
        line("의료기관 / 진료과", data["institution"] + " / 가정의학과 (가상)")
        line("진료일", data["visit_date"])
        line("발급일", data["issue_date"])
        if kind == "INITIAL_VISIT_RECORD":
            line("주호소", "가상 증상: 불편감과 통증 - 테스트 서술")
            line("증상 시작일", data["onset_date"])
            line("현병력", "3일 전 증상 시작, 이번 방문은 가상 초진")
            line("과거력 / 복용약", "기재상 특이사항 없음 / 합성 기록")
            line("진단명", "가상 질환 (의학적 유효성 미검증)")
            line("질병분류기호", data["diagnosis_code"])
            line("진단 상태", "의증 / 검사 후 재평가 예정 (합성)")
            line("처치 및 검사 계획", "가상 검사·처치, 경과 관찰")
            line("기록자", "가상의사 / 실제 면허번호·서명 미사용")
        elif kind == "ITEMIZED_BILL":
            labels = [
                "항목 / 합성코드",
                "단가",
                "횟수",
                "일수",
                "총액",
                "본인부담",
                "공단부담",
                "비급여",
            ]
            widths = [350, 170, 90, 90, 200, 200, 200, 200]
            xs = [75]
            for width in widths:
                xs.append(xs[-1] + width)
            # Compact row font; names and synthetic codes occupy the wider first column.
            for index, values in enumerate(
                [labels]
                + [
                    [
                        row["name"] + " / " + row["code"],
                        f"{row['unit']:,}",
                        str(row["quantity"]),
                        "1",
                        f"{row['total']:,}",
                        f"{row['covered_patient']:,}",
                        f"{row['insurer']:,}",
                        f"{row['noncovered']:,}",
                    ]
                    for row in data["rows"]
                ]
            ):
                for column, value in enumerate(values):
                    draw.rectangle(
                        (xs[column], y, xs[column + 1], y + 75), outline="#7e91a3", width=1
                    )
                    draw.text((xs[column] + 8, y + 18), value, font=small, fill="#111111")
                y += 75
            y += 30
            line("진료비 총액", f"{data['gross']:,} 원")
            line("급여 본인부담 / 공단", f"{data['covered_patient']:,} / {data['insurer']:,} 원")
            line("비급여 합계", f"{data['noncovered']:,} 원")
            line("환자부담 총액", f"{data['patient_due']:,} 원")
        else:
            line("급여 본인부담금", f"{data['covered_patient']:,} 원")
            line("공단부담금", f"{data['insurer']:,} 원")
            line("전액본인부담금", "0 원")
            line("비급여", f"{data['noncovered']:,} 원")
            line("진료비 총액", f"{data['gross']:,} 원")
            line("환자부담 총액", f"{data['receipt_due']:,} 원")
            line("기납부 / 할인 / 환급", "0 / 0 / 0 원")
            line("납부액 / 미수금", f"{data['receipt_due']:,} / 0 원")
            line("수납방법", "가상 결제 - 실제 거래·카드번호 없음")
        draw.text(
            (80, 2150),
            "주의: 질병/수가 코드는 임의 문자열이며 실제 진단·수가·급여 적정성을 뜻하지 않습니다.",
            font=small,
            fill="#b6202a",
        )
        draw.text(
            (80, 2200),
            "SYNTHETIC DATA ONLY - NOT VALID FOR CLAIMS / 1 page",
            font=small,
            fill="#b6202a",
        )
        png = output / f"{kind.lower()}.png"
        pdf = output / f"{kind.lower()}.pdf"
        image.save(png)
        canvas = Canvas(str(pdf), pagesize=(595.28, 841.89))
        canvas.setTitle("SYNTHETIC ONLY - " + kind)
        canvas.drawImage(ImageReader(image), 0, 0, width=595.28, height=841.89)
        canvas.showPage()
        canvas.save()
        documents.append(
            {
                "document_id": str(uuid5(NAMESPACE_URL, f"synthetic:{seed}:{scenario}:{kind}")),
                "kind": kind,
                "path": pdf.name,
                "preview": png.name,
            }
        )
    manifest = {"schema_version": 1, **data, "documents": documents}
    (output / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--font", type=Path, required=True)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument(
        "--scenario",
        choices=["consistent", "patient_mismatch", "amount_mismatch", "missing_code"],
        default="consistent",
    )
    args = parser.parse_args()
    result = generate(args.output, seed=args.seed, scenario=args.scenario, font_path=args.font)
    print(
        json.dumps(
            {
                "status": "GENERATED_SYNTHETIC",
                "pdf_count": len(result["documents"]),
                "seed": args.seed,
            }
        )
    )
