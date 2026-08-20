# 프론트엔드 반응형 정밀 진단 — `web/`

> 작성 2026-08-20 · 대상 `web/` (Next.js 16.3 App Router / React 19 / CSS Modules)
> **이 문서는 진단서다. 이 문서를 쓰는 과정에서 코드는 한 줄도 바꾸지 않았다.**

---

## 0. 읽는 법

### 0.1 표기 규칙

각 항목은 `RSP-xx` ID를 갖는다. 기존 프로젝트 관행(`KF-xx`, `FE-RSP-xx`, `I-xx`, `F-xx`)과 번호가 겹치지 않도록 새 접두어를 썼다. 구현 시 코드 주석과 e2e 스펙에 `RSP-xx(docs/improvement_claude.md)` 형태로 근거를 남기면 기존 주석 문화와 그대로 이어진다.

항목마다 아래 순서를 지킨다.

| 절 | 내용 |
|---|---|
| **근거** | 파일:라인 + 토큰에서 유도한 실측 수치 |
| **증상** | 사용자가 실제로 겪는 것 |
| **왜 안 잡혔나** | 기존 e2e/린트/예산 중 무엇이 이걸 통과시키는가 |
| **적용** | 그대로 붙여넣을 수 있는 코드 |
| **검증** | 고쳤는지 확인하는 구체적 방법 |

라인 번호는 2026-08-20 시점 기준이다.

### 0.2 참조된 선행 문서 부재 안내

코드 주석 곳곳이 `docs/improvement.md`, `docs/improvement_codex_fe.md`, `docs/improvement_claude_fe.md`를 참조하지만 **이 진단 시점에 `docs/` 디렉터리는 비어 있었다.** `.gitignore:91`이 `docs/`를 통째로 무시하므로 git 이력에도 남아 있지 않다 — 이 파일들은 로컬에만 존재했고 지금은 유실된 상태다.

따라서 `KF-02`, `KF-06`, `FE-RSP-05`, `I-19`, `F-18` 같은 ID의 원본 서술은 **코드 주석에 인용된 요약이 유일하게 남은 사료다.** 이 문서는 그 주석들을 1차 자료로 삼아 재구성했다. 이 문서 자체도 `docs/`에 있으므로 같은 이유로 추적되지 않는다 — 팀에서 공유하거나 이력으로 남겨야 한다면 `.gitignore`의 `docs/` 항목을 먼저 재검토해야 한다.

### 0.3 이 진단의 전제 — 손대지 말 것

`web/`은 반응형에 관한 한 이미 성숙한 코드베이스다. 아래는 **이미 올바르므로 건드리지 말 것**이다. 개선 작업 중 가장 흔한 사고가 이것들을 모르고 깨뜨리는 것이다.

| 계약 | 위치 | 깨뜨리면 |
|---|---|---|
| `--tabbar-reserve` ↔ `.primaryNav`/`.tabBar` 1152px 동시 전환 (KF-02·KF-06) | `tokens.css:159`, `shell.module.css:252` | 탭바는 사라졌는데 하단 여백이 남거나, 광고가 없는 탭바 위에 뜬다 |
| 하단 전용 safe-area 정책 — `viewportFit:"cover"`를 켜지 않는다 (FE-RSP-05) | `app/layout.tsx:56-72` | cover만 켜면 배경은 노치까지 늘어나는데 좌우/상단 inset 방어가 없는 상태가 된다 |
| `globals.css`가 최상단 import여야 하는 이유 | `app/layout.tsx:1-8` | `@layer` 등록 순서가 뒤집혀 컴포넌트 스타일이 전역 `a{color}`에 진다 (axe color-contrast 회귀 실측) |
| `shared-components.css`가 `utilities`가 아니라 `components` 레이어인 이유 | `shared/styles/shared-components.css:1-13` | 청크 분리된 `@layer components` 블록 간 레이어 순서가 기대대로 안 풀린 실측 사례 |
| CSP 때문에 인라인 `style` 대신 SVG 속성·`nth-child`로 값을 넣는 규칙 | `frequency-bar.module.css`, `winning-celebration.module.css` | `style-src`에서 `'unsafe-inline'`을 뺄 수 없게 된다 |
| `--font-size-input` (iOS 16px 하한) | `tokens.css:100-101` | iOS Safari가 입력 포커스 시 페이지를 자동 확대한다 |
| `.tableWrap`의 `overflow-x:auto` + `.card`의 `min-width:0` (KF-03) | `surface.module.css:14,31` | Firefox grid automatic minimum size로 `/stats@320`이 다시 가로로 넘친다 |

### 0.4 이 진단이 찾은 구조적 공백

성숙한 만큼, 남은 문제는 개별 버그가 아니라 **접근 방식의 한계**다. 셋으로 요약된다.

1. **모든 반응형 판정이 "뷰포트" 기준이다.** `@container` 사용 0건. 컨테이너 안에 컨테이너가 들어가는 2열 레이아웃에서 안쪽이 바깥쪽 폭을 모른다.
2. **"사용자 환경 적응"이 색상 테마 하나뿐이다.** `forced-colors` / `prefers-contrast` / `prefers-reduced-transparency` 대응 0건.
3. **반응형 회귀 트랙이 터치 기기를 렌더한 적이 없다.** `playwright.responsive.config.ts`가 데스크톱 프로젝트만 쓰므로, 뷰포트를 390px로 줄여도 `pointer: fine`이 유지된다 — 코드베이스 전역 **12곳**의 `@media (hover:hover) and (pointer:fine)` 블록이 전부 활성 상태로 검증된다.

---

## §A 반응형 레이아웃

### RSP-01 — 중첩 브레이크포인트 복리 (핵심)

**근거.** `/saved`는 두 개의 그리드가 **같은 640px**에서 동시에 2열로 전환한다.

- `features/saved-library/library.module.css:14-18` — `.layout`이 `minmax(260px, 1fr) 1fr`, gap `--space-6`(24px)
- `features/saved-library/library.module.css:74-78` — 그 우측 컬럼 안의 `.list`가 `repeat(2, 1fr)`, gap `--space-4`(16px)

토큰에서 유도한 640px 뷰포트 폭 계산:

```
뷰포트                                              640px
- .shell padding-inline (--layout-gutter 16 x 2)  -> 608px
- .layout 2열, gap 24                             -> (608-24)/2 = 292px  (우측 컬럼)
- .list 2열, gap 16                               -> (292-16)/2 = 138px  (카드 1장)
- Card padding (--space-5 20 x 2) + border 1 x 2  -> 96px   (카드 내부 가용 폭)
```

96px 안에 들어가야 하는 것(`saved-library.tsx:234-252`):

- `.itemHeader` — 날짜 텍스트 + `삭제` 버튼. `library.module.css:61-65`는 `justify-content: space-between`인데 **`flex-wrap`이 없다**. 버튼 min-content 약 52px, 날짜는 min-content("2025년")까지 압축돼 겨우 들어간다.
- `LottoBallSet` — `md` 볼 36px(`lotto-ball.module.css:36-40`) 6개 + gap 8px. 한 줄에 2개가 최대(36+8+36 = 80 <= 96)이므로 **6개 숫자가 3줄로 접힌다.**

**증상.** 태블릿 세로(640~800px)에서 `/saved`의 저장 카드가 "한 조합 = 한 줄"이라는 로또 번호의 기본 시각 문법을 잃는다. 640~1023px은 iPad 세로·안드로이드 태블릿·데스크톱 창 반쪽이 모두 들어오는 구간이다.

**왜 안 잡혔나.** `e2e/responsive/document-overflow.spec.ts:32`의 `BOUNDARY_WIDTHS`에 640과 641이 **둘 다 있다.** 그런데 이건 오버플로가 아니라 **압착과 래핑**이다. flex/grid 아이템이 min-content까지 줄고 볼은 wrap되므로 `documentElement.scrollWidth`는 끝까지 `clientWidth` 이하다. `assertNoHorizontalOverflow`가 구조적으로 못 보는 종류의 결함이다(→ RSP-12).

**적용.** 안쪽 그리드의 전환 기준을 뷰포트가 아니라 **자기 컨테이너 폭**으로 바꾼다. `browserslist`가 `last 2 Chrome/Safari/Firefox/iOS versions`(package.json)이므로 `@container`는 전 대상에서 지원된다.

```css
/* features/saved-library/library.module.css */
@layer components {
  /* RSP-01(docs/improvement_claude.md): .layout이 2열로 갈라진 뒤의 우측 컬럼
     실폭은 뷰포트에서 유도할 수 없다 — 640px 뷰포트에서 이 컬럼은 292px뿐이다.
     .list의 2열 전환을 뷰포트가 아니라 이 컨테이너 폭 기준으로 옮긴다. */
  .listRegion {
    container-type: inline-size;
    container-name: saved-list;
  }

  .list {
    display: grid;
    grid-template-columns: 1fr;
    gap: var(--space-4);
  }

  /* 카드 1장이 최소 296px는 확보돼야 볼 6개가 한 줄에 들어간다
     (36px x 6 + 8px x 5 = 256px + Card padding 40px = 296px -> 2열 임계 608px). */
  @container saved-list (min-width: 608px) {
    .list {
      grid-template-columns: repeat(2, 1fr);
    }
  }
}
```

```tsx
// features/saved-library/saved-library.tsx — 기존 section에 컨테이너 클래스만 추가
<section aria-labelledby="saved-list" className={`stack ${styles.listRegion}`}>
```

> 주의: `container-type: inline-size`는 그 요소에 `contain: inline-size`를 걸어 **자식이 부모 폭을 늘리지 못하게** 한다. 컨테이너는 `.list` 자신이 아니라 반드시 **부모**여야 한다.

**같은 패턴이 있는 다른 곳.** 동일 구조가 `/analysis`에도 있다 — `analysis.module.css:27-31`의 `.layout` 2열 안에 `.facts`(`repeat(auto-fit, minmax(140px,1fr))`, 11번 줄)가 들어간다. `.facts`는 `auto-fit`이라 스스로 줄 수를 줄이므로 **안전하다**(주석 25-26줄이 이미 그 근거를 적고 있다). 즉 위험한 것은 **고정 열 수**를 뷰포트 미디어쿼리로 켜는 그리드뿐이다. 전수:

| 위치 | 안쪽 그리드 | 바깥에 2열 레이아웃 | 판정 |
|---|---|---|---|
| `library.module.css:74-78` | `.list` -> `repeat(2,1fr)` @640 | 있음 (`.layout` @640) | **결함 (RSP-01)** |
| `stats.module.css:21-40` | `.groups` -> 2열 @640, 3열 @1024 | 없음 (전폭) | 안전 (표는 `.tableWrap`이 흡수) |
| `data.module.css:36-40` | `.grid` -> `repeat(2,1fr)` @640 | 없음 | 안전 |
| `frequency.module.css:84-88` | `.extremes` -> `repeat(2,1fr)` @640 | 없음 | 안전 |
| `studio.module.css` `.strategyList` | `repeat(3,1fr)` @640 | 없음 (`/recommend`는 단일 컬럼) | 안전 — `.layout` 안으로 옮기면 즉시 결함 |
| `shell.module.css:265-269` | `.footerGroups` -> 3열 @1024 | 없음 | 안전 |

