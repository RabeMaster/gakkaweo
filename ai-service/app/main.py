from fastapi import FastAPI
from sentence_transformers import util

from app.model import model
from app.normalize import normalize_text
from app.schemas import SimilarityRequest, SimilarityResponse

app = FastAPI(title="Gakkaweo AI Service", version="0.1.0")


@app.get("/health")
def health_check():
    return {"status": "ok"}


@app.post("/similarity", response_model=SimilarityResponse)
def compute_similarity(request: SimilarityRequest):
    text1 = normalize_text(request.text1)
    text2 = normalize_text(request.text2)
    embeddings = model.encode([text1, text2])
    raw_score = util.cos_sim(embeddings[0], embeddings[1]).item()
    score = round(max(0.0, raw_score) * 100, 1)
    return SimilarityResponse(score=score, text1=text1, text2=text2)
