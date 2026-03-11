from fastapi import FastAPI
from sentence_transformers import util

from app.model import model
from app.schemas import SimilarityRequest, SimilarityResponse

app = FastAPI(title="Gakkaweo AI Service", version="0.1.0")


@app.get("/health")
def health_check():
    return {"status": "ok"}


@app.post("/similarity", response_model=SimilarityResponse)
def compute_similarity(request: SimilarityRequest):
    embeddings = model.encode([request.text1, request.text2])
    score = util.cos_sim(embeddings[0], embeddings[1]).item()
    return SimilarityResponse(score=score, text1=request.text1, text2=request.text2)