**검증.** RSP-12의 `assertFitsWithoutShrink`를 `/saved`의 `[class*="itemHeader"]`에, `assertNoWrap`을 LottoBallSet 래퍼에 걸고 640·700·800px에서 돌린다. 수동 확인은 DevTools 반응형 모드 640x900에서 `/saved`에 저장 항목 2건 이상을 만든 상태로 본다.

---

### RSP-02 — 640px 2열 패턴의 3중 복제

**근거.** 세 파일이 거의 동일한 `.layout` 규칙 + 거의 동일한 주석을 갖는다.

| 파일 | @640 | @1024 |
|---|---|---|
| `home.module.css:5-25` | `minmax(260px, 1fr) 1fr` | `minmax(320px, 630px) 1fr` |
| `analysis.module.css:2-38` | `minmax(260px, 1fr) 1fr` | `minmax(320px, 420px) 1fr` |
| `library.module.css:5-24` | `minmax(260px, 1fr) 1fr` | `minmax(260px, 340px) 1fr` |

**증상.** 사용자 증상은 없다. 유지보수 비용의 문제다 — 640px 임계를 조정하려면 세 곳을 동시에 고쳐야 하고, 한 곳만 고치면 라우트 간 레이아웃 리듬이 어긋난다. `--tabbar-reserve`를 `:root` 단일 소스로 올린 KF-06과 같은 종류의 문제다.

**적용 (권고).** 공통 규칙으로 접되, **데스크톱 좌측 컬럼 상한만 라우트별 변수로 남긴다.** 630 / 420 / 340px은 라우트마다 실제로 다르고 그럴 만한 이유가 있으므로(히어로 vs 번호판 vs 대조 폼) 이것까지 통일하면 안 된다.

```css
/* shared/styles/shared-components.css — pillActive와 같은 이유로 components 레이어에 둔다 */
@layer components {
  /* RSP-02(docs/improvement_claude.md): home/analysis/library가 거의 동일한
     "좁은 조작부 + 넓은 결과" 2열 전환을 각자 복제했다. 640px 전환 자체는
     공유하고, 데스크톱 좌측 컬럼 상한만 --split-lead-max로 라우트가 주입한다. */
  .splitLayout {
    display: grid;
    grid-template-columns: 1fr;
    gap: var(--space-6);
    align-items: start;
  }

  @media (min-width: 640px) {
    .splitLayout {
      grid-template-columns: minmax(260px, 1fr) 1fr;
    }
  }

  @media (min-width: 1024px) {
    .splitLayout {
      grid-template-columns:
        minmax(var(--split-lead-min, 320px), var(--split-lead-max, 420px))
        1fr;
    }
  }
}
```

```css
/* 각 라우트 모듈은 값만 남긴다 — 예: library.module.css */
@layer components {
  .layout {
    --split-lead-min: 260px;
    --split-lead-max: 340px;
  }
}
```

**반론(기록용).** 라우트가 셋뿐이고 각자 주석으로 이미 연결돼 있으므로 "복제를 그대로 두는" 선택도 방어 가능하다. 다만 RSP-01을 고치면서 `library.module.css`의 `.layout`을 건드리게 되므로 **그 작업과 묶는 것이 가장 싸다**. 단독 리팩터링으로는 우선순위 하.

**검증.** `npm run test:e2e:visual` — 세 라우트 x 640/1024/1280px에서 픽셀 차이가 0이어야 한다(순수 리팩터링이므로).

---

### RSP-03 — `flex-wrap` 누락 지점 전수

**근거.** `justify-content: space-between` + 가변 길이 텍스트 + `flex-wrap` 없음의 조합을 전수 조사했다.

| 위치 | 구성 | 판정 |
|---|---|---|
| `library.module.css:61-65` `.itemHeader` | 날짜 텍스트 ↔ `삭제` 버튼 | **결함** — RSP-01의 96px 폭에서 양쪽이 min-content까지 압착 |
| `shell.module.css:20-26` `.headerInner` | 브랜드 ↔ 헤더 액션 | 안전 — `header-no-wrap.spec.ts`가 1024~1280px을 실측 감시, `.brand`에 `white-space:nowrap`(shell:40) |
| `dialog.module.css:42-47` `.header` | 제목 ↔ 닫기 버튼 | 안전 — 제목이 `reset.css`의 `word-break:keep-all; overflow-wrap:anywhere` 대상 |
| `studio.module.css` `.resultsHeader` | 제목 ↔ 이력 링크 | 안전 — `flex-wrap: wrap` 있음 |
| `page-header.module.css:2-7` `.header` | 타이틀 그룹 ↔ 액션 | 안전 — `flex-wrap: wrap` 있음 |

즉 실제 결함은 `.itemHeader` 한 곳이다. 나머지는 **"확인 완료"**로 기록해 재조사를 막는다.

```css
/* features/saved-library/library.module.css */
.itemHeader {
  display: flex;
  /* RSP-03(docs/improvement_claude.md): .list가 2열로 갈라진 좁은 카드에서
     날짜와 삭제 버튼이 서로를 min-content까지 밀어낸다 — 좁으면 줄을 바꾼다.
     gap이 이미 있으므로 래핑돼도 두 줄이 붙지 않는다. */
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}
```

**검증.** 여기서는 래핑을 **허용**하는 것이 고침이다. `assertFitsWithoutShrink(page, '[class*="itemHeader"]')`로 자식이 min-content 미만으로 눌리지 않는지를 본다.

---

### RSP-04 — `minmax(<고정값>, 1fr)`의 컨테이너 하한 안전판

**근거.** `auto-fill`/`auto-fit` 그리드의 첫 인자가 고정 길이면 **컨테이너가 그 길이보다 좁아질 때 트랙이 컨테이너를 넘는다.** `minmax()`의 min은 컨테이너에 의해 축소되지 않기 때문이다.

| 위치 | 현재 | 위험 |
|---|---|---|
| `number-grid.module.css:4` | `minmax(var(--target-min), 1fr)` = 44px | 320px `/analysis` 카드 내부 약 208px — 현재 안전. RSP-01류 중첩이 생기면 즉시 위험 |
| `analysis.module.css:11` `.facts` | `minmax(140px, 1fr)` | 안전 |
| `ops-console.module.css` `.grid` | `minmax(10rem, 1fr)` = 160px | 안전 |
| `page-skeleton.module.css` `.cards` | `minmax(160px, 1fr)` | 안전 |

**증상.** 현재는 어느 곳도 넘치지 않는다. 이 항목은 **결함 수정이 아니라 예방 접종**이다. RSP-01을 `@container`로 고치면 컨테이너 폭이 뷰포트에서 분리되므로 이 하한들이 처음으로 실제 위험이 된다.

```css
/* entities/round/ui/number-grid.module.css */
.grid {
  display: grid;
  /* RSP-04(docs/improvement_claude.md): minmax()의 min은 컨테이너에 의해 축소되지
     않는다 — 컨테이너가 44px보다 좁아지면 트랙이 밖으로 넘는다. min(…, 100%)로
     감싸면 넓을 때 동작은 완전히 같고, 좁을 때만 트랙이 따라 줄어든다. */
  grid-template-columns: repeat(auto-fill, minmax(min(var(--target-min), 100%), 1fr));
  gap: var(--space-2);
}
```

나머지 셋도 `minmax(min(140px, 100%), 1fr)` 형태로 바꾼다. **`.cell`의 `min-width: var(--target-min)`(number-grid:13)은 건드리지 않는다** — 그건 터치 타깃 계약이지 트랙 크기가 아니다.

**검증.** `document-overflow.spec.ts`가 이미 전 라우트 x 320px을 스윕하므로 회귀는 자동으로 잡힌다. 개선 자체는 DevTools에서 `.grid` 부모에 임시로 `width: 30px`을 줘서 트랙이 따라 줄어드는지 본다.

---

### RSP-05 — 고정 gutter와 넓은 화면의 낭비

**근거.** `tokens.css:124-125` — `--layout-shell-max: 1180px`, `--layout-gutter: 16px`. `utilities.css`의 `.shell`이 이 둘만 쓴다. 즉 **320px과 2560px에서 좌우 여백이 똑같이 16px**이고, 1180px을 넘는 폭은 전부 균등 마진으로 버려진다.

**증상.** 320px에서는 16px 여백이 적절하지만, 1440px 이상에서 콘텐츠가 화면 한가운데 1180px 띠로 떠 있으면서 그 띠의 안쪽 여백은 여전히 16px이라 "화면은 넓은데 글은 가장자리에 붙어 있는" 인상을 준다.

**왜 안 잡혔나.** 이건 결함이 아니라 튜닝이다. 어떤 단언도 "여백이 적절한가"를 묻지 않는다.

```css
/* shared/styles/tokens.css */
:root {
  --layout-shell-max: 1180px;
  /* RSP-05(docs/improvement_claude.md): 320px에서의 16px은 적절하지만 1440px에서도
     16px이면 넓은 화면에서 콘텐츠가 가장자리에 붙는다. 하한은 기존 값을 그대로
     유지해 좁은 폭의 실측(document-overflow.spec.ts의 320px 스윕)을 건드리지 않는다. */
  --layout-gutter: clamp(16px, 2.5vw, 32px);
}
```

**부작용 두 가지를 반드시 함께 확인한다.**

1. `.tableWrap`(surface.module.css:31)의 가로 스크롤 발생 임계가 최대 32px 앞당겨진다.
2. `2.5vw`가 640px에서 정확히 16px이므로 641px부터 gutter가 자라기 시작한다. RSP-01의 640px 폭 계산이 그 지점부터 달라진다 — **RSP-01을 먼저 고치고 이걸 나중에 한다.**

**검증.** `npm run test:e2e:responsive`(320px 스윕이 회귀를 잡는다) + `npm run test:e2e:visual`로 baseline 갱신. 시각 baseline이 전 라우트에서 바뀌므로 **의도적 baseline 재생성이 필요한 유일한 항목**이다.

---

### RSP-06 — `100vw` 기반 클램프의 스크롤바 오차 (확인 완료·조치 불필요)

**근거.** 세 곳이 `calc(100vw - 2 * var(--space-4))`를 쓴다.

- `overlay.module.css:16` `.menu` — `max-width: min(280px, calc(100vw - 2 * var(--space-4)))`
- `overlay.module.css:83` `.toastRegion` — `width: min(420px, calc(100vw - 2 * var(--space-4)))`
- `login-popover.module.css:19` `.warning` — `.menu`와 동일

`100vw`는 **클래식 스크롤바 폭을 포함한다**(Windows Chrome/Firefox에서 약 15px). 즉 데스크톱에서 실제 가용 폭은 `100vw - 15px`이다.

**판정: 조치 불필요.** 세 곳 모두 `min()`으로 280/420px 상한이 걸려 있고, `calc()` 항이 이기려면 뷰포트가 312px(= 280 + 32) 미만이어야 하는데 그 폭에는 스크롤바가 없는 모바일뿐이다. 다만 상한을 올리거나 데스크톱 전용 오버레이를 추가할 때 이 오차가 실효화되므로 기록해 둔다. 그때의 대안은 부모 기준 `100%` 또는 `100cqw`다 — `100dvw`는 스크롤바를 똑같이 포함하므로 해결책이 아니다.

