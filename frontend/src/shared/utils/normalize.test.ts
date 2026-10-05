import { expect, it } from "vitest";
import { normalizeGuessText } from "./normalize";

const whitespace = [
  ...Array.from({ length: 5 }, (_, i) => String.fromCodePoint(0x09 + i)),
  " ",
  "\u0085",
  "\u00a0",
  "\u1680",
  ...Array.from({ length: 11 }, (_, i) => String.fromCodePoint(0x2000 + i)),
  "\u2028",
  "\u2029",
  "\u202f",
  "\u205f",
  "\u3000",
];

it.each(whitespace)("Unicode White_Space %j를 BE·AI와 동일하게 처리합니다", (space) => {
  expect(normalizeGuessText(`${space}안녕${space}${space}세계${space}`)).toBe("안녕 세계");
});

it.each(["\ufeff", "\u001c", "\u001d", "\u001e", "\u001f"])("공백 범위 밖 제어 문자 %j를 제거합니다", (control) => {
  expect(normalizeGuessText(`안녕${control}세계!`)).toBe("안녕세계");
});

it.each([
  ["가나다! ABC?! 123🙂", "가나다 ABC 123"],
  ["!!!", ""],
  ["🙂🙂", ""],
  ["ㄱ디", "디"],
  ["가!나", "가나"],
  ["가".repeat(200), "가".repeat(200)],
])("입력 %j를 정규화합니다", (input, expected) => {
  expect(normalizeGuessText(input)).toBe(expected);
  expect(normalizeGuessText(expected)).toBe(expected);
});
