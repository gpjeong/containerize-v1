# Containerize - Dockerfile 자동 생성 도구

프로젝트 정보를 입력하면 최적화된 Dockerfile을 자동으로 생성하고, Jenkins 파이프라인까지 연동하여 빌드를 트리거할 수 있는 웹 도구입니다.

---

## 프로젝트 구조

```
containerize-v1/
├── containerize-backend/    # Spring Boot 백엔드 (포트 8000)
└── containerize-frontend/   # React 프론트엔드 (포트 4000)
```

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| 백엔드 | Spring Boot 3.3.5, Java 17, Gradle 8.5 |
| 프론트엔드 | React 19, TypeScript, Vite 7, TailwindCSS 4 |
| 에디터 | CodeMirror 6 |
| HTTP 클라이언트 | Axios |
| 컨테이너 | Docker (멀티스테이지 빌드) |

---

## 주요 기능

### 1. 언어 및 프레임워크 선택

지원 언어 및 프레임워크:

| 언어 | 프레임워크 |
|------|-----------|
| Python | FastAPI, Flask, Django |
| Node.js | Express, NestJS, Next.js |
| Java | Spring Boot |

### 2. 프로젝트 분석

언어별로 다른 방식으로 프로젝트를 분석합니다.

- **Java**: JAR/WAR 파일을 업로드하면 자동으로 분석 (최대 500MB)
- **Python**: `requirements.txt` 내용을 붙여 넣으면 프레임워크와 서버 자동 감지
- **Node.js**: `package.json` 내용을 붙여 넣으면 패키지 매니저 및 빌드 명령어 자동 감지

### 3. Dockerfile 세부 설정

| 설정 항목 | 필수 여부 | 설명 |
|-----------|----------|------|
| Base Image | 필수 | 사용할 Docker 베이스 이미지 |
| 포트 | 필수 | 컨테이너 노출 포트 |
| 서비스 URL | 필수 | 서비스 배포 URL |
| 실행 명령어 | 필수 | 컨테이너 시작 명령어 |
| 환경 변수 | 선택 | `KEY=VALUE` 형식, 줄 단위 입력 |
| Health Check | 선택 | Health Check 엔드포인트 경로 |
| 시스템 의존성 | 선택 | apt 패키지 (공백 구분) |

### 4. Dockerfile 생성 및 다운로드

- CodeMirror 에디터에서 생성된 Dockerfile 미리보기
- `Dockerfile` 파일로 다운로드
- 세션 기반 관리 (1시간 후 자동 삭제)

### 5. Jenkins 파이프라인 연동

Dockerfile 생성 후 Jenkins 빌드까지 원클릭으로 연결됩니다.

**지원 파이프라인 유형:**
- **Standard**: 일반 Jenkins 에이전트에서 Docker 빌드
- **Kubernetes (DinD)**: Kubernetes Pod에서 Docker-in-Docker로 빌드
- **Kubernetes (Kaniko)**: Kubernetes Pod에서 권한 없이 Kaniko로 빌드

**주요 기능:**
- Jenkins 잡 존재 여부 확인 및 자동 생성
- Harbor 레지스트리 프로젝트 확인 및 자동 생성
- 파이프라인 스크립트 미리보기 및 직접 편집 후 빌드
- Git 연동 (URL, 브랜치, Credential ID)
- Docker 이미지명/태그 설정

---

## 빌드 방법

### 사전 요구사항

- Docker 및 Docker Compose
- (로컬 개발 시) Java 17, Node.js 20

---

### Docker로 빌드 및 실행 (권장)

#### 백엔드

```bash
cd containerize-backend

# 이미지 빌드
docker build -t containerize-backend .

# 컨테이너 실행
docker run -d \
  --name containerize-backend \
  -p 8000:8000 \
  -v $(pwd)/uploads:/app/uploads \
  containerize-backend
```

#### 프론트엔드 (개발 모드)

```bash
cd containerize-frontend

# 이미지 빌드 (development 스테이지)
docker build --target development -t containerize-frontend .

# 컨테이너 실행
docker run -d \
  --name containerize-frontend \
  -p 4000:4000 \
  -v $(pwd)/src:/app/src \
  -v $(pwd)/public:/app/public \
  -v $(pwd)/index.html:/app/index.html \
  containerize-frontend
```

#### 프론트엔드 (프로덕션 모드)

```bash
cd containerize-frontend

# 이미지 빌드 (production 스테이지 - nginx)
docker build --target production -t containerize-frontend-prod .

# 컨테이너 실행
docker run -d \
  --name containerize-frontend \
  -p 4000:4000 \
  containerize-frontend-prod
```

