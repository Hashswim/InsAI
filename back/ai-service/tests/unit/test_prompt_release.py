from dataclasses import FrozenInstanceError
from hashlib import sha256
from unittest.mock import Mock

import pytest

from app.chains.release import ACTIVE_RELEASE
from app.core.resources import load_jev_prompt


def test_packaged_prompt_matches_immutable_release():
    prompt = load_jev_prompt()
    assert sha256(prompt.encode("utf-8")).hexdigest() == ACTIVE_RELEASE.prompt_sha256
    assert "Judgment" in prompt and "규정은 미등록 상태" in prompt
    with pytest.raises(FrozenInstanceError):
        ACTIVE_RELEASE.prompt_version = "changed"


@pytest.mark.parametrize("version", ["", "jev-v2", "../jev", "/tmp/prompt", "https://example.com"])
def test_unregistered_versions_never_resolve_a_resource(monkeypatch, version):
    resources = Mock()
    monkeypatch.setattr("app.core.resources.files", resources)
    with pytest.raises(ValueError, match="Unknown JEV prompt version"):
        load_jev_prompt(version)
    resources.assert_not_called()


@pytest.mark.parametrize("raw", [b"", b"SECRET_CHANGED_PROMPT", b"\xff"])
def test_modified_assets_are_rejected_without_content_disclosure(monkeypatch, raw):
    resources = Mock()
    resources.return_value.joinpath.return_value.read_bytes.return_value = raw
    monkeypatch.setattr("app.core.resources.files", resources)
    with pytest.raises(ValueError) as caught:
        load_jev_prompt()
    assert str(caught.value) == "JEV prompt release is unavailable or invalid"


def test_missing_asset_does_not_disclose_internal_path(monkeypatch):
    resources = Mock()
    resources.return_value.joinpath.return_value.read_bytes.side_effect = OSError("SECRET_PATH")
    monkeypatch.setattr("app.core.resources.files", resources)
    with pytest.raises(ValueError) as caught:
        load_jev_prompt()
    assert "SECRET" not in str(caught.value)
