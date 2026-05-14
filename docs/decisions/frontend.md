# 프론트엔드 의사결정

> 프론트엔드는 AI를 정말 적극적으로 활용했고, 검토도 백엔드보다 덜 했지만, 그래도 주요한 아키텍처와 패턴에 대해서는 의사결정 기록을 남겨보려고 한다.

> 프론트 경험이 없는 백엔드 개발자보다는, 경험이 있는 백엔드 개발자가 프론트엔드의 주요 패턴과 구조를 어느정도 이해하고 있는 것이, 협업에 도움이 될 것 같아서 나름의 노력을 했다.

## Feature-Based 디렉토리 구조

페이지 단위가 아닌 기능(feature) 단위로 코드를 조직한다.

```
frontend/src/
├── app/               # 앱 셸 — 라우터, 레이아웃, 가드
├── features/          # 기능 모듈 — 각 도메인별 자체 완결
│   ├── game/          #   게임 (추측, 피드백, 힌트)
│   ├── ranking/       #   실시간 랭킹
│   ├── auth/          #   인증 (로그인, 회원가입, 마이페이지)
│   └── admin/         #   어드민 패널
├── pages/             # 라우트 진입점 페이지
└── shared/            # 공용 리소스 — UI, API, 스토어, 유틸
```

### Import 규칙

- **feature → shared**: 허용. 모든 기능 모듈은 `shared/`의 공용 컴포넌트, 스토어, 유틸을 사용할 수 있다.
- **feature → feature**: 금지. 기능 간 직접 import는 허용하지 않는다. 공유가 필요하면 `shared/`로 올린다.
- **shared → feature**: 금지. 공용 모듈이 특정 기능에 의존하면 순환이 생긴다.
- **배럴 export 없음**: `index.ts`로 re-export하지 않고, 파일을 직접 import한다.

> 예전에 트리쉐이킹을 믿고, 배럴 패턴을 남발했지만, 실제로는 트리쉐이킹이 제대로 동작하지 않아서, 사용하지 않는 코드까지 번들에 포함되는 문제가 있었다.

> 또한 특정 타입을 추적하다 보면, 배럴을 통해서 어디서부터 어디까지 의존성이 이어지는지 파악하기 어려운 경우가 많았다. 그래서 명시적으로 파일을 직접 import하는 방식으로 변경했다. 물론, 파일이 많아지는 단점은 있지만, 의존성 추적이 훨씬 명확해졌다.

### 왜 이 구조인가

- 기능별로 API, 훅, 컴포넌트가 한 디렉토리에 모여 있어 변경 범위가 명확
- 새 기능 추가 시 `features/` 아래에 디렉토리만 추가하면 됨
- `shared/`의 크기를 최소화하여 공용 코드의 변경 영향을 제한

## 상태 관리: Zustand + TanStack Query

### 역할 분리

| 상태 종류                            | 도구           | 이유                               |
| ------------------------------------ | -------------- | ---------------------------------- |
| 서버 데이터 (게임, 랭킹, 히스토리)   | TanStack Query | 캐싱, 백그라운드 리페칭, 뮤테이션  |
| 전역 클라이언트 상태 (인증, 테마)    | Zustand        | 여러 feature에서 공유, 동기적 접근 |
| 일시적 UI 상태 (토스트, SSE 연결 수) | Zustand        | 퍼시스턴스 불필요, 즉각 반영       |

### Zustand 스토어

| 스토어               | 역할                                                 |
| -------------------- | ---------------------------------------------------- |
| `useAuthStore`       | 로그인 상태, 유저 정보, `fetchUser()`, `clearUser()` |
| `useThemeStore`      | 라이트/다크/시스템 테마 (localStorage 연동)          |
| `useToastStore`      | 토스트 알림 (5초 자동 소멸)                          |
| `useConnectionStore` | SSE 연결 수 (HEARTBEAT 이벤트 기반)                  |

### TanStack Query 컨벤션

