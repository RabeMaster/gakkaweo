import re
import unicodedata

# 프론트엔드·백엔드와 동일한 Unicode White_Space 범위를 사용합니다.
# Python의 \s는 U+001C~U+001F도 허용하므로 범위를 직접 지정합니다.
_WHITESPACE = r"\u0009-\u000d\u0020\u0085\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000"
_CLEAN = re.compile(rf"[^가-힣a-zA-Z0-9{_WHITESPACE}]")
_COLLAPSE = re.compile(rf"[{_WHITESPACE}]+")


def normalize_text(text: str) -> str:
    text = unicodedata.normalize("NFC", text)
    text = _CLEAN.sub("", text)
    text = _COLLAPSE.sub(" ", text)
    return text.strip()
