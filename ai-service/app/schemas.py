from pydantic import BaseModel, Field


class SimilarityRequest(BaseModel):
    text1: str = Field(..., min_length=2, max_length=200)
    text2: str = Field(..., min_length=2, max_length=200)


class SimilarityResponse(BaseModel):
    score: float
    text1: str
    text2: str
