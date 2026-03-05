# Backend Kubernetes Manifests

Spring Boot 백엔드 서비스를 Kubernetes에 배포하기 위한 매니페스트 파일입니다.

## 파일 목록

| 파일 | 설명 |
|------|------|
| `deployment.yaml` | 백엔드 Pod 배포 설정 |
| `service.yaml` | 클러스터 내부 서비스 노출 설정 |

---

## deployment.yaml

### 주요 설정

| 항목 | 값 | 설명 |
|------|----|------|
| 이미지 | `containerize-backend:latest` | 빌드 후 로컬 또는 레지스트리 이미지 |
| `imagePullPolicy` | `IfNotPresent` | 로컬에 이미지가 있으면 Pull 생략 |
| 컨테이너 포트 | `8000` | Spring Boot 서버 포트 |
| 업로드 볼륨 | `emptyDir` | Pod 생명주기 동안 유지 (재시작 시 초기화) |

### 헬스 체크

백엔드의 `/health` 엔드포인트를 사용하여 Pod 상태를 모니터링합니다.

| 종류 | 경로 | 초기 대기 | 주기 |
|------|------|-----------|------|
| Liveness Probe | `GET /health` | 30초 | 30초마다 |
| Readiness Probe | `GET /health` | 15초 | 10초마다 |

- **Liveness**: 실패 시 Pod를 재시작합니다.
- **Readiness**: 실패 시 Service 트래픽에서 제외합니다.

### 리소스 제한

| 항목 | Request | Limit |
|------|---------|-------|
| Memory | 256Mi | 512Mi |
| CPU | 250m | 500m |

### 업로드 볼륨

```yaml
volumes:
  - name: uploads
    emptyDir: {}
```

> **주의**: `emptyDir`는 Pod가 재시작되면 업로드된 파일이 삭제됩니다.
> 파일을 영구 보존하려면 `PersistentVolumeClaim`으로 교체하세요.

---

## service.yaml

### 주요 설정

| 항목 | 값 | 설명 |
|------|----|------|
| 서비스 타입 | `ClusterIP` | 클러스터 내부에서만 접근 가능 |
| 서비스 이름 | **`backend`** | 프론트엔드 nginx 프록시 연결에 필수 |
| 포트 | `8000` | Spring Boot 서버 포트 |

> **서비스 이름이 `backend`인 이유**
>
> 프론트엔드 nginx 설정에 `proxy_pass http://backend:8000;`이 하드코딩되어 있습니다.
> 이름을 변경하면 프론트엔드에서 백엔드 API를 호출할 수 없습니다.

---

## 배포 방법

```bash
# 1. 이미지 빌드 (프로젝트 루트에서 실행)
docker build -t containerize-backend:latest ./containerize-backend

# minikube 사용 시 로컬 이미지 사용
eval $(minikube docker-env)
docker build -t containerize-backend:latest ./containerize-backend

# 2. 매니페스트 적용
kubectl apply -f k8s/backend/

# 3. 배포 상태 확인
kubectl get pods -l app=containerize-backend
kubectl get service backend

# 4. 로그 확인
kubectl logs -l app=containerize-backend -f
```
