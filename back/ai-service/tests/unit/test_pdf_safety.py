import base64
import logging
from io import BytesIO
from unittest.mock import Mock

import pytest
from fastapi.testclient import TestClient
from pypdf import PdfWriter
from pypdf.generic import ArrayObject, DictionaryObject, NameObject, TextStringObject

from app.core.config import Settings
from app.core.logging import install_secret_redaction
from app.main import app
from app.schemas.jev import PdfDocument


def encoded(writer):
    stream = BytesIO()
    writer.write(stream)
    return base64.b64encode(stream.getvalue()).decode()


def action(kind="/JavaScript"):
    return DictionaryObject(
        {
            NameObject("/S"): NameObject(kind),
            NameObject("/JS"): TextStringObject("SENTINEL_PATIENT"),
        }
    )


def writer_for(mutation):
    writer = PdfWriter()
    page = writer.add_blank_page(width=300, height=300)
    root = writer.root_object
    if mutation == "javascript":
        writer.add_js("SENTINEL_PATIENT")
    elif mutation == "attachment":
        writer.add_attachment("SENTINEL_PATIENT.txt", b"synthetic")
    elif mutation == "open_action":
        root[NameObject("/OpenAction")] = action()
    elif mutation == "page_action":
        page[NameObject("/AA")] = DictionaryObject({NameObject("/O"): action()})
    elif mutation == "annotation_action":
        page[NameObject("/Annots")] = ArrayObject([DictionaryObject({NameObject("/A"): action()})])
    elif mutation == "xfa":
        root[NameObject("/AcroForm")] = DictionaryObject(
            {NameObject("/XFA"): TextStringObject("SENTINEL_PATIENT")}
        )
    elif mutation == "hidden_field":
        child = DictionaryObject(
            {NameObject("/AA"): DictionaryObject({NameObject("/K"): action()})}
        )
        parent = DictionaryObject({NameObject("/Kids"): ArrayObject([child])})
        root[NameObject("/AcroForm")] = DictionaryObject(
            {NameObject("/Fields"): ArrayObject([parent])}
        )
    elif mutation == "rich_media":
        page[NameObject("/Annots")] = ArrayObject(
            [DictionaryObject({NameObject("/Subtype"): NameObject("/RichMedia")})]
        )
    elif mutation == "associated_file":
        root[NameObject("/AF")] = ArrayObject()
    elif mutation == "encrypted":
        writer.encrypt("synthetic-password")
    elif mutation.startswith("/URI:"):
        uri = mutation.removeprefix("/URI:")
        page[NameObject("/A")] = DictionaryObject(
            {NameObject("/S"): NameObject("/URI"), NameObject("/URI"): TextStringObject(uri)}
        )
    elif mutation.startswith("/"):
        root[NameObject("/OpenAction")] = action(mutation)
    return writer


@pytest.mark.parametrize(
    "mutation",
    [
        "javascript",
        "attachment",
        "open_action",
        "page_action",
        "annotation_action",
        "xfa",
        "hidden_field",
        "rich_media",
        "associated_file",
        "encrypted",
        "/Launch",
        "/SubmitForm",
        "/ImportData",
        "/GoToR",
        "/Named",
        "/URI:javascript:alert(1)",
        "/URI:data:text/html,SENTINEL",
        "/URI:file:///etc/passwd",
    ],
)
def test_unsupported_pdf_is_rejected_before_model_execution(
    monkeypatch, request_data, mutation, caplog
):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    request_data["documents"][0]["content_base64"] = encoded(writer_for(mutation))
    model = Mock(side_effect=AssertionError("Model must not run"))
    monkeypatch.setattr("app.providers.stub_provider.StubProvider.analyze", model)
    with TestClient(app) as client:
        response = client.post(
            "/internal/jev", json=request_data, headers={"Authorization": "Bearer test-token"}
        )
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "INVALID_INPUT"
    assert "SENTINEL" not in response.text + caplog.text
    model.assert_not_called()