- **쿼리 키**: `["도메인", "리소스", params]` — 예: `["game", "today"]`, `["game", "status", sentenceId]`
- **staleTime**: 데이터 특성에 따라 차등 — 오늘 문제 `Infinity`(자정까지 불변), 힌트 `60_000`(1분), 게임 상태 `30_000`
- **글로벌 설정**: `retry: 1`, `refetchOnWindowFocus: false`
- **인증 전환 시**: `["ranking"]` invalidate. 로그아웃/탈퇴는 `queryClient.clear()` 전체 초기화

### SSE와 쿼리 캐시 연동

SSE `RANKING_UPDATE` 이벤트 수신 시 `["ranking"]` 쿼리를 invalidate한다. `DAY_CHANGE` 이벤트 시에는 `removeQueries(["game"])`으로 게임 데이터를 완전 초기화한다 (invalidate가 아닌 remove — stale 데이터가 잠깐이라도 보이면 안 되므로).

## Neo-Brutalism 디자인 시스템

### 왜 Neo-Brutalism인가

사실 미적 감각이 아예 없기도 하고, 각 AI들이 특유의 AI 디자인 스타일을 많이 사용하는게 보기 싫었다. 그래서 `어떻게 하면 AI스러운 디자인이 아닌, 인간이 만든 것 같은 디자인을 만들 수 있을까?`를 고민하다가, 웹 디자인 스타일 약 100여개를 html로 직접 생성을 요청하고, 그 중에서 가장 마음에 드는 스타일이 Neo-Brutalism이였다. 그래서 이 스타일을 참고해서 디자인 시스템을 구축했다.

또한 게임이라는 특성상, 둥근 모서리나 부드러운 그림자보다는, 각진 모서리와 선명한 그림자가 더 게임의 긴장감과 피드백을 잘 전달할 수 있다고 판단했다.

### 핵심 규칙

- **둥근 모서리 금지**: 모든 컴포넌트 `rounded-none`
- **두꺼운 테두리**: `border-4 border-black` (다크: `dark:border-white`)
- **하드 섀도우**: 블러 없는 오프셋 그림자 (`6px 6px 0px 0px rgba(0,0,0,1)`)
- **물리적 버튼 피드백**: hover → translate + 그림자 축소, active → 그림자 제거 + 완전 이동

### 다크 모드

`darkMode: 'class'` 전략. 기본은 시스템 설정 따름. 수동 토글 시 localStorage에 저장. 테두리/그림자 색상이 흑백 반전된다.

### 유사도 색상

HSL hue를 유사도에 선형 보간하여 연속 그라데이션을 만든다. Tailwind 클래스가 아닌 inline style로 적용.

> 유사도가 높을수록 초록색에 가까워지고, 낮을수록 빨간색에 가까워진다. 0%는 빨강(0°), 50%는 노랑(60°), 100%는 초록(120°)이 된다. 텍스트는 항상 흰색(`#ffffff`)으로 고정하여, 밝은 배경에서도 가독성을 유지한다.

> Tailwind의 색상 시스템은 고정된 색상 팔레트에 기반하기 때문에, 유사도에 따라 연속적으로 변화하는 색상을 표현하기에는 적합하지 않다. 따라서, 유사도에 따른 색상 변화를 정확하게 구현하기 위해서는 inline style로 HSL hue를 계산하여 적용하는 것이 가장 효과적이라고 판단했다.

```
hue = (similarity / 100) × 120
0% → 빨강(0°), 50% → 노랑(60°), 100% → 초록(120°)
```

라이트/다크 모드 공통 사용. 텍스트는 항상 흰색 (`#ffffff`).

## 주요 패턴

### prop → 상태 동기화

`useEffect` 내 `setState` 금지. render 중 비교 패턴 또는 `key` prop으로 리마운트한다.

```typescript
// BAD
useEffect(() => { setSomething(prop); }, [prop]);

// GOOD — key로 리마운트
<Component key={item.id} data={item} />
```

### 중복 추측 방지

