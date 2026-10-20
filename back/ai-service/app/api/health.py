from fastapi import APIRouter
router = APIRouter()


@router.get("/health/live")
def live():
    return {"status": "UP"}
