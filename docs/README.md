# Kraft 프로젝트 문서

Spring Boot 4.1.1 / Java 25 기반의 게시판(블로그) 웹 서비스 `kraft`의 기술 문서입니다.

> 이 문서는 **현재 `build.gradle.kts`에 선언된 라이브러리만을 기준**으로 작성되었습니다.
> 새로운 의존성 추가를 전제로 한 설명은 포함하지 않으며, 부득이한 경우 명시적으로 표기합니다.

## 문서 목록

| 문서 | 내용 |
| --- | --- |
| [01. 프로젝트 개요](01-project-overview.md) | 서비스 목적, 전체 구조, 패키지 레이아웃 |
| [02. 빌드와 의존성](02-build-and-dependencies.md) | Gradle 설정, Spring Boot 4 모듈형 스타터 분석 |
| [03. 도메인 모델](03-domain-model.md) | 엔티티 상세 분석, 연관관계, ERD, DDL |
| [04. 아키텍처와 계층](04-architecture-and-layers.md) | 계층 구조, DTO 정책, 트랜잭션 경계 |
| [05. API 명세](05-api-spec.md) | REST 엔드포인트, 요청/응답 스펙 |
| [06. 뷰와 템플릿](06-view-and-templates.md) | 템플릿 엔진 불일치 문제와 Thymeleaf 전환 방안 |
| [07. 설정과 실행](07-configuration.md) | application.yml, DataSource, 세션, 보안 설정 |
| [08. 이슈와 TODO](08-issues-and-todo.md) | 문제 목록과 우선순위별 작업 계획(해결 현황 반영) |
| [09. 구현 요약](09-implementation-summary.md) | P0~P3 구현 완료 내용과 실제 검증(curl E2E) 결과 |

## 현재 상태 요약

**P0(빌드 복구) + P1(실사용 가능한 상태) + P2(설계·보안 개선) + P3(2단계 기능) 전 항목 구현이
완료되었습니다**(소셜 로그인/OAuth2만 사용자 결정으로 명시적으로 제외). `./gradlew compileJava`,
`./gradlew test`(91개 테스트)가 모두 통과하며, 회원가입 → 이메일 인증 → 로그인 → 게시글
등록/조회/수정/삭제(사진 첨부 포함) → 댓글 작성/삭제 → 비밀번호 변경까지 전 과정을 실제
`bootRun` + curl로 검증했습니다. 상세 변경 내역과 검증 결과는
[09. 구현 요약](09-implementation-summary.md)을 참고하세요.

`01~07` 문서는 구현 이전 분석을 기준으로 작성되었으며, 이후 해결된 항목은 각 문서에 해결 표시를
추가했습니다. 전체 이슈 해결 현황은 [08. 이슈와 TODO](08-issues-and-todo.md)를 참고하세요.

`docs/analysis.md`는 별도 세션이 작성한 구현 전 시점의 파일별 전수 분석 스냅샷입니다(문서 상단 배너 참고).
