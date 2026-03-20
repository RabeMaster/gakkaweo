from functools import lru_cache

import numpy as np
from sentence_transformers import SentenceTransformer

model = SentenceTransformer("jhgan/ko-sbert-sts")


@lru_cache(maxsize=32)
def encode_text(text: str) -> np.ndarray:
    return model.encode(text)
