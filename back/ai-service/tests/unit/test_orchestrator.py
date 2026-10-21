from concurrent.futures import ThreadPoolExecutor
from dataclasses import FrozenInstanceError
from threading import Barrier
from unittest.mock import Mock
from uuid import uuid4

import pytest
from conftest import FakeProvider

from app.chains.orchestrator import (
    STAGES,
    JevOrchestrator,
    WorkflowProgress,
    WorkflowReport,
    WorkflowStage,
)
from app.chains.verification import VerificationChain
from app.providers.base import ProviderRequest


def stages(monkeypatch, sample_request):
    draft = FakeProvider().analyze(sample_request)
    verified = VerificationChain().run(draft)
    events = []
    mocks = {}
    for name, result in [
        ("JudgmentChain", draft),
        ("EvidenceChain", draft),
        ("VerificationChain", verified),
    ]:
        stage = Mock()

        def action(*args, _name=name, _result=result, **kwargs):
            events.append(_name)
            return _result

        stage.run.side_effect = action
        mocks[name] = stage
        monkeypatch.setattr(f"app.chains.orchestrator.{name}", Mock(return_value=stage))
    return mocks, events, draft, verified


def test_fixed_order_maps_input_and_returns_only_verified_result(monkeypatch, sample_request):
    mocks, events, draft, verified = stages(monkeypatch, sample_request)
    progress = WorkflowProgress()
    result = JevOrchestrator().run(sample_request, FakeProvider(), progress=progress)
    assert result is verified
    assert events == ["JudgmentChain", "EvidenceChain", "VerificationChain"]
    port = mocks["JudgmentChain"].run.call_args.args[0]
    assert isinstance(port, ProviderRequest)
    assert port.request_id == sample_request.request_id
    snapshot, received_draft = mocks["EvidenceChain"].run.call_args.args
    assert snapshot == sample_request and snapshot is not sample_request
    assert received_draft is draft
    mocks["VerificationChain"].run.assert_called_once_with(draft, offline_stub=False)
    assert progress.report() == WorkflowReport(STAGES, None)


@pytest.mark.parametrize("failure", list(WorkflowStage))
def test_failure_stops_all_later_stages_without_a_partial_result(
    monkeypatch, sample_request, failure
):
    mocks, events, _, _ = stages(monkeypatch, sample_request)
    progress = WorkflowProgress()
    request = sample_request
    error = ValueError("SECRET_PATIENT")
    if failure == WorkflowStage.PREPARE:
        request = {}
    else:
        name = {
            WorkflowStage.JUDGMENT: "JudgmentChain",
            WorkflowStage.EVIDENCE: "EvidenceChain",
            WorkflowStage.VERIFICATION: "VerificationChain",
        }[failure]
        mocks[name].run.side_effect = error
    with pytest.raises(ValueError):
        JevOrchestrator().run(request, FakeProvider(), progress=progress)
    report = progress.report()
    index = STAGES.index(failure)
    assert report.completed == STAGES[:index]
    assert report.failed == failure
    assert report.metadata()["last_workflow_status"] == "FAILED"
    assert "SECRET" not in repr(report) + str(report.metadata())
    for offset, name in enumerate(("JudgmentChain", "EvidenceChain", "VerificationChain"), start=1):
        assert mocks[name].run.call_count == int(offset <= index)


@pytest.mark.parametrize(
    "name,stage",
    [
        ("JudgmentChain", WorkflowStage.JUDGMENT),
        ("EvidenceChain", WorkflowStage.EVIDENCE),
        ("VerificationChain", WorkflowStage.VERIFICATION),
    ],
)
def test_wrong_stage_result_type_is_not_allowed_to_complete(
    monkeypatch, sample_request, name, stage
):
    mocks, _, _, _ = stages(monkeypatch, sample_request)
    mocks[name].run.side_effect = None
    mocks[name].run.return_value = None
    progress = WorkflowProgress()
    with pytest.raises(ValueError, match="stage result"):
        JevOrchestrator().run(sample_request, FakeProvider(), progress=progress)
    assert progress.report().failed == stage


