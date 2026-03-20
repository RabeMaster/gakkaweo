from fastapi import FastAPI
from sentence_transformers import util

from app.model import encode_text
from app.normalize import normalize_text
from app.schemas import SimilarityRequest, SimilarityResponse

app = FastAPI(title="Gakkaweo AI Service", version="0.1.0")


@app.get("/health")
def health_check():
    return {"status": "ok"}


@app.post("/similarity", response_model=SimilarityResponse)
def compute_similarity(request: SimilarityRequest):
    sentence = normalize_text(request.sentence)
    guess = normalize_text(request.guess)
    sentence_embedding = encode_text(sentence)
    guess_embedding = encode_text(guess)
    raw_score = util.cos_sim(sentence_embedding, guess_embedding).item()
    score = round(max(0.0, raw_score) * 100, 1)
    return SimilarityResponse(score=score)
