# 게시판 (Java 21 + Spring Boot 3.3)

## 빠른 시작
```bash
./gradlew bootRun
# 브라우저: http://localhost:8080
# H2 콘솔: http://localhost:8080/h2-console (JDBC URL: jdbc:h2:mem:boarddb)
```

## MySQL로 실행
```bash
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

## 기본 기능
- 회원가입 / 로그인 (세션 기반, Spring Security)
- 게시글 CRUD + 페이징/검색
- Thymeleaf + Bootstrap UI
- H2(in-memory) 개발/테스트, MySQL 운영 준비
- TDD 샘플 테스트 포함


## 이미지/업로드 보안 & 최적화
- Magic Byte 검사(JPEG/PNG/GIF/WebP)
- 이미지 용량 제한 (기본 10MB)
- 썸네일 WebP 변환(300x300, Thumbnailator + TwelveMonkeys)
- 첨부 삭제: 작성자 또는 ADMIN만
