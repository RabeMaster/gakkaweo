import unittest
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier, Lock
from time import sleep
from unittest.mock import Mock, patch

from fastapi.testclient import TestClient

from app import model
from app.main import app


class ModelLifecycleTest(unittest.TestCase):
    def tearDown(self):
        model.unload_model()

    def test_model_loads_once_and_warms_up_before_readiness(self):
        encoder = Mock()
        with patch("app.model._create_model", return_value=encoder) as factory:
            model.load_model()
            model.load_model()
        factory.assert_called_once()
        encoder.encode.assert_called_once_with("모델 준비 확인")
        self.assertTrue(model.is_model_ready())

    def test_failed_warmup_never_marks_model_ready(self):
        encoder = Mock()
        encoder.encode.side_effect = RuntimeError("모델 준비 확인 실패")
        with patch("app.model._create_model", return_value=encoder), self.assertRaises(RuntimeError):
            model.load_model()
        self.assertFalse(model.is_model_ready())

    def test_lifespan_enables_health_then_releases_model_and_cache(self):
        encoder = Mock()
        with patch("app.model._create_model", return_value=encoder), TestClient(app) as client:
            self.assertEqual(200, client.get("/health").status_code)
            model.encode_text("캐시 문장")
            self.assertEqual(1, model._cached_encode_text.cache_info().currsize)
        self.assertFalse(model.is_model_ready())
        self.assertEqual(0, model._cached_encode_text.cache_info().currsize)

    def test_concurrent_same_text_is_encoded_only_once(self):
        encoder = Mock()

        def encode(text):
            sleep(0.01)
            return [len(text)]

        encoder.encode.side_effect = encode
        with patch("app.model._create_model", return_value=encoder):
            model.load_model()
        encoder.encode.reset_mock()
        barrier = Barrier(4)

        def lookup():
            barrier.wait()
            return model.encode_text("같은 문장")

        with ThreadPoolExecutor(max_workers=4) as pool:
            values = list(pool.map(lambda _: lookup(), range(4)))
        encoder.encode.assert_called_once_with("같은 문장")
        self.assertEqual([[5]] * 4, values)

    def test_cache_is_bounded_and_cleared_between_model_instances(self):
        encoder = Mock()
        with patch("app.model._create_model", return_value=encoder):
            model.load_model()
        for index in range(40):
            model.encode_text(f"문장 {index}")
        self.assertEqual(32, model._cached_encode_text.cache_info().currsize)
        encoder.encode.reset_mock()
        model.encode_text("문장 0")
        encoder.encode.assert_called_once_with("문장 0")
        model.unload_model()
        with self.assertRaisesRegex(model.ModelNotReadyError, "AI 모델이 아직 준비되지 않았습니다"):
            model.encode_text("문장 39")

        replacement = Mock()
        with patch("app.model._create_model", return_value=replacement):
            model.load_model()
        replacement.encode.reset_mock()
        model.encode_text("문장 39")
        replacement.encode.assert_called_once_with("문장 39")

    def test_different_cpu_inferences_do_not_overlap(self):
        encoder = Mock()
        state_lock = Lock()
        active = 0
        peak = 0

        def encode(text):
            nonlocal active, peak
            with state_lock:
                active += 1
                peak = max(peak, active)
            sleep(0.01)
            with state_lock:
                active -= 1
            return [len(text)]

        encoder.encode.side_effect = encode
        with patch("app.model._create_model", return_value=encoder):
            model.load_model()
        encoder.encode.reset_mock()
        barrier = Barrier(4)

        def lookup(index):
            barrier.wait()
            return model.encode_text(f"다른 문장 {index}")

        with ThreadPoolExecutor(max_workers=4) as pool:
            list(pool.map(lookup, range(4)))
        self.assertEqual(1, peak)
        self.assertEqual(4, encoder.encode.call_count)

    def test_failed_inference_is_not_cached(self):
        encoder = Mock()
        with patch("app.model._create_model", return_value=encoder):
            model.load_model()
        encoder.encode.side_effect = [RuntimeError("추론 실패"), [1.0]]
        with self.assertRaises(RuntimeError):
            model.encode_text("재시도 문장")
        self.assertEqual(0, model._cached_encode_text.cache_info().currsize)
        self.assertEqual([1.0], model.encode_text("재시도 문장"))

    def test_lifespan_releases_model_after_request_context_error(self):
        with patch("app.model._create_model", return_value=Mock()):
            with self.assertRaises(RuntimeError), TestClient(app):
                model.encode_text("캐시 문장")
                raise RuntimeError("요청 컨텍스트 실패")
        self.assertFalse(model.is_model_ready())
        self.assertEqual(0, model._cached_encode_text.cache_info().currsize)


if __name__ == "__main__":
    unittest.main()
