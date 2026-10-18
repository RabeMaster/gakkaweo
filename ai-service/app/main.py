from contextlib import asynccontextmanager
from math import isfinite

from fastapi import FastAPI, HTTPException
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.model import encode_text, is_model_ready, load_model, unload_model
from app.schemas import SimilarityRequest, SimilarityResponse


@asynccontextmanager
async def lifespan(app: FastAPI):
    load_model()
    try:
        yield
    finally:
        unload_model()


app = FastAPI(title="가까워 AI 서비스", version="0.1.0", lifespan=lifespan)


@app.exception_handler(RequestValidationError)
async def handle_request_validation_error(request, exc: RequestValidationError):
    # 라이브러리가 생성한 영문 검증 메시지도 한국어로 전달합니다.
    messages = {
        "missing": "필수 입력값이 누락되었습니다",
        "string_type": "문자열을 입력해야 합니다",
        "string_too_short": "입력은 2자 이상이어야 합니다",
        "string_too_long": "입력은 200자 이하여야 합니다",
        "json_invalid": "요청 본문의 JSON 형식이 올바르지 않습니다",
        "model_attributes_type": "요청 본문은 입력 항목을 포함하는 객체여야 합니다",
    }
    errors = exc.errors()
    for error in errors:
        if error["type"] != "normalized_text_too_short":
            error["msg"] = messages.get(error["type"], "요청 값이 올바르지 않습니다")
        if error["type"] == "json_invalid":
            error["ctx"] = {"error": "JSON 형식 오류"}
    return JSONResponse(status_code=422, content=jsonable_encoder({"detail": errors}))


@app.get("/health")
async def health_check():
    if not is_model_ready():
        raise HTTPException(status_code=503, detail="AI 모델이 아직 준비되지 않았습니다")
    return {"status": "ok"}


def cosine_similarity(sentence_embedding, guess_embedding) -> float:
    from sentence_transformers import util

    return util.cos_sim(sentence_embedding, guess_embedding).item()


def score_from_cosine(raw_score: float) -> float:
    if not isfinite(raw_score):
        raise ValueError("모델이 유한하지 않은 유사도 점수를 반환했습니다")
    return round(min(1.0, max(0.0, raw_score)) * 100, 1)


@app.post("/similarity", response_model=SimilarityResponse)
def compute_similarity(request: SimilarityRequest):
    try:
        sentence_embedding = encode_text(request.sentence)
        guess_embedding = encode_text(request.guess)
        score = score_from_cosine(cosine_similarity(sentence_embedding, guess_embedding))
    except Exception as exc:
        raise HTTPException(status_code=503, detail="AI 유사도 계산을 일시적으로 이용할 수 없습니다") from exc
    return SimilarityResponse(score=score)
