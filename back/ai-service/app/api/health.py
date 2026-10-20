from fastapi import APIRouter

router = APIRouter()


@router.get("/health/live")
def live() -> dict[str, str]:
    return {"status": "UP"}
