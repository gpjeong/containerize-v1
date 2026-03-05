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
docker build -t containerize-frontend .
docker run -p 4000:4000 containerize-frontend
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
| `VITE_API_URL` | `http://localhost:8000` | 백엔드 API URL (빌드 타임) |
| `API_TARGET` | `http://localhost:8000` | 개발 서버 프록시 대상 (런타임) |

`.env.example`을 `.env.local`로 복사하여 사용:

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

## 관련 레포

- [containerize-backend](https://github.com/gpjeong/containerize-backend) - Spring Boot 백엔드
- [containerize-tool](https://github.com/gpjeong/containerize-tool) - 프로젝트 허브 + docker-compose + 문서