서버 호출 전에 `displayGuesses`에서 동일 텍스트를 검사한다. 이미 추측한 문장이면 서버 요청 없이 기존 유사도를 즉시 표시한다.

> 사용자가 실수로 같은 추측을 여러 번 제출하는 것을 방지하기 위해, `handleSubmit` 함수에서 `displayGuesses` 상태를 먼저 검사하여, 이미 추측한 문장인지 확인한다. 만약 이미 추측한 문장이라면, 서버에 중복 요청을 보내지 않고, 기존에 계산된 유사도를 즉시 표시한다. 이렇게 하면 불필요한 서버 부하를 줄이고, 사용자 경험도 개선할 수 있다.

### 텍스트 정규화

`normalizeGuessText()` (`shared/utils/normalize.ts`)로 서버 호출 전 FE에서 선검증한다. 백엔드 `TextNormalizer`와 동일한 정규화 로직 (`[^가-힣a-zA-Z0-9\s]` 제거)을 적용하여 불필요한 서버 왕복을 줄인다.

> 사용자가 추측을 제출하기 전에, `normalizeGuessText()` 함수를 사용하여 입력된 텍스트를 정규화한다. 이 함수는 백엔드의 `TextNormalizer`와 동일한 로직을 적용하여, 특수문자를 제거하고 공백을 정규화한다. 이렇게 하면, 서버에 불필요한 요청을 보내지 않고도, 사용자가 이미 유효하지 않은 추측을 했는지 즉시 피드백을 줄 수 있다.

### 사운드

볼륨 상수와 유틸은 `shared/config/sound.ts`에서 관리한다. `DEFAULT_VOLUME = 0.7`, localStorage `sound_volume` 키로 퍼시스트. 설정 모달에서 슬라이더로 조절.

> TMI) 게임을 플레이할 때, 갑자기 큰 소리가 나면 놀랄 수 있기 때문에, 사운드 볼륨을 조절할 수 있는 기능을 제공하기로 결정했다. 또한, 효과음을 찾는데 장장 1시간이 넘게 걸렸고, 갑자기 놀라거나, 너무 소리가 크거나, 고 음역대가 귀를 거슬리게 할 수 있기 때문에, 직접 DAW(Fl Studio)를 사용해서 시작과 끝에 페이드인/아웃을 넣고, EQ를 걸어서 High를 깎아서 귀에 덜 거슬리도록 편집했다.

### GuessHistory 레이아웃

페이지당 5개의 슬롯을 고정하고, 데이터가 부족한 슬롯은 invisible placeholder로 채운다.

placeholder는 실제 아이템과 동일한 DOM 구조(`border-4`, `p-2 md:p-3`, `SimilarityBadge` 크기)를 유지하되, `invisible aria-hidden`으로 처리하여 시각적으로는 보이지 않지만 공간은 차지하게 했다.

> 추측 기록이 추가될 때마다 높이가 변하면 아래 요소들이 밀려나는 레이아웃 시프트가 발생한다. 처음에는 min-height를 고정하는 방식을 시도했지만, 아이템 높이가 반응형으로 달라지면서 정확한 값을 맞추기 어려웠다. 실제 DOM 구조와 동일한 placeholder를 invisible로 렌더링하면, 아이템 크기가 바뀌어도 자동으로 맞춰지기 때문에 이 방식을 채택했다.

### 상수 중앙화

TanStack Query의 `staleTime`과 환경 변수를 중앙에서 관리한다.

- **staleTime**: `shared/config/query.ts`에서 `STALE_TIME.NONE(0)`, `SHORT(30_000)`, `LONG(60_000)`, `IMMUTABLE(Infinity)` 4단계로 정의한다. 각 `useQuery` 호출에서 이 상수를 참조한다.
- **refetchInterval**: 같은 파일에서 `REFETCH_INTERVAL.FAST(15_000)`, `NORMAL(30_000)`을 정의한다.
- **API_BASE_URL**: `shared/config/env.ts`에서 `VITE_API_BASE_URL` 환경 변수를 모듈 로드 시점에 검증하고, 없으면 즉시 에러를 던진다.