@pytest.mark.parametrize(
    "uri",
    ["https://hospital.invalid", "http://hospital.invalid", "mailto:a@example.invalid", "tel:000"],
)
def test_static_uri_does_not_cause_fetch_or_rejection(request_data, uri):
    data = request_data["documents"][0]
    data["content_base64"] = encoded(writer_for("/URI:" + uri))
    assert PdfDocument.model_validate(data).page_count() == 1


def test_action_after_static_action_is_still_checked(request_data):
    writer = writer_for("plain")
    first = DictionaryObject(
        {NameObject("/S"): NameObject("/GoTo"), NameObject("/D"): TextStringObject("page")}
    )
    first[NameObject("/Next")] = action()
    writer.root_object[NameObject("/OpenAction")] = first
    data = {**request_data["documents"][0], "content_base64": encoded(writer)}
    with pytest.raises(ValueError):
        PdfDocument.model_validate(data)


def test_passive_form_fields_are_allowed(request_data):
    writer = writer_for("plain")
    field = DictionaryObject(
        {NameObject("/FT"): NameObject("/Tx"), NameObject("/T"): TextStringObject("field")}
    )
    writer.root_object[NameObject("/AcroForm")] = DictionaryObject(
        {NameObject("/Fields"): ArrayObject([field])}
    )
    PdfDocument.model_validate({**request_data["documents"][0], "content_base64": encoded(writer)})


def test_plain_opening_destination_is_not_an_active_action(request_data):
    writer = writer_for("plain")
    writer.root_object[NameObject("/OpenAction")] = TextStringObject("chapter")
    PdfDocument.model_validate({**request_data["documents"][0], "content_base64": encoded(writer)})


@pytest.mark.parametrize("limit", ["actions", "structures", "annotations"])
def test_pdf_policy_traversals_have_explicit_bounds(monkeypatch, request_data, limit):
    writer = writer_for("plain")
    if limit == "actions":
        monkeypatch.setattr("app.core.pdf_safety.MAX_ACTIONS", 1)
        first = action("/GoTo")
        first[NameObject("/Next")] = ArrayObject([action("/GoTo"), action("/GoTo")])
        writer.root_object[NameObject("/OpenAction")] = first
    elif limit == "structures":
        monkeypatch.setattr("app.core.pdf_safety.MAX_STRUCTURES", 1)
    else:
        monkeypatch.setattr("app.core.pdf_safety.MAX_PAGE_ANNOTATIONS", 1)
        writer.pages[0][NameObject("/Annots")] = ArrayObject(
            [DictionaryObject(), DictionaryObject()]
        )
    with pytest.raises(ValueError):
        PdfDocument.model_validate(
            {**request_data["documents"][0], "content_base64": encoded(writer)}
        )


def test_cyclic_action_chain_is_not_an_unbounded_walk(request_data):
    writer = writer_for("plain")
    first = action("/GoTo")
    ref = writer._add_object(first)
    first[NameObject("/Next")] = ref
    writer.root_object[NameObject("/OpenAction")] = ref
    PdfDocument.model_validate({**request_data["documents"][0], "content_base64": encoded(writer)})


@pytest.mark.parametrize(
    "root",
    [
        "pypdf._reader",
        "httpx",
        "httpcore.connection",
        "langsmith.client",
        "langchain_core.output_parsers",
        "uvicorn.access",
    ],
)
def test_sensitive_library_diagnostics_are_suppressed(caplog, root):
    restore = install_secret_redaction(Settings())
    try:
        with caplog.at_level(logging.WARNING):
            try:
                raise ValueError("SENTINEL_PATIENT")
            except ValueError:
                logging.getLogger(root).exception("payload=%s", "SENTINEL_PATIENT")
        assert "SENTINEL_PATIENT" not in caplog.text
        assert "diagnostic suppressed" in caplog.text
    finally:
        restore()
