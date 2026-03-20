from pydantic import BaseModel, Field


class SimilarityRequest(BaseModel):
    sentence: str = Field(..., min_length=2, max_length=200)
    guess: str = Field(..., min_length=2, max_length=200)


class SimilarityResponse(BaseModel):
    score: float
