from functools import lru_cache
from threading import RLock
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    import numpy as np
    from sentence_transformers import SentenceTransformer

MODEL_NAME = "jhgan/ko-sbert-sts"
model: SentenceTransformer | None = None
_model_lock = RLock()


class ModelNotReadyError(RuntimeError):
    pass


def _create_model():
    # 모듈 import만으로 모델 다운로드나 PyTorch 초기화를 실행하지 않습니다.
    from sentence_transformers import SentenceTransformer

    return SentenceTransformer(MODEL_NAME, device="cpu")


def load_model() -> None:
    global model
    with _model_lock:
        if model is not None:
            return
        candidate = _create_model()
        # 실제 encode가 성공해야 준비 상태를 공개합니다.
        candidate.encode("모델 준비 확인")
        model = candidate


def unload_model() -> None:
    global model
    with _model_lock:
        model = None
        _cached_encode_text.cache_clear()


def is_model_ready() -> bool:
    return model is not None


@lru_cache(maxsize=32)
def _cached_encode_text(text: str) -> np.ndarray:
    if model is None:
        raise ModelNotReadyError("AI 모델이 아직 준비되지 않았습니다")
    return model.encode(text)


def encode_text(text: str) -> np.ndarray:
    # 같은 잠금으로 CPU 추론과 캐시 미스를 직렬화하여 중복 계산을 막습니다.
    with _model_lock:
        if model is None:
            raise ModelNotReadyError("AI 모델이 아직 준비되지 않았습니다")
        return _cached_encode_text(text)
