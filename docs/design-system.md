# UI/UX Design System — Neo-Brutalism Style Guide

본 프로젝트의 프론트엔드는 아래의 네오 브루탈리즘(Neo-Brutalism) 디자인 원칙을 엄격하게 준수한다.

## 기술 스택

- React + React Router + TypeScript
- Zustand (상태 관리)
- Tailwind CSS (스타일링)
- Vite (빌드)
- Pretendard (폰트)
- shadcn/ui 사용하지 않음 — Tailwind 직접 스타일링

## 1. Core Principles (핵심 원칙)

- **둥근 모서리 금지:** 모든 컴포넌트(버튼, 카드, 입력창)의 모서리는 직각(`rounded-none`)을 기본으로 한다.
- **두꺼운 테두리:** 모든 주요 요소는 굵은 테두리(`border-4 border-black` 또는 `border-2 border-black`)를 가져야 한다.
- **오프셋 그림자 (Hard Shadow):** 블러(Blur)가 들어간 부드러운 그림자는 절대 사용하지 않는다. 그림자는 오직 X, Y축으로 딱 떨어지는 솔리드 컬러이다.
- **폰트:** 항상 'Pretendard'를 사용하며, 제목과 수치 등은 가장 두꺼운 굵기(`font-black` 또는 `font-extrabold`)를 적극 사용한다.

## 2. Tailwind Config (필수 확장 설정)

```javascript
theme: {
  extend: {
    fontFamily: {
      sans: ['Pretendard', 'system-ui', 'sans-serif'],
    },
    boxShadow: {
      'brutal': '6px 6px 0px 0px rgba(0,0,0,1)',
      'brutal-sm': '3px 3px 0px 0px rgba(0,0,0,1)',
      'brutal-hover': '2px 2px 0px 0px rgba(0,0,0,1)',
      'brutal-sm-hover': '1px 1px 0px 0px rgba(0,0,0,1)',
    },
  },
}
```

### 다크모드 그림자

다크모드에서는 그림자 색상을 흰색으로 전환한다. CSS 변수 또는 별도 유틸리티로 처리:

```javascript
boxShadow: {
  'brutal-dark': '6px 6px 0px 0px rgba(255,255,255,1)',
  'brutal-dark-sm': '3px 3px 0px 0px rgba(255,255,255,1)',
  'brutal-dark-hover': '2px 2px 0px 0px rgba(255,255,255,1)',
  'brutal-dark-sm-hover': '1px 1px 0px 0px rgba(255,255,255,1)',
},
```

## 3. Interactive States (상호작용)

버튼 등 클릭 가능한 요소는 반드시 아래의 Hover/Active 상태를 구현하여 '물리적인 버튼이 눌리는' 타격감을 줘야 한다.
**translate 거리 = base shadow - hover shadow** 원칙을 지켜야 그림자 밖으로 오버슈트하지 않는다.

### md/lg 버튼 (shadow-brutal, 6px)

| 상태           | 클래스                                                                      |
| -------------- | --------------------------------------------------------------------------- |
| 기본           | `shadow-brutal`                                                             |
| Hover          | `hover:shadow-brutal-hover hover:translate-x-[4px] hover:translate-y-[4px]` |
| Active (Click) | `active:shadow-none active:translate-x-[6px] active:translate-y-[6px]`      |
| Disabled       | `opacity-50 cursor-not-allowed pointer-events-none translate-x-0 translate-y-0` (그림자 유지) |
| Loading        | `opacity-70 cursor-wait shadow-brutal animate-pulse`                        |

### sm 버튼 (shadow-brutal-sm, 3px)

| 상태           | 클래스                                                                         |
| -------------- | ------------------------------------------------------------------------------ |
| 기본           | `shadow-brutal-sm`                                                             |
| Hover          | `hover:shadow-brutal-sm-hover hover:translate-x-[2px] hover:translate-y-[2px]` |
| Active (Click) | `active:shadow-none active:translate-x-[3px] active:translate-y-[3px]`         |
| Disabled       | `opacity-50 cursor-not-allowed pointer-events-none translate-x-0 translate-y-0` (그림자 유지) |
| Loading        | `opacity-70 cursor-wait shadow-brutal-sm animate-pulse`                        |

모든 상태 전환에는 `transition-all duration-100`을 적용하여 부드러운 전환을 준다.

## 4. Color Palette (색상 규정)

### 유사도 피드백 색상 (HSL 그라데이션)

퍼센트만 표시하며, 0%~100% 구간에서 HSL hue를 선형 보간하여 연속적으로 색상이 변한다.
라벨 텍스트 없이 색상만으로 피드백을 전달한다. `inline style`로 적용 (Tailwind 클래스 아님).

