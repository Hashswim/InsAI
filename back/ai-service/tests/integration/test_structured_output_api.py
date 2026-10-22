from unittest.mock import Mock

import pytest
from conftest import FakeProvider, root_run
from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage

from app.jev.release import ACTIVE_RELEASE
from app.main import app


@pytest.mark.parametrize(
    "failure",
    ["json", "duplicate_judgment", "duplicate_evidence", "unknown_page", "blank_reason", "none"],
)
def test_parsing_failure_is_terminal_and_trace_never_contains_raw_output(
    monkeypatch, sample_request, catalog_entry, catalog_path, failure, caplog
):
    for key, value in {
        "APP_ENV": "local",
        "JEV_PROVIDER": "gemini",
        "GOOGLE_API_KEY": "synthetic-key",
        "JEV_SERVICE_TOKEN": "synthetic-token",
        "JEV_ROUTING_POLICY": "priority",
        "JEV_MODEL_PRIORITY": '["first","second"]',
        "JEV_MODEL_CATALOG_PATH": str(
            catalog_path([catalog_entry(model="first"), catalog_entry(model="second")])
        ),
        "JEV_MAX_ATTEMPTS": "3",
        "JEV_RETRY_ATTEMPTS": "2",
        "JEV_FALLBACK_ENABLED": "true",
        "LANGSMITH_ENABLED": "true",
        "LANGSMITH_API_KEY": "synthetic-trace-key",
    }.items():
        monkeypatch.setenv(key, value)
    findings = FakeProvider().analyze(sample_request).findings
    findings.items[0].item = "SECRET_PATIENT"
    malformed = failure != "none"
    if failure == "duplicate_judgment":
        findings.items.append(findings.items[0].model_copy(deep=True))
    elif failure == "duplicate_evidence":
        findings.items[0].evidence.append(findings.items[0].evidence[0].model_copy(deep=True))
    elif failure == "unknown_page":
        findings.items[0].evidence[0].page = 2
    elif failure == "blank_reason":
        findings.items[0].verification_reason = " "
    text = findings.model_dump_json()
    if failure == "json":
        text = text.replace('"page":1', '"page":2,"page":1')
    structured = Mock()
    structured.invoke.return_value = {
        "parsed": findings,
        "raw": AIMessage(content=text, response_metadata={"finish_reason": "STOP"}),
        "parsing_error": None,
    }
    sdk = Mock()
    sdk.with_structured_output.return_value = structured
    factory = Mock(return_value=sdk)
    trace = Mock()
    monkeypatch.setattr("app.providers.gemini_provider.ChatGoogleGenerativeAI", factory)
    monkeypatch.setattr("langsmith.Client", Mock(return_value=trace))
    with TestClient(app) as client:
        response = client.post(
            "/internal/jev",
            json=sample_request.model_dump(mode="json"),
            headers={"Authorization": "Bearer synthetic-token"},
        )
    assert response.status_code == (502 if malformed else 200)
    assert response.headers["x-request-id"] == str(sample_request.request_id)
    if malformed:
        assert response.json()["error"]["code"] == "INVALID_AI_RESPONSE"
        assert "SECRET" not in response.text
    else:
        assert response.json()["items"][0]["item"] == "SECRET_PATIENT"
        assert response.json()["verification"]["factual_consistency"] == "NOT_VERIFIED"
        assert response.json()["verification"]["human_review"] == "REQUIRED"
    payload = root_run(trace)
    assert payload["inputs"] == payload["outputs"] == {}
    assert payload["error"] == ("ANALYSIS_FAILED" if malformed else None)
    assert payload["extra"]["metadata"]["execution_attempts"] == 1
    report = payload["extra"]["metadata"]
    assert report["orchestrator_version"] == "jev-orchestrator-v1"
    if failure == "none":
        assert report["last_workflow_completed_stages"] == "PREPARE,J,E,V"
        assert report["last_workflow_status"] == "COMPLETED"
        assert report["last_workflow_failed_stage"] == "NONE"
    else:
        expected = "E" if failure in {"duplicate_evidence", "unknown_page"} else "J"
        assert report["last_workflow_failed_stage"] == expected
        assert report["last_workflow_status"] == "FAILED"
    if malformed:
        assert not any(key.startswith("verification_") for key in payload["extra"]["metadata"])
    else:
        for key, value in response.json()["verification"].items():
            assert payload["extra"]["metadata"][f"verification_{key}"] == value
    for key, value in ACTIVE_RELEASE.metadata().items():
        assert payload["extra"]["metadata"][key] == value
    assert "SECRET_PATIENT" not in str(trace.mock_calls) + caplog.text
    structured.invoke.assert_called_once()
    factory.assert_called_once()
