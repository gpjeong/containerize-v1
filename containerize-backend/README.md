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
java -jar build/libs/containerize-backend-1.0.0.jar
```

서버가 `http://localhost:8000`에서 시작됩니다.

- API 문서: http://localhost:8000/api/docs
- Health Check: http://localhost:8000/health

### Docker 실행

```bash
docker build -t containerize-backend .
docker run -p 8000:8000 -v $(pwd)/uploads:/app/uploads containerize-backend
```

### docker-compose (풀스택)

프로젝트 루트에서:

```bash
cd ..
docker compose up -d --build
```

## 환경변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `SPRING_PROFILES_ACTIVE` | `default` | 활성 프로필 (`dev`, `prod`) |
| `APP_UPLOAD_DIRECTORY` | `./uploads` | 파일 업로드 경로 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4000,...` | CORS 허용 origins (쉼표 구분) |

`application.yml`에서 추가 설정 가능:

| 설정 키 | 기본값 | 설명 |
|---------|--------|------|
| `app.upload.max-size` | `536870912` (500MB) | 업로드 최대 파일 크기 |
| `app.upload.cleanup-delay-seconds` | `3600` | 업로드 파일 자동 삭제 시간 (초) |
| `app.rate-limit.general-requests-per-minute` | `30` | 일반 요청 분당 제한 |
| `app.rate-limit.heavy-requests-per-minute` | `10` | 업로드/생성 요청 분당 제한 |

## 프로젝트 구조

```
src/main/java/com/containerize/
├── config/           # 설정 (CORS, Rate Limit, WebMvc)
├── controller/       # REST API 엔드포인트
├── service/          # 비즈니스 로직
├── dto/              # 요청/응답 DTO
├── exception/        # 예외 처리
└── util/             # 유틸리티 (SecurityUtil, SessionManager)

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
| GET | `/health` | Health check |
| GET | `/api/templates` | 지원 언어/프레임워크 목록 |
| POST | `/api/analyze/python` | Python 프로젝트 분석 |
| POST | `/api/analyze/nodejs` | Node.js 프로젝트 분석 |
| POST | `/api/upload/java` | JAR/WAR 파일 업로드 및 분석 |
| POST | `/api/generate` | Dockerfile 생성 |
| GET | `/api/download/{sessionId}` | 생성된 Dockerfile 다운로드 |
| POST | `/api/preview/pipeline` | Jenkins 파이프라인 스크립트 미리보기 |
| POST | `/api/build/jenkins` | Jenkins 빌드 트리거 |
| POST | `/api/build/jenkins/custom` | 커스텀 파이프라인으로 빌드 트리거 |
| POST | `/api/setup/jenkins/check-job` | Jenkins 잡 존재 확인 |
| POST | `/api/setup/jenkins/create-job` | Jenkins 잡 생성 |
| POST | `/api/setup/harbor/check-project` | Harbor 프로젝트 존재 확인 |
| POST | `/api/setup/harbor/create-project` | Harbor 프로젝트 생성 |

## 보안

- 파일 업로드 시 확장자(`.jar`, `.war`), Content-Type, Magic Number, 파일 크기 4단계 검증
- 파일명 sanitization 및 경로 순회(Path Traversal) 방지
- 세션 ID UUID 형식 검증
- IP 기반 Rate Limiting (일반 요청 30/분, 업로드/생성 10/분)
- 비루트 사용자(`appuser`)로 컨테이너 실행
- 세션 기반 파일 격리 및 1시간 후 자동 삭제