```
hue = (similarity / 100) × 120
배경: hsl(hue, 80%, 45%)
텍스트: 항상 #ffffff
```

**예외 — 프로그래스 바**: 텍스트가 채워진 영역(색상)과 빈 영역(gray)에 걸치므로 `#ffffff` 고정 시 라이트 모드 빈 영역에서 시인성 저하. `text-black dark:text-white` Tailwind 클래스 사용.

| 유사도 | hue  | 시각적 색상 |
| ------ | ---- | ----------- |
| 0%     | 0°   | 빨강        |
| 25%    | 30°  | 주황        |
| 50%    | 60°  | 노랑        |
| 75%    | 90°  | 연두        |
| 100%   | 120° | 초록        |

유사도 색상은 라이트/다크모드 공통으로 사용한다.

### 기본 색상

| 용도           | 라이트모드                    | 다크모드            |
| -------------- | ----------------------------- | ------------------- |
| 배경           | `bg-white`                    | `dark:bg-gray-950`  |
| 텍스트         | `text-black`                  | `dark:text-white`   |
| 테두리         | `border-black`                | `dark:border-white` |
| 카드/섹션 배경 | `bg-indigo-50` 등 연한 파스텔 | `dark:bg-gray-900`  |
| 주요 액션 버튼 | 원색 배경 + `text-black`      | 동일                |

## 5. Dark Mode (다크모드)

- Tailwind `darkMode: 'class'` 전략 사용
- 기본값: 브라우저 시스템 설정 따름 (`prefers-color-scheme`)
- 수동 토글: 헤더에 라이트/다크 스위치 배치, `localStorage`에 저장
- 테두리/그림자 색상 반전: `border-black` ↔ `dark:border-white`, 그림자도 동일

## 6. Typography (타이포그래피)

| 용도        | 클래스                                     |
| ----------- | ------------------------------------------ |
| 페이지 제목 | `text-4xl font-black`                      |
| 섹션 제목   | `text-2xl font-extrabold`                  |
| 유사도 수치 | `text-3xl font-black tabular-nums`         |
| 본문        | `text-base font-medium`                    |
| 보조 텍스트 | `text-sm text-gray-600 dark:text-gray-400` |

수치 표시에는 `tabular-nums`로 숫자 정렬을 맞춘다.

## 7. Layout (레이아웃)

- **PC 전용**: 최소 너비 기준 설계, 모바일 미지원
- **반응형**: 화면 크기에 따라 유연하게 조절 (넓은 모니터에서도 자연스럽게)
- **최대 너비**: 게임 영역은 `max-w-2xl` 또는 `max-w-3xl` 중앙 정렬
- **간격**: Tailwind 기본 spacing 사용 (`p-4`, `gap-4`, `space-y-4` 등)

## 8. 페이지 구성

| 페이지     | 경로       | 설명                                          |
| ---------- | ---------- | --------------------------------------------- |
| 게임       | `/`        | 추측 입력, 유사도 결과, 히스토리, 힌트 마스크 |
| 랭킹       | `/ranking` | 실시간 순위 (SSE)                             |
| 로그인     | `/login`   | 소셜 로그인 + 로컬 로그인 2컬럼 + 회원가입 다이얼로그 |
| 마이페이지 | `/mypage`  | 프로필 이미지 업로드/삭제, 닉네임 확인 (✏️ 편집), 회원 탈퇴 |
| 어드민     | `/admin`   | 사이드바+탭 레이아웃 (대시보드/문장/사용자/시스템). ROLE_ADMIN만 접근 |
| 개인정보 처리방침 | `/privacy` | 개인정보 수집/이용/보호 방침 |
| 서비스 이용약관 | `/terms` | 서비스 이용 조건 및 규정 |

## 9. 로그인 프로바이더 색상

각 프로바이더의 공식 브랜드 가이드라인을 준수한다. 로컬 계정은 중립 색상 사용.

| 프로바이더 | 라이트 배경 | 다크 배경      | 텍스트 (라이트)  | 텍스트 (다크)    |
| ---------- | ----------- | -------------- | ---------------- | ---------------- |
| 카카오     | `#FEE500`   | `#FEE500`      | `#191919`        | `#191919`        |
| Google     | `#FFFFFF`   | `#131314`      | `#1F1F1F`        | `#E3E3E3`        |
| 네이버     | `#03C75A`   | `#03C75A`      | `#FFFFFF`        | `#FFFFFF`        |
| 가까워     | `gray-200`  | `gray-700`     | `black`          | `white`          |