> 프로덕션 모드에서는 프론트엔드가 nginx를 통해 `/api` 경로를 백엔드(`backend:8000`)로 프록시합니다.

---

### Docker Compose로 한 번에 실행

프로젝트 루트에 `docker-compose.yaml`이 포함되어 있습니다.

```bash
# 빌드 및 실행 (개발 모드, 핫 리로드 지원)
docker compose up -d --build

# 로그 확인
docker compose logs -f

# 중지
docker compose down
```

> 프론트엔드는 기본적으로 개발 모드(핫 리로드)로 실행됩니다.
> 프로덕션 모드로 실행하려면 `docker-compose.prod.yaml`을 함께 사용하세요.

```bash
# 프로덕션 모드
COMPOSE_FILE=docker-compose.yaml:docker-compose.prod.yaml docker compose up -d --build
```

---

### 로컬에서 직접 실행 (개발용)

#### 백엔드

```bash
cd containerize-backend

# 빌드
./gradlew bootJar

# 실행
java -jar build/libs/containerize-backend-1.0.0.jar
```

#### 프론트엔드

```bash
cd containerize-frontend

# 의존성 설치
npm ci

# 개발 서버 실행 (포트 4000)
npm run dev -- --host 0.0.0.0 --port 4000

# 프로덕션 빌드
npm run build
```

---

## 접속 방법

| 서비스 | URL |
|--------|-----|
| 프론트엔드 (웹 UI) | http://localhost:4000 |
| 백엔드 API | http://localhost:8000 |
| Swagger UI | http://localhost:8000/api/docs |
| OpenAPI 스펙 | http://localhost:8000/api/openapi.json |
| Health Check | http://localhost:8000/health |

---

## API 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/health` | 헬스 체크 |
| GET | `/api/templates` | 지원 언어/프레임워크 목록 |
| POST | `/api/analyze/python` | Python 프로젝트 분석 |
| POST | `/api/analyze/nodejs` | Node.js 프로젝트 분석 |
| POST | `/api/upload/java` | Java JAR/WAR 업로드 및 분석 |
| POST | `/api/generate` | Dockerfile 생성 |
| GET | `/api/download/{sessionId}` | 생성된 Dockerfile 다운로드 |
| POST | `/api/preview/pipeline` | Jenkins 파이프라인 스크립트 미리보기 |
| POST | `/api/build/jenkins` | Jenkins 빌드 트리거 |
| POST | `/api/build/jenkins/custom` | 커스텀 파이프라인으로 빌드 트리거 |
| POST | `/api/setup/jenkins/check-job` | Jenkins 잡 존재 확인 |
| POST | `/api/setup/jenkins/create-job` | Jenkins 잡 생성 |
| POST | `/api/setup/harbor/check-project` | Harbor 프로젝트 존재 확인 |
| POST | `/api/setup/harbor/create-project` | Harbor 프로젝트 생성 |

---

## 사용 흐름

```
1. 언어 선택 (Python / Node.js / Java)
        ↓
2. 프로젝트 분석
   - Java: JAR/WAR 파일 업로드
   - Python: requirements.txt 붙여넣기
   - Node.js: package.json 붙여넣기
        ↓
3. Dockerfile 세부 설정
   (Base Image, 포트, 실행 명령어 등)
        ↓
4. Dockerfile 생성 → 미리보기 → 다운로드
        ↓
5. (선택) Jenkins 파이프라인 설정 → 빌드 트리거
```

---

## 환경 설정

백엔드는 `src/main/resources/application.yml`에서 설정합니다.

| 설정 키 | 기본값 | 설명 |
|---------|--------|------|
| `server.port` | `8000` | 백엔드 포트 |
| `app.upload.max-size` | `536870912` (500MB) | 업로드 최대 파일 크기 |
| `app.upload.cleanup-delay-seconds` | `3600` | 업로드 파일 자동 삭제 시간 (초) |
| `app.cors.allowed-origins` | `localhost:4000, localhost:5173` 등 | CORS 허용 오리진 |
| `app.rate-limit.general-requests-per-minute` | `30` | 일반 요청 분당 제한 |
| `app.rate-limit.heavy-requests-per-minute` | `10` | 파일 업로드 등 분당 제한 |

---

## 보안

- 파일 업로드 시 확장자(`.jar`, `.war`), Content-Type, Magic Number(바이트 시그니처), 파일 크기 4단계 검증
- 파일명 sanitization 및 경로 순회(Path Traversal) 방지
- 세션 ID UUID 형식 검증
- Rate Limiting 적용 (일반 요청 30/분, 업로드/생성 요청 10/분)
- 비루트 사용자(`appuser`)로 컨테이너 실행
- 업로드 파일 세션 기반 격리 및 1시간 후 자동 삭제
