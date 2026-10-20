import math
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime


def retry_after_seconds(value: str | None) -> float | None:
    if not value:
        return None
    try:
        delay = float(value)
    except ValueError:
        try:
            date = parsedate_to_datetime(value)
            if date.tzinfo is None:
                return None
            delay = (date - datetime.now(timezone.utc)).total_seconds()
        except (ValueError, TypeError, OverflowError):
            return None
    return max(0.0, delay) if math.isfinite(delay) and delay >= 0 else None