def test_progress_snapshot_is_immutable_and_cannot_be_reused(sample_request):
    progress = WorkflowProgress()
    before = progress.report()
    orchestrator = JevOrchestrator()
    orchestrator.run(sample_request, FakeProvider(), progress=progress)
    assert before.completed == ()
    assert progress.report().completed == STAGES
    with pytest.raises(FrozenInstanceError):
        before.failed = WorkflowStage.JUDGMENT
    with pytest.raises(ValueError, match="cannot be reused"):
        orchestrator.run(sample_request, FakeProvider(), progress=progress)


@pytest.mark.parametrize("stage", [WorkflowStage.EVIDENCE, "PREPARE", None])
def test_cannot_skip_stages_or_accept_arbitrary_stage_labels(stage):
    progress = WorkflowProgress()
    with pytest.raises(ValueError, match="Invalid JEV stage transition"):
        progress.begin(stage)


@pytest.mark.parametrize(
    "completed,failed",
    [
        (("SECRET_PATIENT",), None),
        ((WorkflowStage.EVIDENCE,), None),
        (STAGES, WorkflowStage.VERIFICATION),
        ((), WorkflowStage.EVIDENCE),
    ],
)
def test_report_metadata_accepts_only_valid_closed_stage_sequences(completed, failed):
    with pytest.raises(ValueError) as caught:
        WorkflowReport(completed, failed)
    assert "SECRET" not in str(caught.value)


def test_caller_request_mutation_during_j_does_not_change_e_scope(sample_request):
    expected_id = sample_request.documents[0].document_id
    provider = FakeProvider()

    def analyze(port):
        sample_request.documents[0].document_id = uuid4()
        sample_request.documents[0].content_base64 = "SECRET_CHANGED_INPUT"
        return FakeProvider().analyze(port)

    provider.analyze = Mock(side_effect=analyze)
    result = JevOrchestrator().run(sample_request, provider)
    assert result.findings.items[0].evidence[0].document_id == expected_id
    provider.analyze.assert_called_once()


@pytest.mark.parametrize("fail_one", [False, True])
def test_one_orchestrator_is_safe_for_concurrent_independent_runs(sample_request, fail_one):
    other = sample_request.model_copy(deep=True)
    other.request_id = uuid4()
    for document in other.documents:
        document.document_id = uuid4()
    barrier = Barrier(2)
    provider = FakeProvider()
    orchestrator = JevOrchestrator()

    def analyze(port):
        barrier.wait(timeout=5)
        if fail_one and port.request_id == other.request_id:
            raise ValueError("SECRET_FAILURE")
        return FakeProvider().analyze(port)

    provider.analyze = analyze

    def run(request):
        progress = WorkflowProgress()
        try:
            result = orchestrator.run(request, provider, progress=progress)
        except ValueError:
            return None, progress.report()
        return result.findings.items[0].evidence[0].document_id, progress.report()

    with ThreadPoolExecutor(max_workers=2) as pool:
        first, second = list(pool.map(run, (sample_request, other)))
    assert first == (sample_request.documents[0].document_id, WorkflowReport(STAGES, None))
    if fail_one:
        assert second == (None, WorkflowReport((WorkflowStage.PREPARE,), WorkflowStage.JUDGMENT))
    else:
        assert second == (other.documents[0].document_id, WorkflowReport(STAGES, None))


def test_cancellation_marks_stage_and_is_not_swallowed(monkeypatch, sample_request):
    class Cancelled(BaseException):
        pass

    mocks, _, _, _ = stages(monkeypatch, sample_request)
    mocks["EvidenceChain"].run.side_effect = Cancelled()
    progress = WorkflowProgress()
    with pytest.raises(Cancelled):
        JevOrchestrator().run(sample_request, FakeProvider(), progress=progress)
    assert progress.report().failed == WorkflowStage.EVIDENCE
    mocks["VerificationChain"].run.assert_not_called()