---

### RSP-07 — 타입 스케일이 컨테이너가 아니라 뷰포트를 본다

**근거.** `tokens.css:89-94`의 6개 토큰이 전부 `clamp(min, base + Nvw, max)` 형태다. `vw`는 뷰포트 폭이므로, RSP-01처럼 2열로 갈라진 292px 컬럼 안에서도 폰트는 **1280px 데스크톱과 같은 크기**를 쓴다.

**적용 판단 — `cqi` 전면 도입은 비권장.** 컨테이너가 없는 요소에서 "가장 가까운 컨테이너"로 폴백하며 소형 뷰포트 폰트로 되돌아간다. 전역 토큰 6개를 한꺼번에 컨테이너 의존으로 만드는 것은 위험 대비 이득이 없다.

**대신 권고하는 국소 대응.** 실제로 컬럼 폭에 민감한 것은 가장 큰 스텝 하나뿐이다 — `--text-2xl`(최대 35.2px)을 쓰는 `page-header.module.css`의 `.title`.

```css
/* shared/ui/page-header.module.css */
@layer components {
  /* RSP-07(docs/improvement_claude.md): --text-2xl은 뷰포트 vw 기반이라 2열
     레이아웃의 좁은 컬럼 안에서도 35px을 쓴다. 전역 토큰을 바꾸는 대신
     여기서만 컨테이너 폭을 상한으로 겹쳐 건다 — min()이라 컨테이너가 없어도
     안전하게 --text-2xl로 폴백한다. */
  .titleGroup {
    container-type: inline-size;
  }

  .title {
    font-size: min(var(--text-2xl), 9cqi);
  }
}
```

**우선순위 하.** 현재 `PageHeader`가 2열 레이아웃의 좁은 컬럼 안에 놓인 라우트는 없다. RSP-01을 고친 뒤 재평가한다.

---

### RSP-08 — 탭 개수 5가 CSS에 하드코딩돼 있다

**근거.** 두 소스가 서로를 모른 채 "5"를 각자 갖는다.

- `app/_shell/nav-items.ts:49-55` — `TAB_BAR_ITEMS` 배열 5개
- `shell.module.css:180` — `.indicator { width: calc(100% / 5) }`
- `shell.module.css:191-210` — `[data-active-index="0"]`~`"4"` 다섯 규칙(`translateX(0%)`~`translateX(400%)`)

`.tabBar` 자체는 `grid-auto-flow: column; grid-auto-columns: 1fr`(shell:164-165)이라 **개수에 무관하게 올바르게 동작한다.** 어긋나는 것은 인디케이터뿐이다.

**증상.** 지금은 정상이다. 탭이 4개가 되면 인디케이터 폭이 20%로 남아 활성 탭보다 좁게 그려지고, 6개가 되면 여섯 번째에서 아예 사라진다(`data-active-index="5"`에 매칭되는 규칙이 없어 `opacity: 0`). **조용히 틀리는** 종류의 결함이다.

**적용 — 최소 방어(권고).** 탭 개수는 `nav-items.ts:48` 주석이 "5개를 넘기면 터치 타깃이 44px 아래로 내려간다"고 상한을 명시하고 있어 실제로 바뀔 가능성이 낮다. 비용 대비 이득이 낮으므로 **구조 변경 대신 결합을 주석으로 못박는다.**

```ts
// app/_shell/nav-items.ts
/** 모바일 하단 탭. 5개를 넘기면 터치 타깃이 44px 아래로 내려간다(§12.7).
 *  RSP-08(docs/improvement_claude.md): shell.module.css:180의 calc(100%/5)와
 *  :191-210의 nth 규칙 5개가 이 배열 길이를 하드코딩하고 있다 — 이 배열을
 *  바꾸면 그 두 곳을 반드시 함께 고쳐야 한다. .tabBar 자체는
 *  grid-auto-columns:1fr이라 개수에 무관하다(어긋나는 것은 .indicator뿐). */
export const TAB_BAR_ITEMS: NavItem[] = [ /* … */ ];
```

**구조적 해법(개수가 실제로 바뀔 때).** `tab-bar.tsx`가 `data-tab-count`를 내보내고 CSS가 그 값에 매칭한다. 인라인 `style`은 CSP `style-src` 정책상 쓰지 않는다.

```css
/* app/_shell/shell.module.css */
.tabBar[data-tab-count="4"] .indicator { width: 25%; }
.tabBar[data-tab-count="5"] .indicator { width: 20%; }
.tabBar[data-tab-count="6"] .indicator { width: calc(100% / 6); }
```

**검증.** `TAB_BAR_ITEMS`에서 항목을 하나 임시로 지우고 `/`를 390px에서 열어 인디케이터 폭이 탭 폭과 일치하는지 본다.

---

### RSP-09 — 가로모드·짧은 뷰포트에 CSS 분기가 하나도 없다

**근거.** 코드베이스 전체 CSS에서 **높이 기준 미디어쿼리는 0건**이다(`@media (max-height:)`, `(orientation:)` 전무). 유일한 높이 판정은 JS에 있다 — `ad-unit.tsx:274`의 `SHORT_VIEWPORT_QUERY = "(max-height: 480px)"`.

가로모드 844x390에서 실제로 벌어지는 일:

| 요소 | 계산 | 결과 |
|---|---|---|
| `.header` (sticky) | `min-height: 64px` (tokens:127) | 화면 높이의 16%를 상시 점유 |
| `.tabBar` (fixed) | `--layout-tabbar-h: 56px` (tokens:128) | 14% 추가 점유 |
| 남는 본문 높이 | 390 − 64 − 56 | **270px** |
| `dialog .panel` | `max-height: 85dvh` (dialog:18) | 331px + backdrop padding 32px = 363px |

`.backdrop`(dialog:2-11)은 `fixed; inset: 0`에 `padding: var(--space-4)`뿐이고 `--z-overlay`(400)가 `--z-tabbar`(300)보다 위이므로, **현재 구조는 우연히 안전하다.** 390px 뷰포트에 363px이 들어가고 탭바에 가려지지도 않는다.

**실제 비대칭 하나.** `useKeyboardOpen`(`shared/hooks/use-keyboard-open.ts`)을 소비하는 곳은 `ad-unit.tsx:283` **한 곳뿐**이다. 가상 키보드가 열리면 광고는 내려가지만, 같은 순간 `/community/write`의 `.textarea`(`field.module.css`, `min-height: 160px`)와 다이얼로그 패널은 아무 반응이 없다. iOS Safari에서 키보드가 화면의 절반 이상을 먹으면 다이얼로그의 `85dvh`가 **키보드 뒤로 내려간다** — `dvh`는 키보드를 반영하지 않는다.

**적용.** CSS만으로는 키보드를 볼 수 없으므로 최소 방어는 상한 강화다.

```css
/* shared/ui/dialog.module.css */
.panel {
  /* RSP-09(docs/improvement_claude.md): 85dvh는 가로모드(844x390)에서 331px이고,
     가상 키보드가 올라온 상태는 dvh가 반영하지 않는다. svh(가장 작은 뷰포트)를
     함께 걸어 주소창이 펼쳐졌거나 키보드가 올라온 상태에서도 패널이 화면 밖으로
     내려가지 않게 한다. */
  max-height: min(85dvh, 100svh - 2 * var(--space-4));
}
```

키보드까지 정확히 따라가려면 **이미 있는 훅을 재사용한다**(신규 훅 없음).

```tsx
// shared/ui/dialog.tsx — useScrollLock 바로 옆에 붙인다
import { useKeyboardOpen } from "@/shared/hooks/use-keyboard-open";
// …
const keyboardOpen = useKeyboardOpen();
// panel에 data-keyboard-open={keyboardOpen ? "true" : undefined}
```

```css
.panel[data-keyboard-open="true"] {
  /* 키보드가 올라오면 패널을 화면 상단에 붙이고 높이를 절반으로 낮춘다. */
  align-self: start;
  max-height: 50svh;
}
```

**검증.** `e2e/responsive/fixed-ui.spec.ts:31`이 이미 844x390 프로젝트를 갖고 있다 — 여기에 "다이얼로그를 연 상태에서 `.panel`이 뷰포트 안에 완전히 들어온다"는 케이스를 추가한다. 키보드는 Playwright로 에뮬레이트할 수 없으므로 실기기 확인이 필요하다.

---

## §B 반응형 회귀 테스트

### RSP-10 — responsive 트랙이 터치 포인터를 렌더한 적이 없다 (핵심)

**근거.**

- `playwright.responsive.config.ts:25` — `{ name: "chromium", use: { ...devices["Desktop Chrome"] } }`
- `playwright.responsive.config.ts:33-37` — `{ name: "firefox-overflow", use: { ...devices["Desktop Firefox"] } }`

두 프로젝트 모두 `hasTouch: false`, `isMobile: false`다. 스펙들은 폭만 바꾼다 — `touch-target.spec.ts:42`, `form-controls.spec.ts:10`, `fixed-ui.spec.ts:10`, `touch-target-stretched-link.spec.ts:24`, `fixed-ui-toast-safe-area.spec.ts:18`이 모두 `test.use({ viewport: { width: 390, height: 844 } })`.

**Playwright에서 뷰포트 크기는 `pointer`/`hover` 미디어 특성을 바꾸지 않는다.** 그 특성은 `hasTouch`/`isMobile`에서 온다. 따라서 이 트랙은 390x844에서도 `(hover: hover) and (pointer: fine)`를 **참으로** 평가한다.

**무엇이 검증되지 않는가.** 그 가드가 **12개 블록**(8개 파일)에 있다.

```
button.module.css             x 4   .primary / .secondary / .quiet / .dangerQuiet hover
overlay.module.css            x 2   .menuItem / .menuItemDanger hover
home.module.css               x 1   .cta hover (translateY(-1px) + 그림자 확장)
shell.module.css              x 1   .navLink hover
lotto-ball.module.css         x 1   .ball hover (translateY(-2px) scale(1.05))
number-grid.module.css        x 1   .cell hover (scale(1.05)) / .disabled 무력화
studio.module.css             x 1   .historyLink hover
post-summary-card.module.css  x 1   .title a hover (underline)
```

즉 **실제 모바일 사용자가 보는 CSS는 responsive 트랙에서 한 번도 렌더된 적이 없다.** 특히 `lotto-ball`과 `number-grid`의 `scale(1.05)`는 요소의 실측 사각형을 바꾸므로, hit area 단언(`assertMinHitArea`)이 **터치 기기에는 존재하지 않는 확대 상태를 재고 있을 수 있다.**

**왜 다른 트랙으로 못 메우나.** `playwright.a11y.config.ts:28`에 `Pixel 7`, `playwright.visual.config.ts:41-45`에 `Pixel 5`/`iPhone 14`/`iPad (gen 7)`이 있다. 그러나 **a11y 트랙은 axe 규칙을, visual 트랙은 픽셀 diff를 돌린다** — `responsive-assertions.ts`의 프로그래매틱 단언은 어느 쪽에서도 실행되지 않는다.

