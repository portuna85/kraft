# 01. 프로젝트 개요

## 1.1 서비스 정의

`kraft`는 회원 가입/로그인 후 게시글을 작성·조회·수정·삭제하는 **게시판 웹 서비스**입니다.
소스 코드의 주석(`User.java`, `Comment.java`)에서 다음 기획 의도가 확인됩니다.

- 회원 정보 변경은 **비밀번호 변경만** 허용한다.
- 회원가입 직후 권한은 `GUEST`, **이메일 인증 후** `USER`로 승격한다.
- `USER`는 `Post`와 `Comment`의 작성·수정·삭제가 가능하다.
- **모든 사용자**(비로그인 포함)는 `Post` 조회가 가능하다.
- 댓글(`Comment`) 기능은 **2단계 과제**로 분류되어 있다.

`src/main/resources/templates/index.html`의 문구("스프링부트로 시작하는 웹 서비스 Ver.2")로 미루어,
해당 도서의 예제 게시판을 Spring Boot 4 / Java 25 환경으로 재구성하는 학습·실습 프로젝트로 보입니다.

## 1.2 기술 스택

| 구분 | 기술 | 버전 |
| --- | --- | --- |
| 언어 | Java | 25 (toolchain) |
| 프레임워크 | Spring Boot | 4.1.1 |
| 빌드 | Gradle (Kotlin DSL) | 9.7.1 (wrapper) |
| 웹 | Spring MVC (`spring-boot-starter-webmvc`) | - |
| 뷰 | Thymeleaf + `thymeleaf-extras-springsecurity6` | - |
| 영속성 | Spring Data JPA (Hibernate) | - |
| 보안 | Spring Security | - |
| 세션 | Spring Session JDBC | - |
| DB | H2 (개발), MariaDB (운영 예정) | - |
| 검증 | Jakarta Bean Validation | - |
| 보일러플레이트 | Lombok | - |

## 1.3 패키지 구조

```
com.kraft
├── KraftApplication.java          # @SpringBootApplication 진입점
├── domain                         # 영속 계층 (엔티티 + 리포지토리)
│   ├── BaseEntity.java            # 생성/수정 시각 공통 필드 (@MappedSuperclass)
│   ├── comment
│   │   └── Comment.java           # 댓글 (2단계, 미완성)
│   ├── post
│   │   ├── Post.java              # 게시글 엔티티
│   │   └── PostRepository.java    # JpaRepository + findAllDesc()
│   └── user
│       ├── Role.java              # 권한 enum (ADMIN/USER/GUEST)
│       ├── User.java              # 회원 엔티티
│       └── UserRepository.java    # JpaRepository
├── service                        # 비즈니스 계층
│   ├── post/PostService.java      # (비어 있음)
│   └── user/UserService.java      # (비어 있음)
└── web                            # 프레젠테이션 계층
    ├── IndexController.java       # 화면 컨트롤러 (비어 있음)
    ├── api
    │   ├── PostApiController.java # REST 컨트롤러 (파일명/클래스명 불일치)
    │   └── UserApiController.java # (비어 있음)
    └── dto/post                   # 요청/응답 DTO (모두 빈 클래스)
        ├── PostSaveRequestDto.java
        ├── PostUpdateRequestDto.java
        ├── PostResponseDto.java
        └── PostsListResponseDto.java
```

`web/dto/user` 디렉터리는 생성되어 있으나 **파일이 없습니다.**

## 1.4 리소스 구조

```
src/main/resources
├── application.yml                # 애플리케이션 설정
├── static/js/app/index.js         # jQuery 기반 게시글 CRUD AJAX
└── templates
    ├── index.html                 # 게시글 목록 + 로그인 영역
    ├── layout/header.html         # <head> ~ <body> 시작 (Bootstrap 4.3.1 CDN)
    ├── layout/footer.html         # jQuery 3.3.1 + Bootstrap JS + index.js
    ├── post/post-save.html        # 게시글 등록 폼
    └── post/post-update.html      # 게시글 수정 폼
```

> 템플릿 파일은 **Mustache 문법**(`{{>layout/header}}`, `{{#posts}}`)으로 작성되어 있으나
> 의존성에는 Thymeleaf만 존재합니다. 자세한 내용은 [06. 뷰와 템플릿](06-view-and-templates.md) 참고.

## 1.5 완성도 현황

> **2026-09-09 업데이트**: 아래 표는 이 문서 작성 시점(구현 전) 상태다. 이후 P0(빌드 복구)+P1(실사용
> 가능한 상태) 구현이 완료되어 현재는 대부분 항목이 해결되었다. 실제 반영 내용과 검증 결과는
> [09. 구현 요약](09-implementation-summary.md) 참고.

| 계층 | 파일 | 분석 당시 상태 | 현재 상태 |
| --- | --- | --- | --- |
| 도메인 | `BaseEntity`, `Role`, `User`, `Post` | 필드 정의 완료 / 생성자·비즈니스 메서드 없음 | ✅ `@Builder` 생성자, 비즈니스 메서드(`update`, `changePassword`, `promoteToUser`), `User.email` 유니크 제약, `Post.@Getter`/`LAZY` 반영 완료 |
| 도메인 | `Comment` | 골격만 존재 (`id`, `content`) | 미해결 (P3, 2단계 범위) |
| 리포지토리 | `PostRepository`, `UserRepository` | 최소 구현 | ✅ `findAllDesc()` JOIN FETCH, `findByEmail`/`existsByEmail` 추가 |
| 서비스 | `PostService`, `UserService` | **미구현** (필드 주입만) | ✅ 전체 메서드 구현 |
| DTO | `Post*Dto` 4종 | **빈 클래스** | ✅ **record**로 구현(`SignUpRequestDto` 포함 총 5종). 근거는 [09장 3절](09-implementation-summary.md#3-dto를-record로-전환한-결정) 참고 |
| API | `PostsApiController` | 시그니처만 존재, 서비스 미구현으로 컴파일 실패 | ✅ `PostApiController`로 파일명 일치, 5개 엔드포인트 동작 확인 |
| API | `UserApiController` | **미구현** | ✅ 회원가입(`POST /api/v1/users`) 구현 |
| 화면 | `IndexController` | **미구현** | ✅ `/`, `/posts/save`, `/posts/update/{id}` 라우팅 구현 |
| 설정 | `SecurityConfig`, `JpaConfig` | **파일 자체가 없음** | ✅ `config/security/SecurityConfig.java`, `config/JpaConfig.java` 신설 |
