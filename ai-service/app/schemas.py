from pydantic import BaseModel


class SimilarityRequest(BaseModel):
    text1: str
    text2: str


class SimilarityResponse(BaseModel):
    score: float
    text1: str
    text2: str