**적용.** `firefox-overflow`가 이미 쓰는 `testMatch` 스코프 패턴을 그대로 재사용한다. 트랙 전체를 두 배로 돌리지 않는다.

```ts
// playwright.responsive.config.ts
projects: [
  { name: "chromium", use: { ...devices["Desktop Chrome"] } },

  // (기존 firefox-overflow 주석 그대로)
  {
    name: "firefox-overflow",
    testMatch: /document-overflow\.spec\.ts/,
    use: { ...devices["Desktop Firefox"] },
  },

  // RSP-10(docs/improvement_claude.md): Desktop Chrome은 뷰포트를 390px로
  // 줄여도 hasTouch:false라 (hover:hover) and (pointer:fine)을 참으로 평가한다 —
  // 코드베이스 12곳의 hover 가드 블록이 전부 활성 상태로 검증돼 왔다.
  // lotto-ball/.cell의 hover scale(1.05)은 hit area 실측 사각형을 직접 바꾸므로,
  // 터치 판정이 실제로 필요한 스펙에만 Pixel 7 프로젝트를 스코프한다
  // (firefox-overflow와 같은 이유로 트랙 전체를 재실행하지 않는다).
  {
    name: "mobile-chromium",
    testMatch: /(touch-target.*|form-controls|fixed-ui.*)\.spec\.ts/,
    use: { ...devices["Pixel 7"] },
  },
],
```

**주의 — 도입 시 예상되는 red.** `Pixel 7`은 `viewport: 412x915`, `deviceScaleFactor: 2.625`다. 스펙의 `test.use({ viewport })`가 프로젝트 뷰포트를 덮어쓰므로 폭은 390으로 유지되지만 `isMobile: true`와 `hasTouch: true`는 남는다. `assertMinHitArea`가 hover scale 없는 실제 크기를 재게 되므로 **일부 요소가 처음으로 44px 미달로 red가 될 수 있다** — 그게 이 항목의 목적이다.

**검증.** `npx playwright test --config=playwright.responsive.config.ts --project=mobile-chromium`. red가 나오면 그것이 지금까지 감춰져 있던 실제 결함이다.

---

### RSP-11 — hit area·폼 컨트롤의 뷰포트 커버리지 공백

**근거.** `document-overflow.spec.ts:32`는 12개 폭(320~1440) + 320x512 + 844x390을 스윕한다. 반면 나머지 responsive 스펙은 전부 **390x844 단일 뷰포트**다.

| 스펙 | 현재 뷰포트 | 빠진 것 |
|---|---|---|
| `touch-target.spec.ts` | 390x844 | **320**, 360(가장 흔한 안드로이드), 844x390 |
| `touch-target-stretched-link.spec.ts` | 390x844 | 동일 |
| `form-controls.spec.ts` | 390x844 | 320 |
| `fixed-ui.spec.ts` | 390x844 + 844x390 | 이미 가로모드 포함 (양호) |

`form-controls`는 `--font-size-input: max(16px, …)`(tokens:100) 덕에 폭과 무관하게 안전하다. 다만 `community.module.css:143`처럼 공통 `Field`를 거치지 않고 개별 정의된 입력이 늘어나면 폭별 확인이 필요해진다.

hit area 쪽에서 실제로 주의할 요소는 `.adStickyMobileClose`(`ad-unit.module.css:64-83`)다 — `::before { inset: -12px }`로 히트 영역을 확장하는데, `getBoundingClientRect()`는 `::before`를 포함하지 않는다. `assertMinHitArea`가 이 요소를 어떻게 세는지 확인이 필요하다.

**적용.** `document-overflow.spec.ts`가 이미 쓰는 폭 배열 스윕 패턴을 hit area에도 적용한다.

```ts
// e2e/responsive/touch-target.spec.ts 상단 — 기존 test.use({ viewport })를 대체
/**
 * RSP-11(docs/improvement_claude.md): 390x844 단일 뷰포트만 재고 있었다.
 * hit area는 가장 좁은 폭에서 먼저 깨지고(320), 가장 흔한 안드로이드 폭(360)이
 * 빠져 있었다. document-overflow.spec.ts의 폭 스윕 패턴을 가져오되, 라우트 x 폭
 * 전부가 아니라 폭 3종으로만 늘려 CI 시간 증가를 억제한다.
 */
const HIT_AREA_VIEWPORTS = [
  { width: 320, height: 568 },
  { width: 360, height: 800 },
  { width: 390, height: 844 },
];

for (const viewport of HIT_AREA_VIEWPORTS) {
  test.describe(`${viewport.width}px`, () => {
    test.use({ viewport });
    // … 기존 테스트 본문 …
  });
}
```

**검증.** 실행 시간이 3배가 된다. `touch-target.spec.ts`가 16개 라우트를 도므로 3 x 16 = 48회 내비게이션 — 로컬 기준 예상 2~3분. 그 이상이면 폭을 320/390 두 개로 줄인다.

---

### RSP-12 — "압착" 단언이 없다

**근거.** `e2e/lib/responsive-assertions.ts`가 제공하는 단언은 5종이다.

| 함수 | 무엇을 보는가 | RSP-01/03을 잡는가 |
|---|---|---|
| `assertNoHorizontalOverflow` (17-41) | `documentElement.scrollWidth > clientWidth` | 아니오 — 압착은 오버플로가 아니다 |
| `assertMinHitArea` (48-65) | 요소 사각형 < 44px | 아니오 — 버튼은 44px를 유지한 채 옆 텍스트가 눌린다 |
| `assertFormControlFontSizeAtLeast16px` (71-89) | computed `font-size` < 16px | 무관 |
| `assertNotOccludedByFixedUi` (112-143) | 고정 요소와의 사각형 교차 | 무관 |
| `assertElementMaxHeight` (96-105) | 요소 높이 > 임계 | 래핑은 잡지만 **`header a` 한 곳에만 쓰인다** |

즉 "요소가 자기 min-content까지 눌렸다"와 "한 줄이어야 할 것이 여러 줄이 됐다"를 잡는 단언이 없다.

**적용.** 두 함수를 추가한다. 기존 파일의 스타일(실패 메시지에 원인이 드러나도록)을 따른다.

```ts
// e2e/lib/responsive-assertions.ts 에 추가

/**
 * RSP-12(docs/improvement_claude.md): 한 줄이어야 할 요소가 여러 줄로 접혔는지
 * 본다. assertElementMaxHeight는 임계 픽셀을 호출부가 알아야 하지만, 여기서는
 * line-height 대비 실측 높이 비율로 판정하므로 폰트 크기 변화에 영향받지 않는다.
 * 로또 볼 세트처럼 "한 묶음이 시각적으로 끊기면 안 되는" 요소에 쓴다.
 */
export async function assertNoWrap(page: Page, selector: string, maxLines = 1) {
  const wrapped = await page.$$eval(
    selector,
    (elements, limit) =>
      elements
        .filter((el) => {
          const style = window.getComputedStyle(el);
          if (style.display === "none") return false;
          const lineHeight = parseFloat(style.lineHeight);
          // normal이면 폰트 크기의 1.2배로 근사한다 — 정확한 값이 아니라
          // "몇 배로 늘었는가"만 보면 되므로 근사로 충분하다.
          const unit = Number.isNaN(lineHeight) ? parseFloat(style.fontSize) * 1.2 : lineHeight;
          return el.getBoundingClientRect().height > unit * (limit + 0.5);
        })
        .map((el) => {
          const rect = el.getBoundingClientRect();
          const cls = el.className ? "." + String(el.className).split(" ").join(".") : "";
          return `${el.tagName.toLowerCase()}${cls} (h=${Math.round(rect.height)})`;
        }),
    maxLines,
  );
  expect(wrapped, `${maxLines}줄을 넘겨 래핑된 요소: ${wrapped.join(", ")}`).toEqual([]);
}

/**
 * RSP-12(docs/improvement_claude.md): 요소가 자기 콘텐츠의 min-content 폭까지
 * 눌렸는지 본다. flex/grid 아이템은 min-width:auto 아래로는 줄지 않으므로
 * document 오버플로를 만들지 않는다 — assertNoHorizontalOverflow가 구조적으로
 * 못 보는 압착이 여기서 잡힌다(RSP-01의 /saved 640px가 정확히 이 경우다).
 */
export async function assertFitsWithoutShrink(page: Page, selector: string, tolerance = 1) {
  const squeezed = await page.$$eval(
    selector,
    (elements, tol) =>
      elements
        .filter((el) => el.scrollWidth > el.clientWidth + tol)
        .map((el) => {
          const cls = el.className ? "." + String(el.className).split(" ").join(".") : "";
          return `${el.tagName.toLowerCase()}${cls} (scroll=${el.scrollWidth} client=${el.clientWidth})`;
        }),
    tolerance,
  );
  expect(squeezed, `콘텐츠가 컨테이너보다 넓게 눌린 요소: ${squeezed.join(", ")}`).toEqual([]);
}
```

새 스펙:

```ts
// e2e/responsive/container-squeeze.spec.ts (신규)
import { test } from "@playwright/test";

import { assertFitsWithoutShrink, assertNoWrap } from "../lib/responsive-assertions";

/**
 * RSP-01/RSP-03(docs/improvement_claude.md): 2열 레이아웃이 켜지는 640px 직후가
 * 컨테이너 폭이 가장 좁은 구간이다 — 뷰포트는 넓어졌는데 안쪽 컬럼은 292px로
 * 절반이 된다. document 오버플로는 나지 않으므로 document-overflow.spec.ts가
 * 그대로 통과시킨다.
 */
const SPLIT_WIDTHS = [640, 700, 800, 1023];

test.describe("2열 전환 직후 컨테이너에서 콘텐츠가 압착되지 않는다", () => {
  for (const width of SPLIT_WIDTHS) {
    test(`${width}px에서 /saved 저장 카드가 압착되지 않는다`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await page.goto("/saved", { waitUntil: "networkidle" });
      await assertNoWrap(page, '[class*="itemHeader"] > span');
      await assertFitsWithoutShrink(page, '[class*="itemHeader"]');
    });
  }
});
```

**검증.** 위 스펙을 **먼저** 추가하면 RSP-01 수정 전에는 red, 수정 후 green이어야 한다. 그 순서가 지켜지지 않으면 단언이 실제 결함을 못 보고 있다는 뜻이다.

---

### RSP-13 — firefox 스코프 재평가 조건 (조치 불필요·조건 기록)

**근거.** `playwright.responsive.config.ts:26-32`의 주석은 firefox를 `document-overflow.spec.ts`에만 스코프한 이유를 명시한다 — "responsive 트랙 전체를 firefox로 재실행하면 CI 비용이 두 배". 근거가 명확하므로 **유지 권고**다.