- 카카오/네이버는 라이트/다크 공통 배경 (브랜드 색상이 양쪽 모드에서 충분한 대비 제공)
- Google만 다크모드 전용 배경 (`#131314`) 사용 — 공식 Sign-In 다크 테마 기준
- 가까워(로컬 계정)는 중립 gray + 가까워 로고 SVG 아이콘 (ㄱ/ㄲ 눈 + 원형 입)
- hover 시 배경색 변화 없음 (neo-brutalism 그림자 + translate 상호작용으로 통일)

## 10. 컴포넌트 규칙

### 버튼

```
기본: border-4 border-black bg-{color} shadow-brutal rounded-none font-bold
      px-6 py-3 transition-all duration-100
호버: hover:shadow-brutal-hover hover:translate-x-[4px] hover:translate-y-[4px]
클릭: active:shadow-none active:translate-x-[6px] active:translate-y-[6px]
비활성: disabled:opacity-50 disabled:cursor-not-allowed disabled:pointer-events-none
        disabled:shadow-none disabled:translate-x-0 disabled:translate-y-0
```

### 입력창

```
border-4 border-black rounded-none shadow-brutal bg-white dark:bg-gray-900
px-4 py-3 font-medium text-lg
focus:outline-none focus:ring-0 focus:border-indigo-500 dark:focus:border-indigo-400
placeholder:text-gray-400
```

### 카드

```
border-4 border-black dark:border-white rounded-none shadow-brutal
dark:shadow-brutal-dark bg-white dark:bg-gray-900 p-6
```

### 푸터

```
border-t-4 border-black dark:border-white bg-white dark:bg-gray-950
max-w-6xl mx-auto px-6 py-6 flex items-center justify-between
```

- 좌측: `© 2026 가까워` (`text-sm font-bold text-gray-600 dark:text-gray-400`)
- 우측: 이용약관, 개인정보처리방침 (내부 Link), GitHub, Blog (외부 `<a>` target="_blank")
- 링크: `text-sm font-bold text-gray-600 dark:text-gray-400 hover:text-black dark:hover:text-white transition-colors`
- Layout: `flex flex-col min-h-screen` + `<main>` `flex-1` (스티키 푸터)

### 랭킹 패널

- **1·2·3등 액센트**: `box-shadow: inset 4px 0 0 0` CSS 변수 (`--rank-1-accent`, `--rank-2-accent`, `--rank-3-accent`). 다크모드 변수 자동 전환
- **1등 shine**: `rank-shine` 애니메이션 (행 전체를 훑는 반짝임, 4s linear)
- **1등 배지**: 트로피 SVG 아이콘 (`text-black`)
- **배지 색상**: 1등=`yellow-400/300`, 2등=`slate-300/400`, 3등=`amber-600/500 text-white`. 4+등=`gray-200/700`
- **라이브 인디케이터**: `live-pulse` 애니메이션 (초록 펄스 도트) + `sseConnectionCount` (Zustand store, HEARTBEAT 기반)
- **재생/정지 버튼**: `border-2 shadow-brutal-sm-hover w-7 h-7`. 호버 시 `-top-7` 위치 툴팁 (재생/정지)

### 플레이 방법 모달

- 최초 방문 시 자동 표시 (localStorage `help_modal_shown`). 닫으면 재표시 안 함
- "플레이 방법" 버튼으로 언제든 재열람 (`border-2 bg-yellow-300 text-xs font-black`)
- 섹션 간 `border-t-2 border-gray-200 dark:border-gray-800` 구분선
- AI 유사도 설명: 의미 기반 측정, 글자 매칭 아님 (예시 포함)
- 클리어 후 계속 플레이 가능 안내 (left-accent 콜아웃, `<ul><li>` 구조)
- 랭킹 선정 방식 안내 섹션: 유사도 > 시도 횟수 > 100% 선착순
- 플레이 방법 번호 리스트: `<ol>` 내 노란 배지에 1~5 숫자

### 설정 모달

- **테마**: 라이트/다크/시스템 3버튼 토글. 활성 = `bg-black text-white`
- **사운드 볼륨**: `input[type=range]` 슬라이더 (`accent-yellow-400`). localStorage `sound_volume` (기본 0.7, 범위 0~1). 상수: `shared/config/sound.ts`. 0이면 🔇, 그 외 🔊
- **사운드 테스트**: 런치패드 버튼 (`border-2 shadow-brutal-sm`). 색상별 구분 (클리어=green). 기존 재생 중 사운드 정지 후 새로 재생. 음소거 시 disabled

### 프로필 이미지 (마이페이지)