> staleTime을 각 useQuery 호출마다 매직 넘버로 넣으면, 전체 캐싱 정책을 변경할 때 모든 파일을 찾아서 바꿔야 한다. 상수로 중앙화하면 한 곳만 수정하면 되고, "이 데이터는 어떤 수준의 freshness가 필요한가"를 네이밍으로 명시할 수 있다.

> 환경 변수 검증도 비슷한 맥락이다. `import.meta.env.VITE_API_BASE_URL`을 여기저기서 직접 읽으면, 값이 빠졌을 때 런타임 도중에 `undefined/api/...` 같은 URL로 요청이 나가서 디버깅이 어렵다. 모듈 초기화 시점에 fail-fast하면 원인을 바로 찾을 수 있다.

### 에러 바운더리

React의 Error Boundary는 class 컴포넌트로만 구현할 수 있다. 이 제약 때문에 `ErrorBoundary` class 컴포넌트에서 에러를 감지하고, `ErrorFallback` 함수 컴포넌트에서 UI를 렌더링하는 구조로 분리했다.

- **ErrorFallback**: 개발 환경(`import.meta.env.DEV`)에서만 스택 트레이스를 노출하고, "다시 시도"와 "홈으로" 버튼을 제공한다.
- **라우터 통합**: `router.tsx`에서 루트 레벨 `errorElement`로 `RouteErrorFallback`을 설정하여, 라우트 내 에러도 동일한 UI로 처리한다.

> class 컴포넌트 안에서 복잡한 UI를 작성하면 훅을 쓸 수 없어서 유지보수가 어려워진다. 에러 감지(class)와 에러 표시 UI(함수)를 분리하면 UI 쪽은 자유롭게 훅을 쓸 수 있다.

## 모바일 반응형

`md:` (768px) 단일 breakpoint로 모바일과 데스크톱을 구분한다. mobile-first 전략이므로, 접두사 없는 클래스가 모바일이고 `md:` 접두사가 데스크톱 오버라이드다.

| 페이지 | 모바일 | 데스크톱 |
|--------|--------|----------|
| 게임 (/) | 게임 칼럼만 표시. 랭킹/힌트는 MobileSideSheet 드로어로 접근 | 사이드 패널(좌 w-72) + 게임(우) 2단 |
| 로그인 (/login) | 소셜 → 가로 구분선("또는") → 가까워 1단 스택 | 소셜 + 세로 구분선 + 가까워 2단 |
| 푸터 | 세로 스택, 링크 flex-wrap | 좌우 배치 justify-between |

> 이 게임의 UI가 비교적 단순해서 모바일/데스크톱 2단계면 충분하다고 판단했다. 태블릿을 별도로 다루면 CSS만 복잡해지고, 실사용자 대부분이 폰이나 데스크톱일 것으로 예상했다. 어드민(`/admin`)은 데스크톱 전용이라 반응형 대상에서 제외했다.

## 오버레이 시스템

모달, 드로어 등 오버레이 컴포넌트들은 공통적으로 Escape 닫기, click-outside 닫기, body scroll lock이 필요하다. 이 세 가지 동작을 각각 커스텀 훅으로 분리하고, 오버레이 컴포넌트에서 조합하는 구조로 설계했다.

### Dialog

모든 모달의 기반이 되는 공용 컴포넌트다. `createPortal(document.body)`로 렌더링하여 부모 CSS의 간섭을 받지 않는다.

- `useEscapeStack`, `useScrollLock`을 내부에서 호출한다.
- Click-outside는 백드롭의 `onMouseDown === e.currentTarget` 검사로 구현한다.
- `useId()`로 `aria-labelledby`를 동적 연결한다.
- `disableClose` prop을 켜면 로딩 중 Escape, click-outside, 닫기 버튼을 모두 차단한다.