**단, 재평가 조건을 미리 적어 둔다.** KF-03의 원인은 Firefox의 grid automatic minimum size 처리 차이였고, 해법은 `surface.module.css:14`의 `.card { min-width: 0 }`였다. RSP-01을 `@container`로 고치면 `container-type: inline-size`가 `contain: inline-size`를 걸어 **격리 경계가 하나 더 생긴다** — 브라우저 간 구현 차이가 다시 드러날 수 있는 정확히 그 지점이다.

**조건.** RSP-01 적용 후 다음 중 하나라도 해당하면 `/saved`를 firefox 스코프에 넣는다.

1. `@container`를 쓰는 라우트가 2개 이상이 됐다.
2. `container-type`을 건 요소가 `display: grid` 아이템이다 — `/saved`의 `<section>`이 `.layout` 그리드 아이템이므로 **RSP-01 적용 즉시 해당한다**.

```ts
// 그때 적용할 변경 — testMatch에 추가만 하면 된다
{
  name: "firefox-overflow",
  testMatch: /(document-overflow|container-squeeze)\.spec\.ts/,
  use: { ...devices["Desktop Firefox"] },
},
```

---

## §C 적응형 접근성

### RSP-14 — `forced-colors` 대응 0건

**근거.** 코드베이스 전체에서 `forced-colors` 0건. Windows 고대비 모드(및 브라우저 강제 색상)에서 `background-color`, `background-image`, `box-shadow`, `border-color`가 시스템 색으로 강제 치환된다.

| 위치 | 무엇이 사라지나 | 정보 손실인가 |
|---|---|---|
| `lotto-ball.module.css:50,59,68,77,86,95` | 구간별 `--gradient-ball-*` 6종 → 전부 동일 배경 | **아니오** — 숫자 텍스트가 값을 전달한다. 색은 "구간"의 보조 부호 |
| `lotto-ball.module.css:16-19` | 인셋 3D 쉐이딩 | 아니오 — 순수 장식 |
| `lotto-ball.module.css:103-113` `.matched` | `outline: 3px solid #f59e0b` | **유지된다** — `outline`은 치환 대상이 아니다. 색만 시스템 색으로 바뀐다 |
| `number-grid.module.css` `.locked` | 그라디언트 배경 | 아니오 — `aria-label`이 "고정"을, ★ 뱃지가 시각 신호를 준다(651줄 주석이 그 설계를 명시) |
| `number-grid.module.css` `.excluded::before` | 45° 빗금 오버레이 | 아니오 — `text-decoration: line-through`가 남는다 |
| `number-grid.module.css` `.badge` | 배경 + `box-shadow` | 부분 — 뱃지 글자(★/✕)는 남지만 배경이 사라져 셀 위에서 읽기 어려워질 수 있다 |
| `shell.module.css:175-189` `.indicator` | `--color-tabbar-active-bg` | 아니오 — `.tabLink[aria-current="page"]`가 `font-weight:700`을 함께 준다(shell:238-241) |
| `companion-pair-row.module.css:29-43` `.medal1~3` | 금/은/동 그라디언트 → 동일 | **부분** — 1·2·3위 구분이 순전히 색. 다만 표의 행 순서가 순위를 전달한다 |
| `feedback.module.css` 배지 5종 | 배경·글자색 | 아니오 — 배지 텍스트가 상태를 말한다 |
| `frequency-bar.module.css`, `pattern-distribution.module.css` | SVG `fill` | **아니오, 사라지지도 않는다** — SVG `fill`은 CSS 배경이 아니라 페인트 속성이라 강제 색상 모드에서 그대로 렌더된다 |

**종합 판정: 정보 손실이 아니라 식별성 저하다.** 이 프로젝트는 "색만으로 정보를 전달하지 않는다"는 규칙을 이미 일관되게 지켰기 때문에(주석 여러 곳이 그 근거를 명시) 강제 색상 모드에서도 **모든 정보가 읽힌다.** 잃는 것은 시각적 구분 속도다. 그래서 **우선순위 중**이다.

**적용.** 형태를 `border`로 되살린다 — `border`는 강제 색상 모드에서 `CanvasText`로 치환되며 살아남는다.

```css
/* entities/round/ui/lotto-ball.module.css */
@layer components {
  /* RSP-14(docs/improvement_claude.md): 강제 색상 모드에서 --gradient-ball-* 6종이
     전부 같은 배경으로 치환돼 구간 구분이 사라진다. 정보 자체는 볼 안의 숫자가
     전달하므로 손실은 아니지만, 원형 요소의 경계마저 사라져 "공"으로 읽히지
     않는 것이 문제다 — 테두리로 형태만 되살린다. 색 구분은 포기한다(강제 색상
     모드의 취지가 정확히 그것이다). */
  @media (forced-colors: active) {
    .ball {
      forced-color-adjust: none;
      background: Canvas;
      color: CanvasText;
      border: 1px solid CanvasText;
      /* 인셋 쉐이딩과 글로우는 어차피 치환되므로 명시적으로 없앤다. */
      box-shadow: none;
    }

    .matched {
      /* outline은 살아남지만 색이 .ball 테두리와 구분되지 않는다. */
      outline: 3px solid Highlight;
    }
  }
}
```

```css
/* entities/round/ui/number-grid.module.css */
@layer components {
  /* RSP-14(docs/improvement_claude.md): .locked의 그라디언트와 .excluded::before의
     빗금이 사라진다. 상태 자체는 aria-label과 ★/✕ 뱃지가 전달하므로 손실은
     아니나, 시스템 색 어휘로 상태를 되살려 식별 속도를 유지한다. */
  @media (forced-colors: active) {
    .locked {
      forced-color-adjust: none;
      background: Highlight;
      color: HighlightText;
      border: 2px solid Highlight;
    }

    .excluded {
      border: 2px dashed GrayText;
      color: GrayText;
    }

    .excluded::before {
      display: none;
    }

    .badge {
      border: 1px solid CanvasText;
      background: Canvas;
    }
  }
}
```

```css
/* entities/statistics/ui/companion-pair-row.module.css */
@layer components {
  /* RSP-14: 금/은/동 구분이 순전히 그라디언트 색이다. 표 행 순서가 순위를
     전달하므로 정보 손실은 없으나, 세 메달이 동일해 보이는 것은 피한다 —
     테두리 굵기로 위계를 준다. */
  @media (forced-colors: active) {
    .medal {
      forced-color-adjust: none;
      background: Canvas;
      border: 1px solid CanvasText;
    }
    .medal1 { border-width: 3px; }
    .medal2 { border-width: 2px; }
    .medal3 { border-width: 1px; }
  }
}
```

**검증.** Windows: 설정 → 접근성 → 대비 테마 적용 후 `/`, `/recommend`, `/companion` 확인. Chrome DevTools: Rendering → "Emulate CSS media feature forced-colors: active". 자동화는 Playwright `use: { forcedColors: "active" }`로 가능하며, visual 트랙에 프로젝트를 하나 추가하는 것이 가장 싸다.

---

### RSP-15 — `prefers-reduced-transparency` 미대응

**근거.** 반투명·블러가 걸린 지점:

| 위치 | 효과 |
|---|---|
| `shell.module.css:14-17` `.header` | `background: var(--color-surface-3)`(알파 0.9 / 다크 0.065) + `backdrop-filter: blur(12px)` |
| `home.module.css:34-36` `.hero` | `--color-surface-2`(알파 0.82 / 다크 0.05) + `backdrop-filter: blur(16px) saturate(180%)` |
| `tokens.css:18-20, 174-176` | `--color-surface-1/2/3`이 전부 `rgba()` 알파 표면 |

`home.module.css:45-49`에 **`@supports not (backdrop-filter: blur(1px))` 폴백은 있다** — iOS 17 이하 대응이다. 그러나 "지원하지만 원하지 않는" 사용자(macOS "투명도 줄이기", Windows "투명 효과 끄기")를 위한 경로는 없다.

**증상.** 투명도 감소를 켠 사용자에게 알파 표면과 블러는 실제 접근성 문제다(전정기관 민감·인지 부하). 또한 다크 테마의 `--color-surface-1`(알파 0.035)은 **블러가 없으면 배경과 거의 구분되지 않는다** — `@supports` 폴백이 `.hero`에만 걸려 있고 `.header`나 `Card`에는 없어서, 블러 미지원 환경에서 카드 경계가 이미 약하다.

**적용.** RSP-18과 **완전히 같은 코드로 해결된다.**

```css
/* shared/styles/tokens.css — 다크/라이트 재선언 블록 다음에 둔다 */

/* RSP-15(docs/improvement_claude.md): 투명도 감소를 켠 사용자에게 알파 표면과
   backdrop-filter는 접근성 문제다. 별칭 레이어가 없는 이 tokens.css 설계 덕분에
   표면 토큰 3개만 불투명으로 재선언하면 전 화면이 따라온다 — 컴포넌트 CSS를
   하나도 건드리지 않는다. RSP-18(모바일 스크롤 시 백드롭 재합성 비용)과 같은
   코드로 해결된다. */
@media (prefers-reduced-transparency: reduce) {
  :root {
    --color-surface-1: #eef4ff;
    --color-surface-2: #f5f9ff;
    --color-surface-3: #ffffff;
  }

  [data-theme="dark"] {
    --color-surface-1: #0d1430;
    --color-surface-2: #111936;
    --color-surface-3: #16203f;
  }
}
```

```css
/* app/_shell/shell.module.css */
@media (prefers-reduced-transparency: reduce) {
  .header {
    -webkit-backdrop-filter: none;
    backdrop-filter: none;
  }
}
```

```css
/* app/(public)/home.module.css — 기존 @supports 블록 바로 아래 */
@media (prefers-reduced-transparency: reduce) {
  .hero {
    -webkit-backdrop-filter: none;
    backdrop-filter: none;
    background: var(--color-surface-solid);
  }
}
```

> 위 불투명 값은 기존 알파 표면을 캔버스 위에 합성한 근사치다. 채택 전 라이트/다크 양쪽에서 대비를 재확인하고, `tokens.css`의 기존 관행대로 `/* 대비 N:1 */` 주석을 붙인다.

**검증.** DevTools Rendering → "Emulate CSS media feature prefers-reduced-transparency: reduce". `npm run test:e2e:a11y`로 axe color-contrast가 여전히 통과하는지 확인 — 불투명 표면으로 바뀌면 대비 수치가 **달라진다**(대개 개선되지만 확인 필요).

---

### RSP-16 — `prefers-contrast: more` 미대응

**근거.** 경계선이 전부 토큰 하나에 걸려 있다 — `tokens.css:49` `--color-border: rgba(31, 57, 105, 0.16)`, `tokens.css:197` 다크 `rgba(255, 255, 255, 0.1)`.

이 토큰을 쓰는 곳: `Card`(surface:7), 표의 모든 `th`/`td`(surface:69), `.tableWrap`의 스크롤 힌트(surface:44-45), 입력 필드(field), 아코디언(navigation), 탭바 상단선(shell:167), 푸터 구분선(shell:113), 댓글 L자 연결선(comment-thread), 드롭다운 메뉴(overlay:18) — 사실상 전부다.

