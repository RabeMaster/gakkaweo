from pydantic import BaseModel, Field, field_validator
from pydantic_core import PydanticCustomError

from app.normalize import normalize_text


class SimilarityRequest(BaseModel):
    sentence: str = Field(..., min_length=2, max_length=200)
    guess: str = Field(..., min_length=2, max_length=200)

    @field_validator("sentence", "guess")
    @classmethod
    def validate_normalized_text(cls, value: str) -> str:
        normalized = normalize_text(value)
        if len(normalized) < 2:
            raise PydanticCustomError(
                "normalized_text_too_short",
                "정규화 후 유효한 문자(한글, 영문, 숫자)를 2자 이상 포함해야 합니다",
            )
        return normalized


class SimilarityResponse(BaseModel):
    score: float = Field(ge=0, le=100, allow_inf_nan=False)
