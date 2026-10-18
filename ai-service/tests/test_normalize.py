import unittest

from app.normalize import normalize_text


class TextNormalizationTest(unittest.TestCase):
    def test_unicode_white_space_matches_frontend_and_backend(self):
        whitespaces = [
            *map(chr, range(0x09, 0x0E)),
            " ",
            "\u0085",
            "\u00a0",
            "\u1680",
            *map(chr, range(0x2000, 0x200B)),
            "\u2028",
            "\u2029",
            "\u202f",
            "\u205f",
            "\u3000",
        ]
        for whitespace in whitespaces:
            with self.subTest(whitespace=repr(whitespace)):
                self.assertEqual("가나다 라마", normalize_text(f"{whitespace}가나다{whitespace}라마{whitespace}"))

    def test_non_property_controls_are_removed(self):
        for control in ("\ufeff", "\u001c", "\u001d", "\u001e", "\u001f"):
            self.assertEqual("가나다라마", normalize_text(f"가나다{control}라마"))

    def test_nfc_and_character_policy_are_preserved(self):
        self.assertEqual("가나다 ABC 123", normalize_text("가나다! ABC?! 123🙂"))

    def test_empty_short_and_boundary_inputs(self):
        for value, expected in (("!!!", ""), ("🙂🙂", ""), ("ㄱ디", "디"), ("가!나", "가나"), ("가" * 200, "가" * 200)):
            with self.subTest(value=value):
                self.assertEqual(expected, normalize_text(value))
                self.assertEqual(expected, normalize_text(expected))


if __name__ == "__main__":
    unittest.main()
