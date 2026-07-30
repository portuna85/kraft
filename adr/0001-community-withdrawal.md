# ADR 0001 — 커뮤니티 탈퇴 흐름

- **상태**: 승인됨
- **날짜**: 2026-07-30
- **관련**: `docs/improvement.md` KB-04

## 배경

`CommunityUser.withdrawnAt` 컬럼(V14 마이그레이션에서 도입)은 스키마와 getter만 있고 실제로
설정하는 코드가 전혀 없는 데드 컬럼이었다. README는 "탈퇴한 사용자의 게시물은 문맥을 보존한
상태로 삭제되는 tombstone 정책"을 이미 광고하고 있었지만, 뒷받침하는 구현이 없었다.

## 결정

### 1. 기존 게시글·댓글의 작성자 표기를 일괄 재작성한다

`CommunityPost`/`CommunityComment`는 작성자 닉네임을 `authorNameSnapshot`으로 매 글마다
스냅샷 저장한다(라이브 조인이 아님). 탈퇴 시점에 그 사용자가 쓴 모든 게시글·댓글의
`authorNameSnapshot`을 익명화된 값으로 일괄 UPDATE해, README가 광고하는 tombstone 정책을
실제로 이행한다. 게시글·댓글의 본문·구조 자체는 건드리지 않는다(기존 `hideByAuthor`/
`markDeleted`와 별개).

### 2. 재로그인은 자동 재활성화한다

`community_users`는 `(provider, provider_id)` unique 제약이 있어, 탈퇴한 계정이 같은 OAuth
계정으로 재로그인하면 새 행을 만들 수 없다(제약이 항상 기존 행으로 수렴시킨다). 이 구조적
사실을 받아들여, 재로그인 시 `withdrawn_at`을 지우고 OAuth 공급자가 이번에 내려준 최신
닉네임·프로필 이미지로 다시 채운다. 탈퇴 시 익명화된 옛 닉네임은 복구하지 않는다(애초에
저장돼 있지 않음).

### 3. 세션 무효화는 요청별 DB 재확인 필터로 구현한다

관리자 체인은 `SessionRegistry` + `maximumSessions(1)`로 동시 세션을 추적하지만, 커뮤니티
체인에는 이 인프라가 없다. 탈퇴 시점에 다른 기기의 세션까지 즉시 끊으려면 새 인프라
(`SessionRegistry` 도입)가 필요한데, 규모 대비 과한 방법이라 판단해 대신 매 요청마다
`CommunityWithdrawnAccountFilter`가 인증된 principal의 `withdrawn_at`을 DB에서 확인하고,
설정돼 있으면 그 자리에서 세션을 무효화하며 401을 응답하는 방식을 택했다. 탈퇴를 요청한
당사자의 세션은 같은 요청 안에서 즉시 무효화하고, 다른 기기의 세션은 다음 요청에서 걸러진다
— "즉시"가 아니라 "다음 요청부터"라는 차이가 있지만, 이 규모의 서비스에서는 충분하다.

### 4. 저장 번호·추천 이력 등 인접 데이터는 이번 범위 밖이다

`SavedNumbersService`·`RecommendationSetHistoryService`는 `ownerId`로 `CommunityUser`를
참조하지만 직접적인 코드 의존은 없다(식별자만 공유). 탈퇴 후에도 이 데이터는 그대로
남는다 — 별도 요청이 있을 때 처리한다(`docs/improvement.md` §5 "탈퇴 이후" 장기 항목).

## 결과

- `CommunityUser`에 `withdraw()`/`reactivate()` 뮤테이터 추가.
- `CommunityWithdrawalService.withdraw(userId)` — 계정 행 잠금 → 닉네임 익명화
  → 게시글·댓글 일괄 재작성. 이미 탈퇴한 계정은 멱등하게 무시.
- `CommunityOAuth2UserService.upsert()`에 재활성화 분기 추가.
- `CommunityWithdrawnAccountFilter` — `CommunitySecurityConfig`의 `AuthorizationFilter` 앞에
  등록, 매 요청마다 탈퇴 여부 재확인.
- `POST /api/v1/community/me/withdrawal` 엔드포인트 + `AccountMenu`에 확인 대화상자를 거치는
  탈퇴 버튼.
