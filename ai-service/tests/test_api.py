import math
import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.main import app, score_from_cosine
from app.schemas import SimilarityRequest, SimilarityResponse


class SimilarityApiTest(unittest.TestCase):
    def test_import_does_not_load_model(self):
        from app import model

        self.assertFalse(model.is_model_ready())

    def test_health_is_unavailable_until_model_is_ready(self):
        with TestClient(app, backend="asyncio") as client:
            response = client.get("/health")
            self.assertEqual(503, response.status_code)
            self.assertEqual({"detail": "AI 모델이 아직 준비되지 않았습니다"}, response.json())

    def test_invalid_normalized_text_is_rejected_before_encoding(self):
        with patch("app.main.encode_text") as encode:
            for field in ("sentence", "guess"):
                for invalid in ("!!!", "  ", "나!", "🙂🙂"):
                    with self.subTest(field=field, invalid=invalid):
                        payload = {"sentence": "정답 문장", "guess": "추측 문장", field: invalid}
                        response = TestClient(app).post("/similarity", json=payload)
                        self.assertEqual(422, response.status_code)
            encode.assert_not_called()

    def test_normalization_precedes_embedding_and_score_is_clamped(self):
        with (
            patch("app.main.encode_text", return_value=[1.0]) as encode,
            patch("app.main.cosine_similarity", return_value=1.00001),
        ):
            response = TestClient(app).post("/similarity", json={"sentence": "가나다!", "guess": "  ABC  \n DEF? "})
        self.assertEqual(200, response.status_code)
        self.assertEqual({"score": 100.0}, response.json())
        self.assertEqual(["가나다", "ABC DEF"], [call.args[0] for call in encode.call_args_list])

    def test_unready_model_returns_503(self):
        response = TestClient(app).post("/similarity", json={"sentence": "정답 문장", "guess": "추측 문장"})
        self.assertEqual(503, response.status_code)

    def test_non_finite_model_output_returns_503(self):
        with (
            patch("app.main.encode_text", return_value=[1.0]),
            patch("app.main.cosine_similarity", return_value=math.nan),
        ):
            response = TestClient(app).post("/similarity", json={"sentence": "정답 문장", "guess": "추측 문장"})
        self.assertEqual(503, response.status_code)

    def test_inference_failure_does_not_expose_internal_error(self):
        with patch("app.main.encode_text", side_effect=RuntimeError("비공개 모델 상세 정보")):
            response = TestClient(app).post("/similarity", json={"sentence": "정답 문장", "guess": "추측 문장"})
        self.assertEqual(503, response.status_code)
        self.assertEqual({"detail": "AI 유사도 계산을 일시적으로 이용할 수 없습니다"}, response.json())

    def test_request_validation_messages_are_in_korean(self):
        cases = (
            ({"sentence": "가나"}, "필수 입력값이 누락되었습니다"),
            ({"sentence": "가나", "guess": 123}, "문자열을 입력해야 합니다"),
            ({"sentence": "가나", "guess": "가"}, "입력은 2자 이상이어야 합니다"),
            ({"sentence": "가나", "guess": "가" * 201}, "입력은 200자 이하여야 합니다"),
            (
                {"sentence": "가나", "guess": "!!!"},
                "정규화 후 유효한 문자(한글, 영문, 숫자)를 2자 이상 포함해야 합니다",
            ),
            ([], "요청 본문은 입력 항목을 포함하는 객체여야 합니다"),
        )
        with patch("app.main.encode_text") as encode:
            for payload, message in cases:
                with self.subTest(payload=payload):
                    response = TestClient(app).post("/similarity", json=payload)
                    self.assertEqual(422, response.status_code)
                    error = response.json()["detail"][0]
                    self.assertEqual(message, error["msg"])
                    self.assertIn("loc", error)
                    self.assertIn("type", error)
            response = TestClient(app).post("/similarity", content="{", headers={"Content-Type": "application/json"})
            self.assertEqual(422, response.status_code)
            self.assertEqual("요청 본문의 JSON 형식이 올바르지 않습니다", response.json()["detail"][0]["msg"])
            encode.assert_not_called()

    def test_score_preserves_rounding_and_rejects_non_finite_values(self):
        for raw, expected in ((-0.5, 0.0), (0.75321, 75.3), (1.0001, 100.0)):
            self.assertEqual(expected, score_from_cosine(raw))
        for raw in (math.nan, math.inf, -math.inf):
            with (
                self.subTest(raw_score=raw),
                self.assertRaisesRegex(ValueError, "모델이 유한하지 않은 유사도 점수를 반환했습니다"),
            ):
                score_from_cosine(raw)

    def test_raw_length_limit_is_enforced_before_normalization(self):
        with self.assertRaises(ValidationError):
            SimilarityRequest(sentence="가나다", guess="가나다" + "!" * 200)

    def test_input_length_boundaries_before_and_after_normalization(self):
        for field in ("sentence", "guess"):
            for value in ("", "가", "가" * 201):
                with self.subTest(field=field, length=len(value)), self.assertRaises(ValidationError):
                    SimilarityRequest(**{"sentence": "가나", "guess": "가나", field: value})
            for value in ("가나", "가" * 200):
                request = SimilarityRequest(**{"sentence": "가나", "guess": "가나", field: value})
                self.assertEqual(value, getattr(request, field))

    def test_response_contract_rejects_out_of_range_or_non_finite_score(self):
        for score in (-1, 101, math.inf, math.nan):
            with self.subTest(score=score), self.assertRaises(ValidationError):
                SimilarityResponse(score=score)

    def setUp(self):
        # 비용이 큰 모델 로딩만 생략하고 준비 상태 검사는 유지합니다.
        self.loader_patch = patch("app.main.load_model")
        self.loader_patch.start()

    def tearDown(self):
        self.loader_patch.stop()


if __name__ == "__main__":
    unittest.main()