라이트 `rgba(31,57,105,0.16)`을 `--color-canvas`(#f3f7ff) 위에 합성하면 실효 색은 약 `#d4dceb`, 대비 약 **1.27:1**이다. WCAG 1.4.11(비텍스트 대비) 3:1 기준을 크게 밑돈다. 다크는 더 낮다.

**증상.** 저시력·고령 사용자에게 카드와 표의 행 구분이 사실상 보이지 않는다. 대비 강화를 켠 사용자에게 앱이 아무 반응도 하지 않는다.

**왜 안 잡혔나.** axe의 `color-contrast` 규칙은 **텍스트 대비만** 본다. 1.4.11 비텍스트 대비는 자동 검사 대상이 아니다. `npm run test:e2e:a11y`가 통과하는 것과 이 문제는 무관하다.

**적용.** RSP-15와 마찬가지로 **토큰 4개 재선언으로 전 화면이 개선된다.**

```css
/* shared/styles/tokens.css */

/* RSP-16(docs/improvement_claude.md): --color-border는 라이트에서 캔버스 위 실효
   대비가 약 1.27:1로, WCAG 1.4.11(비텍스트 3:1)에 크게 못 미친다. 기본값을 올리면
   전체 디자인 인상이 바뀌므로, 대비 강화를 명시적으로 요청한 사용자에게만 올린다.
   경계선을 쓰는 컴포넌트가 10곳 이상이지만 이 파일 한 곳만 고치면 전부 따라온다
   (별칭 레이어를 없앤 §1.4 G-3 설계의 직접적 이득). */
@media (prefers-contrast: more) {
  :root {
    --color-border: rgba(31, 57, 105, 0.55);
    --color-ink-muted: #3a4761;
    --color-ink-subtle: #5a6a80;
    --color-focus-ring: rgba(0, 90, 120, 0.9);
  }

  [data-theme="dark"] {
    --color-border: rgba(255, 255, 255, 0.45);
    --color-ink-muted: #c3cde4;
    --color-ink-subtle: #96a3bd;
    --color-focus-ring: rgba(0, 229, 255, 0.95);
  }
}
```

**검증.** DevTools Rendering → "Emulate CSS media feature prefers-contrast: more". 확인 라우트는 표가 가장 많은 `/stats`, 카드가 가장 많은 `/data`. 새 값들의 대비 수치를 계산해 기존 주석 관행대로 `/* 대비 N:1 */`을 붙인다.

---

### RSP-17 — `prefers-reduced-motion` 패턴 불일치 (확인 완료·경미)

**근거.** `base.css:52-60`의 전역 블록이 `*, *::before, *::after`에 `animation-duration: 0.01ms !important`, `animation-iteration-count: 1 !important`, `transition-duration: 0.01ms !important`를 건다. 이것으로 코드베이스의 모든 애니메이션이 덮인다.

| 애니메이션 | 위치 | 전역 블록으로 덮이나 |
|---|---|---|
| `matchPulse` (무한) | `lotto-ball.module.css:105-113` | 예 |
| `baselinePulse` (무한) | `frequency-bar.module.css` | 예 |
| `pulse` (스켈레톤, 무한) | `feedback.module.css` | 예 |
| `spin` (스피너, 무한) | `button.module.css` | 예 |
| `resultEnter` (backwards) | `studio.module.css` | 예 — 즉시 완료 상태로 렌더 |
| `confettiFall` (forwards) | `winning-celebration.module.css` | 예 |
| `togglePop` | `button.module.css` | 예 |
| `commentHighlight` | `comment-thread.module.css:307-316` | 예 |

그런데 `comment-thread.module.css:318-323`만 **개별 `@media (prefers-reduced-motion: reduce)` 블록을 추가로 갖는다.**

**판정: 결함 아님. 오히려 이 개별 블록이 옳다.** 전역 블록은 애니메이션을 0.01ms로 끝낼 뿐이므로 `commentHighlight`의 **종료 상태**(`background: var(--color-surface-1); border-color: var(--color-border)` — 즉 강조 없음)로 즉시 착지한다. 그러면 "방금 쓴 댓글을 강조한다"는 UX-03의 목적이 모션 감소 사용자에게만 사라진다. 개별 블록은 `border-color: var(--color-brand)`를 정적으로 남겨 **정보를 보존한다.**

**조치.** 없음. 다만 이 구분(애니메이션이 정보를 전달하는가, 순수 장식인가)이 코드에 명시돼 있지 않으므로 전역 블록에 규칙을 한 줄 남길 것을 권고한다.

```css
/* shared/styles/base.css — 기존 블록에 주석만 추가 */
/* RSP-17(docs/improvement_claude.md): 이 전역 블록은 애니메이션을 "즉시 종료
   상태로" 만든다 — 없애는 것이 아니다. 따라서 종료 상태가 정보를 잃는
   애니메이션(예: comment-thread.module.css의 .highlighted — 강조가 사라진
   상태로 착지한다)은 개별 @media 블록으로 정적 대체 표현을 따로 줘야 한다. */
@media (prefers-reduced-motion: reduce) { /* … */ }
```

---

## §D 반응형 성능

### RSP-18 — sticky 헤더의 backdrop blur (핵심)

**근거.** `shell.module.css:7-18` — `.header`가 `position: sticky; top: 0` + `backdrop-filter: blur(12px)`.

이 조합은 **스크롤하는 모든 프레임에서 헤더 뒤 영역을 다시 샘플링하고 블러를 재계산**하게 만든다. 컴포지터가 아니라 페인트 단계 비용이며 모바일 GPU에서 특히 비싸다. 헤더 폭이 화면 전체라 샘플링 면적도 최대다.

**왜 예산 체계가 못 잡나.** `scripts/lighthouse-budget.mjs`는 `formFactor: "mobile"`, `screenEmulation: { mobile: true, width: 412, … }`로 재지만 **`throttlingMethod: "provided"`** 다(주석이 그 이유를 명시 — localhost에서 simulated 스로틀이 비현실적이라). `provided`는 CPU 스로틀링도 끄므로 개발 머신 성능으로 잰다. 게다가 Lighthouse의 CLS/TBT는 **로드 시점** 지표라 스크롤 중 자터는 애초에 측정 대상이 아니다. 즉 이 비용은 **어떤 게이트에도 걸리지 않는다.**

**증상.** 저사양~중급 안드로이드에서 `/stats`나 `/frequency`처럼 긴 페이지를 스크롤할 때 헤더 근처 프레임 드롭. 실사용자 데이터(`/api/vitals`)에 INP로도 잡히지 않는다 — 입력 응답이 아니라 스크롤 부드러움의 문제이기 때문이다.

**적용 — 안 1 (권고): 모바일에서 블러를 끈다.** 데스크톱 nav 전환과 같은 1152px 경계를 쓰면 계약이 하나 더 늘지 않는다.

```css
/* app/_shell/shell.module.css */
@layer components {
  .header {
    position: sticky;
    top: 0;
    z-index: var(--z-header);
    min-height: var(--layout-header-h);
    display: flex;
    align-items: center;
    /* RSP-18(docs/improvement_claude.md): sticky + backdrop-filter는 스크롤하는
       모든 프레임에서 헤더 뒤 영역을 재샘플링·재블러한다. 모바일 GPU에서 가장
       비싸고, lighthouse-budget.mjs가 throttlingMethod:"provided"(무스로틀)로
       재기 때문에 어떤 게이트에도 걸리지 않는다. 기본은 불투명으로 두고,
       탭바가 사라지는 것과 같은 1152px 경계에서만 켠다(새 브레이크포인트를
       만들지 않는다 — KF-02 참고). */
    background: var(--color-surface-solid);
    border-bottom: 1px solid var(--color-border);
  }

  @media (min-width: 1152px) {
    .header {
      background: var(--color-surface-3);
      -webkit-backdrop-filter: blur(12px);
      backdrop-filter: blur(12px);
    }
  }
}
```

**안 2 — 블러 반경만 줄인다.** `blur(12px)` → `blur(6px)`. 샘플링 비용이 반경에 대체로 비례하므로 절반 가까이 준다. 시각적 인상은 거의 유지되고 위험이 가장 낮다.

**안 3 — 유지하고 측정만 한다.** 아래 절차로 실측한 뒤 판단한다.

**주의.** `.header` 배경을 불투명으로 바꾸면 라이트 테마에서 `--color-surface-solid`(#ffffff)와 `--color-canvas`(#f3f7ff)가 매우 가깝다. `border-bottom`이 구분을 담당하므로 **RSP-16과 함께 보면 좋다.** 다크는 `#111936` vs `#050816`이라 충분히 구분된다.

**검증 (실측 절차).**

```
1. npm run build && npm start
2. Chrome DevTools -> Performance -> CPU: 4x slowdown
3. /stats 를 열고 Record -> 페이지 끝까지 스크롤 -> Stop
4. Main 트랙의 Paint / Composite Layers 합계와 dropped frames 확인
5. shell.module.css의 backdrop-filter 두 줄을 주석 처리하고 3~4를 반복
6. 차이가 프레임당 2ms 미만이면 안 3(유지), 그 이상이면 안 1
```

---

### RSP-19 — 타입 스케일 clamp의 실익 재검토

**근거.** `tokens.css:89-94`의 실제 변동폭(루트 16px 기준):

| 토큰 | @320px (하한 적용) | @1440px | 변동 |
|---|---|---|---|
| `--text-xs` | 12.16px | 13.12px | **0.96px** |
| `--text-sm` | 14.08px | 15.04px | **0.96px** |
| `--text-base` | 14.72px | 15.68px | **0.96px** |
| `--text-md` | 16.8px | 17.92px | **1.12px** |
| `--text-lg` | 19.2px | 21.6px | 2.4px |
| `--text-2xl` | 28px | 35.2px | **7.2px** |

즉 본문 4개 스텝은 전 뷰포트 구간에서 **약 1px** 움직인다. 사용자가 지각하기 어려운 차이다. 반면 `vw` 단위는 뷰포트가 바뀔 때마다(모바일 주소창 접힘/펼침 포함) 재계산과 리플로우를 유발한다.

**증상.** 실질 성능 영향은 미미하다. 실제 문제는 **복잡도 대비 이득**이다 — 6개 토큰이 3항 `clamp()`를 쓰는데 그중 4개는 사실상 상수다.

**적용 — 부분 단순화.** 본문 4개는 고정값으로, 시각적 위계가 실제로 움직이는 2개만 `clamp()`를 남긴다.

```css
/* shared/styles/tokens.css */
  /* RSP-19(docs/improvement_claude.md): --text-xs~md는 320~1440px 전 구간에서
     변동폭이 약 1px이라 clamp()의 이득이 없다 — 지각하기 어려운 차이를 위해
     뷰포트 변화마다(모바일 주소창 접힘 포함) 재계산을 유발한다. 실제로 위계가
     움직이는 --text-lg / --text-2xl만 유동으로 남긴다. */
  --text-xs: 0.78rem;
  --text-sm: 0.9rem;
  --text-base: 0.94rem;
  --text-md: 1.08rem;
  --text-lg: clamp(1.2rem, 1.1rem + 0.5vw, 1.35rem);
  --text-2xl: clamp(1.75rem, 1.5rem + 1.25vw, 2.2rem);
```

**반론(기록용).** 순수 정리 작업이고 사용자 이득이 0에 가깝다. 시각 baseline이 전 라우트에서 미세하게 바뀌어 재생성이 필요하다. **비용이 이득보다 크므로 우선순위 최하** — 다른 이유로 `tokens.css`를 크게 손볼 때 함께 하는 것을 권고한다.

**검증.** `assertFormControlFontSizeAtLeast16px`가 통과해야 한다. `--font-size-input: max(16px, var(--text-base))`(tokens:100)이 하한을 보장하므로 안전하지만 확인은 필요하다.

---

### RSP-20 — `--fixed-bottom-inset` 전역 재기입의 리레이아웃 범위

**근거.** `ad-unit.tsx:286-295`가 `document.documentElement.style.setProperty("--fixed-bottom-inset", …)`로 값을 바꾼다. 이 프로퍼티를 읽는 곳은 셋이다.

- `shell.module.css:107-109` `.main { padding-bottom: calc(… + var(--fixed-bottom-inset, 0px)) }`
- `shell.module.css:119-122` `.footer { padding-bottom: calc(… + var(--fixed-bottom-inset, 0px)) }`
- `overlay.module.css:78` `.toastRegion { bottom: calc(… + var(--fixed-bottom-inset, 0px)) }`

`:root`의 커스텀 프로퍼티가 바뀌면 브라우저는 그 프로퍼티를 참조하는 모든 요소의 스타일을 무효화한다. `.main`은 페이지 전체를 감싸는 요소이므로 그 `padding-bottom` 변경은 문서 레이아웃 재계산을 부른다.

`visible`이 바뀌는 조건(`ad-unit.tsx:285`)은 다섯이다 — `isDesktop`, `isShortViewport`, `closed`, `keyboardOpen`, `unit`. 이 중 `keyboardOpen`이 **`/community/write`에서 입력창을 탭할 때마다, 그리고 닫을 때마다** 토글된다.

**증상.** 커뮤니티 글쓰기에서 입력 시작/종료 시 페이지 하단이 51px 늘었다 줄었다 한다. 문서 높이가 바뀌므로 스크롤 위치가 상대적으로 이동할 수 있다 — 특히 페이지 하단 근처에서 입력 중일 때 체감된다.

**왜 안 잡히나.** Lighthouse CLS는 사용자 입력 직후 500ms 안의 이동을 "최근 입력 예외"로 제외한다. 정당한 제외지만 그렇다고 사용자가 안 느끼는 건 아니다.

**적용.** 계약(광고가 게시하고 셸이 소비)은 유지한다. 대신 **키보드가 열릴 때 값을 0으로 되돌리지 않는다** — 광고는 숨기되 예약 공간은 그대로 둔다.

```tsx
// shared/ui/ad-unit.tsx
export function StickyMobileAd({ unit }: { unit: string }) {
  const [closed, setClosed] = useState(false);
  const isDesktop = useMediaQuery(DESKTOP_QUERY);
  const isShortViewport = useMediaQuery(SHORT_VIEWPORT_QUERY);
  const keyboardOpen = useKeyboardOpen();

  // RSP-20(docs/improvement_claude.md): --fixed-bottom-inset이 바뀔 때마다
  // .main/.footer/.toastRegion 세 곳이 무효화되고 문서 높이가 51px 움직인다.
  // keyboardOpen은 /community/write에서 입력을 탭할 때마다 토글되므로 그 비용이
  // 가장 자주 발생한다 — 광고는 내리되(겹침 방지가 목적) 예약 공간은 유지해
  // 문서 높이가 흔들리지 않게 한다. slotReserved와 visible을 분리한다.
  const slotReserved = !isDesktop && !isShortViewport && !closed && Boolean(unit);
  const visible = slotReserved && !keyboardOpen;

  useEffect(() => {
    document.documentElement.style.setProperty(
      "--fixed-bottom-inset",
      slotReserved ? STICKY_AD_INSET_PX : "0px",
    );
    return () => {
      document.documentElement.style.removeProperty("--fixed-bottom-inset");
    };
  }, [slotReserved]);

  if (!visible) return null;
  // … 이하 동일 …
}
```

**주의 — 계약 의미가 바뀐다.** `--fixed-bottom-inset`이 "광고가 실제로 보이는 동안만 51px"에서 "광고 슬롯이 예약된 동안 51px"로 바뀌므로, **`ad-unit.tsx:262-268`과 `shell.module.css:97-106`, `overlay.module.css:76-77`의 주석 세 곳을 함께 갱신해야 한다.** 이 문서의 §7 표에도 같은 내용이 있다.

**검증.** `npm run test:e2e:responsive`. 그리고 실기기(iOS Safari / Android Chrome)에서 `/community/write`의 제목 입력을 탭했을 때 페이지가 튀지 않는지 확인 — 이것만은 에뮬레이션으로 재현되지 않는다.

---

### RSP-21 — CLS 관점 확인 (확인 완료·조치 불필요)

향후 재조사를 막기 위해 **문제가 아닌 것**을 명시적으로 기록한다.

| 대상 | 검토 | 판정 |
|---|---|---|
| `AdSenseSidebar` (`ad-unit.tsx:229-259`) | CSS 미디어쿼리로 `.adSidebarSlot { min-height: 600px }`를 ≥1024px에서 먼저 예약하고(KF-07), `adUnitRendersContent()`로 실제 렌더될 때만 그 클래스를 붙인다 | **올바름** — 하이드레이션 시프트 없음 |
| `StickyMobileAd` mount | `--fixed-bottom-inset`을 51px로 올려 `.main`/`.footer`의 `padding-bottom`만 바꾼다 | **CLS 대상 아님** — 문서 하단 여백 변화는 뷰포트 안에서 요소를 움직이지 않는다. 단 RSP-20의 잦은 재기입은 별개 문제 |
| `themeTogglePlaceholder` (`shell.module.css:55-59`) | 마운트 전 `--target-min`만큼 자리 예약 (I-33) | **올바름** |
| `accountControlPlaceholder` (`shell.module.css:64-68`) | 동일 패턴 (KF-25①) | **올바름** — 주석이 "최종 라벨 너비까지는 못 맞춘다"는 한계도 명시 |
| `.saveStatus` (`studio.module.css`) | `min-height: 1.5rem`으로 배지 자리 선점 (I-29) | **올바름** |
| `PageSkeleton` | 세션 게이트 워터폴에 적용 (KF-08) → `/saved`·`/recommend/history` CLS 0.117 → 0.0006 | **올바름** — `lighthouse-budget.mjs`의 `PENDING_CLS_ROOT_CAUSE`가 빈 집합인 근거 |

**남은 CLS 위험은 RSP-22 하나뿐이다.**

---

### RSP-22 — 폰트 폴백 메트릭 미지정

**근거.** `app/layout.tsx:32-47`의 `localFont` 호출에 **`fallback`도 `adjustFontFallback`도 없다.**

`next/font/local`은 `next/font/google`과 달리 자동 폴백 메트릭 조정을 하지 않는다 — 원본 폰트의 메트릭을 알 수 없으므로 `size-adjust`/`ascent-override`가 담긴 폴백 `@font-face`를 생성하지 않는다.

`--font-sans: var(--font-noto-sans-kr), system-ui, sans-serif`(tokens:87)이므로 폰트 도착 전에는 `system-ui`가 그린다. Windows의 `system-ui`(Segoe UI)와 Noto Sans KR은 x-height·어센더가 다르고, **한글 글리프는 Segoe UI에 없어 맑은 고딕으로 다시 폴백**되므로 차이가 더 커진다.

주석(layout.tsx:22-31)이 밝힌 대로 한글 서브셋을 KS X 1001 2,350자로 좁혀 **536KB**까지 줄였고 preload를 켰다. 그래도 느린 회선에서는 도착까지 수백 ms가 걸리고 `display: "swap"`이므로 그동안 폴백으로 그린 뒤 교체된다 — **그 교체 순간이 리플로우다.**

**증상.** 느린 회선에서 첫 화면이 한 번 출렁인다. 텍스트가 많은 `/info/[slug]`, `/stats`에서 가장 크다. CLS에 계상된다.

**왜 안 잡히나.** `lighthouse-budget.mjs`가 `throttlingMethod: "provided"` — localhost 무스로틀이라 폰트가 사실상 즉시 도착한다. **폰트 스왑 시프트를 구조적으로 측정할 수 없는 설정**이다. RSP-18과 같은 사각지대다.

**적용.**

```tsx
// app/layout.tsx
/*
 * RSP-22(docs/improvement_claude.md): next/font/local은 next/font/google과 달리
 * 폴백 메트릭을 자동 조정하지 않는다 — size-adjust/ascent-override가 없으면
 * display:"swap"으로 폰트가 도착하는 순간 폴백(system-ui)과의 메트릭 차이만큼
 * 리플로우가 난다. 536KB 서브셋이라 느린 회선에서 그 창이 수백 ms다.
 * lighthouse-budget.mjs는 throttlingMethod:"provided"(무스로틀 localhost)라
 * 이 시프트를 구조적으로 측정하지 못한다(RSP-18과 같은 사각지대).
 */
const notoSansKr = localFont({
  src: [
    { path: "../../public/fonts/noto-sans-kr-400.woff2", weight: "400" },
    { path: "../../public/fonts/noto-sans-kr-700.woff2", weight: "700" },
  ],
  display: "swap",
  variable: "--font-noto-sans-kr",
  preload: true,
  fallback: ["system-ui", "sans-serif"],
  adjustFontFallback: "Arial",
});
```

`adjustFontFallback`은 `next/font/local`에서 `"Arial"` / `"Times New Roman"` / `false`만 받으므로, 한글 폴백 메트릭까지 정확히 맞추려면 수동 `@font-face`가 필요하다.

```css
/* shared/styles/base.css — 수동 폴백이 필요할 경우.
   아래 override 값은 자리표시자다. 실제 값은 Noto Sans KR과 폴백 폰트의
   OS/2 테이블(sxHeight / sTypoAscender / unitsPerEm)을 비교해 유도한다
   (fonttools ttx 또는 capsize). 근거 없는 값을 그대로 넣으면 오히려
   리플로우를 키운다. */
@font-face {
  font-family: "Noto Sans KR Fallback";
  src: local("Malgun Gothic"), local("Apple SD Gothic Neo");
  size-adjust: 100%;
  ascent-override: 88%;
  descent-override: 22%;
  line-gap-override: 0%;
}
```

```css
/* shared/styles/tokens.css */
--font-sans: var(--font-noto-sans-kr), "Noto Sans KR Fallback", system-ui, sans-serif;
```

**검증.** DevTools Network에서 폰트만 "Slow 3G"로 스로틀하고 `/info/methodology`를 새로고침 → Performance Insights의 Layout shift 항목에 폰트 스왑이 잡히는지 확인. 수정 후 그 shift score가 줄어야 한다. `lighthouse-budget.mjs`로는 잡히지 않으므로 **수동 확인이 유일한 방법**임을 기록해 둔다.

---

## §6 우선순위

### 6.1 종합 표

| ID | 항목 | 사용자 영향 | 구현 비용 | 회귀 위험 | 권장 |
|---|---|---|---|---|---|
| **RSP-10** | responsive 트랙에 터치 포인터 없음 | — (검증 기반) | 낮음 (설정 5줄) | 낮음 | **1순위** |
| **RSP-12** | 압착 단언 부재 | — (재발 방지) | 중간 (단언 2개 + 스펙) | 없음 | **1순위 (RSP-10과 같은 PR)** |
| **RSP-01** | `/saved` 중첩 브레이크포인트 압착 | 높음 (640~1023px) | 중간 (`@container` 도입) | 중간 (Firefox 확인) | **2순위** |
| **RSP-03** | `.itemHeader` `flex-wrap` 누락 | 중간 (RSP-01 동반) | 매우 낮음 (1줄) | 없음 | **2순위 (동반)** |
| **RSP-04** | `minmax()` 하한 안전판 | 없음 (예방) | 매우 낮음 | 없음 | **2순위 (동반)** |
| **RSP-02** | 640px 2열 패턴 복제 | 없음 | 중간 | 중간 | 2순위 동반 (단독 비권장) |
| **RSP-18** | sticky 헤더 backdrop blur | 중간 (저사양 스크롤) | 낮음 | 낮음 (다크 대비 확인) | **3순위** |
| **RSP-15** | `prefers-reduced-transparency` | 중간 (해당 사용자) | 낮음 (RSP-18과 같은 코드) | 낮음 | **3순위 (동반)** |
| **RSP-16** | `prefers-contrast: more` | 높음 (저시력) | 매우 낮음 (토큰 4개) | 없음 (기본 경로 불변) | **3순위 (동반)** |
| **RSP-22** | 폰트 폴백 메트릭 | 중간 (느린 회선 CLS) | 중간 (메트릭 실측 선행) | 낮음 | 4순위 |
| **RSP-11** | hit area 뷰포트 커버리지 | — (검증 강화) | 낮음 | 없음 (CI 시간 3배 주의) | 4순위 |
| **RSP-14** | `forced-colors` | 낮음 (식별성만) | 중간 (4개 파일) | 낮음 | 5순위 |
| **RSP-09** | 가로모드·키보드 다이얼로그 | 낮음 | 중간 | 중간 | 5순위 |
| **RSP-20** | `--fixed-bottom-inset` 재기입 | 낮음 (글쓰기 중 튐) | 중간 (계약 의미 변경) | **높음** (주석 3곳 동반) | 6순위 |
| **RSP-05** | 고정 gutter | 낮음 | 낮음 | **높음** (전 라우트 baseline 재생성) | 6순위 |
| **RSP-08** | 탭 개수 5 하드코딩 | 없음 (잠재) | 낮음 | 낮음 | 주석 방어만 즉시 |
| **RSP-07** | 타입 스케일 컨테이너 | 없음 | 낮음 | 낮음 | RSP-01 이후 재평가 |
| **RSP-19** | 타입 clamp 단순화 | 없음 | 낮음 | 중간 (baseline) | 최하 |
| RSP-06 | `100vw` 스크롤바 오차 | 없음 | — | — | **조치 불필요** |
| RSP-13 | firefox 스코프 | — | — | — | **조치 불필요** (조건 기록) |
| RSP-17 | reduced-motion 패턴 | 없음 | — | — | **조치 불필요** (주석만) |
| RSP-21 | CLS 재검토 | 없음 | — | — | **조치 불필요** (확인 완료) |

### 6.2 권장 실행 순서

**PR 1 — 검증 기반 (프로덕션 코드 변경 없음, 테스트만)**
`RSP-10`(mobile-chromium 프로젝트) + `RSP-12`(단언 2개 + `container-squeeze.spec.ts`).
이 PR은 **red로 끝나는 것이 정상이다.** 지금까지 감춰져 있던 결함이 처음 드러나는 지점이다. red 목록 자체가 다음 PR의 범위를 정의한다.

**PR 2 — `/saved` 압착 수정**
`RSP-01`(`@container`) + `RSP-03`(`flex-wrap`) + `RSP-04`(`minmax` 안전판) + `RSP-02`(같은 파일을 건드리는 김에).
PR 1의 `container-squeeze.spec.ts`가 green이 되어야 한다. `RSP-13`의 조건에 해당하므로 firefox 스코프에 `container-squeeze`를 추가한다.

**PR 3 — 표면·대비 (성능 + 접근성 동시)**
`RSP-18`(모바일 blur 제거) + `RSP-15`(`prefers-reduced-transparency`) + `RSP-16`(`prefers-contrast`).
셋이 `tokens.css`의 표면·경계 토큰이라는 같은 지점을 건드린다. `npm run test:e2e:a11y`로 axe color-contrast 재확인 필수.

**PR 4 이후** — `RSP-22`(메트릭 실측 선행), `RSP-11`(CI 시간 측정 선행), `RSP-14`, `RSP-09`, `RSP-20`, `RSP-05`.

---

## §7 적용 시 함께 갱신해야 하는 계약

구현 중 **한쪽만 바꾸면 조용히 어긋나는** 짝들이다.

| 바꾸는 것 | 반드시 함께 | 근거 |
|---|---|---|
| `shell.module.css`의 `.primaryNav`/`.tabBar` 1152px | `tokens.css:159`의 `--tabbar-reserve` 미디어쿼리, `breakpoints.ts`의 `BP.desktopNav` | KF-02·KF-06 |
| `STICKY_AD_INSET_PX`(ad-unit.tsx:269) | `.adStickyMobile .adUnit`의 `min-height: 50px` + `border-top: 1px` | ad-unit.tsx:262-268 주석 |
| `--fixed-bottom-inset` 의미 (RSP-20) | `shell.module.css:97-106`, `ad-unit.tsx:262-268`, `overlay.module.css:76-77` 세 주석 | 본 문서 RSP-20 |
| `TAB_BAR_ITEMS` 길이 | `shell.module.css:180`의 `calc(100%/5)`, `:191-210`의 nth 5개 | 본 문서 RSP-08 |
| `--layout-gutter` (RSP-05) | `document-overflow.spec.ts`의 320px 기대, visual baseline 전체, RSP-01의 폭 계산 | 본 문서 RSP-05 |
| `--text-*` (RSP-19) | `--font-size-input`/`--font-size-input-lg`(tokens:100-101), visual baseline | tokens.css:97-101 |
| 표면 토큰 불투명화 (RSP-15) | `home.module.css:45-49`의 `@supports` 폴백 — 두 경로가 같은 결과를 내야 한다 | 본 문서 RSP-15 |
| `@container` 도입 (RSP-01) | `playwright.responsive.config.ts`의 firefox 스코프 | 본 문서 RSP-13 |
| 새 responsive 프로젝트 (RSP-10) | CI 워크플로의 트랙 실행 시간 예산 | `playwright.responsive.config.ts:26-32` 주석의 비용 논리 |

---

## 부록 A — 진단에 쓴 조회

재현 가능하도록 기록한다(`web/`에서 실행).

```bash
# 미디어쿼리 전수
grep -rn "@media" src --include=*.css

# 반응형 원시 기능 사용 여부
grep -rn "@container\|container-type" src                                      # 0건
grep -rn "forced-colors\|prefers-contrast\|prefers-reduced-transparency" src   # 0건
grep -rn "dvh\|svh\|lvh" src --include=*.css                                   # 4건
grep -rn "safe-area" src                                                       # 9건

# hover 가드 블록 (RSP-10)
grep -rn "hover: hover" src --include=*.css | wc -l                            # 12

# space-between + flex-wrap 부재 (RSP-03)
grep -rn -B4 "justify-content: space-between" src --include=*.css

# 고정 열 수 그리드 (RSP-01)
grep -rn "repeat([0-9]" src --include=*.css

# 뷰포트 단위 클램프 (RSP-06)
grep -rn "100vw\|100dvw" src --include=*.css

# 테스트 디바이스 프로젝트 (RSP-10)
grep -rn "devices\[" *.config.ts
```

## 부록 B — 확인했고 문제가 없던 것

같은 곳을 다시 파지 않도록 남긴다.

- **`.tableWrap`의 가로 스크롤 힌트** (`surface.module.css:41-57`) — `local`/`scroll` 배경 첨부로 스크롤 가능 여부를 시각화하고, `overscroll-behavior-x: contain`으로 브라우저 뒤로가기 제스처를 차단한다. 스크린리더 중복을 피하려 콘텐츠 없이 배경만 쓴 것까지 의도적이다.
- **`--font-size-input`** (`tokens.css:100-101`) — `max(16px, …)`이라 뷰포트와 무관하게 iOS 자동 확대를 막는다. `field.module.css`, `library.module.css`, `community.module.css` 세 소비처가 모두 이 토큰을 쓴다. `maximum-scale`로 확대 자체를 막지 않은 것(WCAG 1.4.4)도 올바르다.
- **`.tabLabel` 말줄임** (`shell.module.css:231-236`) — 라벨 래핑이 탭 높이를 늘려 `--tabbar-reserve` 상수와 어긋나는 것을 막는다(F-18). 400% 확대에서도 안전.
- **`.tabLink`의 `min-width: 0`** (`shell.module.css:219`) — 5칸 고정 그리드에서 grid item 기본 min-width가 콘텐츠 폭이라 확대 시 탭바가 밀리는 문제를 이미 막아 뒀다.
- **광고와 탭바의 z 순서·위치** (`ad-unit.module.css:40-57`) — 광고가 `--tabbar-reserve`만큼 떠서 탭바 "위에" 쌓이고, 탭바가 이미 `env(safe-area-inset-bottom)`을 흡수하므로 광고는 중복으로 더하지 않는다(KF-06).
- **광고 데스크톱 경계 1024 vs 탭바 1152** — 광고가 **더 일찍** 사라지므로 "탭바는 없는데 광고가 그 자리에 뜨는" 조합은 생기지 않는다. `breakpoints.ts:11-18` 주석이 두 값이 다른 이유를 명시한다.
- **`reset.css`의 한글 줄바꿈** — `word-break: keep-all; overflow-wrap: anywhere`를 `:where()`로 걸어 특이성 0을 유지한다. 어절 단위 줄바꿈과 긴 URL 강제 분리를 동시에 만족한다.
- **`base.css:8-10`의 `scroll-padding-top`** — sticky 헤더 높이만큼 앵커 도착 위치를 앞당긴다. `/stats`의 목차(`stats.module.css:11-16`)와 skip-nav가 이것에 의존한다.
- **`.excluded`·`.locked`의 비색상 중복 부호** — 상태를 색만으로 전달하지 않는다는 규칙이 `aria-label` + ★/✕ 뱃지 + `line-through`로 일관되게 지켜져 있다. RSP-14의 영향이 "정보 손실"이 아니라 "식별성 저하"에 그치는 직접적 이유다.
- **`.adStickyMobileClose::before { inset: -12px }`** — 시각적 크기(작은 ✕)는 유지하고 히트 영역만 넓히는 올바른 패턴. 다만 `getBoundingClientRect()` 기반 단언과의 상호작용은 RSP-11에서 확인 대상으로 남겼다.
