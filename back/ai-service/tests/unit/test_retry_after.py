from datetime import datetime, timedelta, timezone
from email.utils import format_datetime

import pytest

from app.providers.retry_after import retry_after_seconds


@pytest.mark.parametrize(
    "value,expected",
    [
        (None, None),
        ("", None),
        ("nonsense", None),
        ("nan", None),
        ("inf", None),
        ("-1", None),
        ("0", 0),
        ("1.5", 1.5),
        ("60", 60),
    ],
)
def test_retry_after_numeric_validation(value, expected):
    assert retry_after_seconds(value) == expected


def test_retry_after_http_date():
    value = format_datetime(datetime.now(timezone.utc) + timedelta(seconds=60), usegmt=True)
    assert 58 <= retry_after_seconds(value) <= 60