> 처음에는 `react-modal` 같은 라이브러리를 고려했지만, 네오 브루탈리즘 스타일링에 맞추려면 결국 대부분의 CSS를 오버라이드해야 해서, 직접 만드는 편이 간결했다. Portal 없이 relative 포지셔닝만 쓰면 `overflow: hidden`인 부모 안에서 잘리는 문제가 있어서, Portal을 채택했다.

### ConfirmDialog

`Dialog`를 래핑하여 "정말 삭제하시겠습니까?" 류의 확인/취소 패턴을 표준화한 컴포넌트다.

- 상단 메시지(`whitespace-pre-line`으로 개행 지원) + 하단 취소/확인 버튼으로 구성한다.
- `isLoading` 시 양쪽 버튼 disabled + `disableClose` 전파로 중복 실행을 방지한다.

### MobileSideSheet

모바일에서 랭킹/힌트 패널을 보여주는 오른쪽 드로어다. `md:hidden`으로 데스크톱에서는 숨긴다.

- 화면 오른쪽 고정 트리거 버튼(랭킹/힌트) → 탭하면 `animate-slide-in-right`로 열린다.
- 탭 전환 헤더로 랭킹/힌트를 같은 드로어 안에서 전환할 수 있다.
- `useClickOutside`, `useEscapeStack`, `useScrollLock` 세 훅을 모두 사용한다.
- 닫을 때는 `isClosing` 상태를 거쳐 슬라이드 아웃 애니메이션이 끝난 후 상태를 리셋한다.

> 처음에는 랭킹과 힌트를 각각 별도 드로어로 열까 고민했는데, 하나의 드로어에 탭 전환을 넣는 것이 전환 비용이 낮고 사용자 경험이 더 자연스러웠다.

### 공용 훅

| 훅 | 역할 | 구현 방식 |
|----|------|-----------|
| `useEscapeStack` | Escape 키 처리 | 모듈 레벨 스택. 최상위 핸들러만 호출하여 Dialog 위에 Dialog가 떴을 때 아래 것이 닫히는 문제를 방지 |
| `useScrollLock` | body scroll lock | 모듈 레벨 카운터. 다중 오버레이 동시 활성화 시 마지막이 닫힐 때만 unlock. scrollbar width `paddingRight` 보상 |
| `useClickOutside` | click-outside 닫기 | `mousedown` 이벤트 기반. `excludeRefs`로 특정 요소 제외 가능 |

> `useEscapeStack`이 없었을 때 Dialog 중첩 시 Escape를 누르면 두 Dialog가 동시에 닫히는 문제가 있었다. 전역 스택을 두고 최상위 핸들러만 호출하면 이 문제가 해결된다. `useScrollLock`도 비슷하게, 카운터 없이 단순히 `overflow: hidden`을 토글하면 Dialog A가 닫힐 때 Dialog B가 아직 열려 있는데도 스크롤이 풀려버린다.

## 공용 UI 컴포넌트

`shared/ui/`에 위치하는, 특정 도메인에 종속되지 않는 범용 컴포넌트들이다.

| 컴포넌트 | 역할 |
|----------|------|
| `DefaultAvatar` | 프로필 사진이 없을 때 표시하는 기본 아바타. `size` prop으로 sm/md/lg 3단계. 인라인 SVG 아이콘 사용 |
| `SkeletonRow` | 데이터 로딩 중 표시하는 스켈레톤. `animate-pulse` + `height`, `className` props |
| `AnnouncementBanner` | 공지사항 배너. 유형별(안내/점검/경고) 색상 분기. 닫기 시 localStorage에 기록하여 중복 표시 방지 |

> 공용 컴포넌트의 기준은 "두 개 이상의 feature에서 사용하는가"다. 한 feature에서만 쓰이는 컴포넌트는 해당 feature 디렉토리에 둔다. 다만 Dialog처럼 기능이 복잡하고 일관된 동작이 중요한 것은 한 곳에서만 쓰더라도 `shared/`에 두어 중앙 관리한다.

---

_마지막 업데이트: 2026-05-14_