- **프로필 이미지 클릭** → 팝오버(변경/삭제) 표시
- **변경**: hidden `<input type="file" accept="image/*">` → 크롭 모달 → 256×256 WebP 리사이징 → 서버 업로드
- **삭제**: ConfirmDialog 확인 후 서버 삭제 → 기본 아바타 복원
- **크롭 모달**: `react-easy-crop` — `aspect={1}`, `cropShape="rect"` (직각, 네오브루탈리즘). 줌 슬라이더 1.0~10.0
- **팝오버**: `border-4 shadow-brutal-sm`, click-outside/Escape 닫기
- **hover 오버레이**: `bg-black/40` + 편집 아이콘 (프로필 이미지 위)

### 추측 피드백

- **위치**: 게임 페이지 중앙 칼럼, GuessInput 위
- **구조**: 고정 높이 2행 (`h-6` × 2, `space-y-1`). 레이아웃 시프트 방지
- **Row 1**: 최고 유사도 — `displayGuesses`에서 `useMemo` 파생. HSL 색상 퍼센트
- **Row 2**: 에러 시 에러 메시지 (`text-red-500`) / 정상 시 마지막 추측 (guessText + HSL 퍼센트)
- **빈 상태**: 라벨 + `—` (대시)로 공간 유지
- **데이터 원천**: `displayGuesses` 파생 (추가 API 없음). 새로고침 시 history API → 자동 복원
- **FE 정규화**: `normalizeGuessText()` — BE `TextNormalizer`와 동일 로직 (`[^가-힣a-zA-Z0-9\s]` 제거). 서버 호출 전 선검증

### 힌트 패널

- **위치**: 좌측 칼럼, RankingPanel 아래. 부모 `div.w-72.shrink-0.space-y-6`이 RankingPanel + HintPanel 래핑
- **조건**: 항상 표시. 비로그인 → 로그인 유도 안내. 로그인 + bestSimilarity < 60 → 잠금 안내
- **카드**: `Card` + `!p-4 space-y-3` (RankingPanel과 동일 배경)
- **제목**: "다른 플레이어의 추측" `text-lg font-extrabold`
- **상태 분기**: (1) 비로그인 → 로그인 유도 (2) bestSimilarity < 60 → 잠금 안내 (3) 빈 목록 (4) 힌트 목록 (스포일러 처리)
- **스포일러**: 힌트 목록 기본 `blur-sm` 처리. 클릭 시 전체 해제. 새로고침 시 자동 리셋 (React state, localStorage 미사용)
- **행 구분선**: `border-b-2 border-black/20 dark:border-white/20` (마지막 항목 제외)
- **갱신**: 매 추측 시 `["game", "hints", sentenceId]` invalidate + `staleTime: 60_000`
- **힌트 범위**: 요청자의 bestSimilarity 미만 추측만 표시 (서버측 필터링)

### 어드민 패널 (`/admin`)

- **레이아웃**: 사이드바(w-56, 고정) + 콘텐츠 영역(flex-1). `flex gap-6 items-start`
- **사이드바**: `border-4 shadow-brutal`. 탭: 대시보드/문장/사용자/시스템. 활성 탭 `bg-yellow-300 text-black`
- **테이블**: `border-4 shadow-brutal` 래퍼. 헤더 `border-b-4 bg-gray-100 dark:bg-gray-800`. 행 `border-b-2 border-black/20` + hover `bg-yellow-50`
- **위젯 카드**: `border-4 shadow-brutal-sm`. 라벨 `text-xs uppercase tracking-wide`. 수치 `text-3xl font-black tabular-nums`
- **상태 배지**: `px-2 py-0.5 text-xs font-black border-2`. ACTIVE=green, DISABLED=gray, ADMIN=red, USER=blue, 차단=gray-800
- **다이얼로그**: `border-4 shadow-brutal max-w-lg`. 헤더 `border-b-4 px-6 py-5`. 푸터 `border-t-4`. `role="dialog" aria-modal="true"` + Escape 닫기 필수
- **텍스트 링크 액션**: 테이블 행 내 액션은 `text-indigo-600 dark:text-indigo-400 font-black text-xs hover:underline`
- **Pagination**: `Button sm secondary` 이전/다음 + `tabular-nums` 페이지 표시
- **공지 유형 라벨**: 안내(blue-300), 점검(orange-300), 경고(red-400). select에서도 한글 라벨 사용
- **공지 배너**: `shared/ui/AnnouncementBanner.tsx`. 유형별 색상 배경 + `shadow-brutal-sm`. 닫기 시 localStorage `id_startsAt` 복합 키 저장
