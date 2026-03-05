# Containerize Backend (Spring Boot)

Dockerfile Generator 백엔드 API 서버. 소스 코드를 분석하고 최적화된 Dockerfile을 자동 생성합니다.

## Tech Stack

- **Java 17** + **Spring Boot 3.3.5**
- **Gradle 8.5+** (Kotlin DSL)
- **Jinjava** (Jinja2 호환 템플릿 엔진)
- **SpringDoc OpenAPI** (Swagger UI)

## Quick Start

### 단독 실행

```bash
# Gradle Wrapper로 실행
./gradlew bootRun

# 또는 JAR 빌드 후 실행
./gradlew bootJar
java -jar build/libs/containerize-backend.jar
```

서버가 `http://localhost:8000`에서 시작됩니다.

- API 문서: http://localhost:8000/api/docs
- Health Check: http://localhost:8000/health

### Docker 실행

```bash
docker build -t containerize-backend .
docker run -p 8000:8000 containerize-backend
```

### docker-compose (풀스택)

[containerize-tool](https://github.com/gpjeong/containerize-tool) 레포에서:

```bash
cd ../containerize-tool
docker-compose up --build
```

## 환경변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `SPRING_PROFILES_ACTIVE` | `default` | 활성 프로필 (`dev`, `prod`) |
| `APP_UPLOAD_DIRECTORY` | `./uploads` | 파일 업로드 경로 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:8000,...` | CORS 허용 origins (쉼표 구분) |

## 프로젝트 구조

```
src/main/java/com/containerize/
├── config/           # 설정 (CORS, Rate Limit, WebMvc)
├── controller/       # REST API 엔드포인트
├── service/          # 비즈니스 로직
├── dto/              # 요청/응답 DTO
├── exception/        # 예외 처리
└── util/             # 유틸리티

src/main/resources/
├── application.yml   # 메인 설정
├── application-dev.yml
└── templates/        # Dockerfile Jinja2 템플릿
```

## 테스트

```bash
./gradlew test
```

## API Endpoints

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/generate` | Dockerfile 생성 |
| POST | `/api/upload/java` | JAR/WAR 파일 업로드 |
| POST | `/api/analyze/python` | Python 프로젝트 분석 |
| POST | `/api/analyze/nodejs` | Node.js 프로젝트 분석 |
| GET | `/api/download/{session_id}` | 생성된 Dockerfile 다운로드 |
| GET | `/health` | Health check |

## 관련 레포

- [containerize-frontend](https://github.com/gpjeong/containerize-frontend) - React 프론트엔드
- [containerize-tool](https://github.com/gpjeong/containerize-tool) - 프로젝트 허브 + docker-compose + 문서
