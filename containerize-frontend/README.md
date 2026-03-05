# Containerize Frontend (React + Vite)

Dockerfile Generator 프론트엔드 애플리케이션. 사용자가 언어/프레임워크를 선택하고 설정을 입력하면 최적화된 Dockerfile을 생성합니다.

## Tech Stack

- **React 19** + **TypeScript 5.9**
- **Vite 7** (빌드 + 개발 서버)
- **Tailwind CSS 4** (스타일링)
- **CodeMirror 6** (Dockerfile 에디터)
- **Axios** (HTTP 클라이언트)

## Quick Start

### 단독 실행 (개발 모드)

```bash
npm install
npm run dev
```

`http://localhost:4000`에서 개발 서버가 시작됩니다.

> 백엔드 API(`http://localhost:8000`)가 실행 중이어야 합니다. Vite dev server가 `/api` 요청을 백엔드로 프록시합니다.

### 프로덕션 빌드

```bash
npm run build
npm run preview
```

### Docker 실행

```bash
# 개발 모드 (핫 리로드)
docker build --target development -t containerize-frontend .
docker run -p 4000:4000 \
  -e API_TARGET=http://host.docker.internal:8000 \
  -v $(pwd)/src:/app/src \
  containerize-frontend

# 프로덕션 모드 (nginx)
docker build --target production -t containerize-frontend-prod .
docker run -p 4000:4000 containerize-frontend-prod
```

### docker-compose (풀스택, 권장)

프로젝트 루트에서:

```bash
cd ..
docker compose up -d --build
```

## 환경변수

| 변수 | 로컬 개발 | Docker 컨테이너 | 설명 |
|------|-----------|-----------------|------|
| `VITE_API_BASE_URL` | `http://localhost:8000` | `http://localhost:8000` | 브라우저에서 직접 호출하는 API URL (빌드 타임) |
| `API_TARGET` | `http://localhost:8000` | `http://backend:8000` | Vite dev server 프록시 대상 (런타임) |

> **Docker 컨테이너 주의사항**: 컨테이너 내부에서 `localhost`는 백엔드가 아닌 프론트엔드 컨테이너 자신을 가리킵니다.
> Docker 환경에서는 반드시 `API_TARGET=http://backend:8000`으로 설정해야 합니다 (docker-compose.yaml에 이미 설정되어 있음).

`.env.example`을 `.env.local`로 복사하여 로컬 개발 환경 설정:

```bash
cp .env.example .env.local
```

## 프로젝트 구조

```
src/
├── App.tsx               # 메인 앱 컴포넌트
├── main.tsx              # 엔트리 포인트
├── api/
│   └── client.ts         # Axios 클라이언트
├── components/
│   ├── Header.tsx
│   ├── Footer.tsx
│   ├── LanguageSelector.tsx    # 언어/프레임워크 선택
│   ├── FileUpload.tsx          # JAR/WAR 업로드
│   ├── ConfigForm.tsx          # Docker 설정 폼
│   ├── DockerfilePreview.tsx   # Dockerfile 미리보기
│   ├── JenkinsBuild.tsx        # Jenkins 빌드 트리거
│   ├── LoadingOverlay.tsx
│   └── modals/
│       ├── AlertModal.tsx
│       ├── JenkinsSetupModal.tsx
│       ├── HarborSetupModal.tsx
│       ├── PipelinePreviewModal.tsx
│       └── ResetConfirmModal.tsx
├── context/
│   └── AppContext.tsx     # 전역 상태 관리
└── types/
    └── index.ts           # TypeScript 타입 정의
```

## 스크립트

| 명령어 | 설명 |
|--------|------|
| `npm run dev` | 개발 서버 시작 (HMR) |
| `npm run build` | 프로덕션 빌드 |
| `npm run preview` | 빌드 결과 미리보기 |
| `npm run lint` | ESLint 실행 |
