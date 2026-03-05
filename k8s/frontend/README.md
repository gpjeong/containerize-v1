# Frontend Kubernetes Manifests

React 프론트엔드 서비스를 Kubernetes에 배포하기 위한 매니페스트 파일입니다.
nginx 기반 프로덕션 이미지를 사용하며 NodePort로 외부에서 접근합니다.

## 파일 목록

| 파일 | 설명 |
|------|------|
| `configmap.yaml` | K8s용 nginx 설정 |
| `deployment.yaml` | 프론트엔드 Pod 배포 설정 |
| `service.yaml` | 외부 접근을 위한 NodePort 서비스 설정 |

---

## configmap.yaml

Docker 이미지 내부의 nginx 설정을 K8s 환경에 맞게 오버라이드합니다.

### 오버라이드 이유

원본 이미지의 nginx 설정에는 Docker 전용 DNS 리졸버가 포함되어 있습니다.

```nginx
# 원본 (Docker 전용) - K8s에서 동작 안 함
resolver 127.0.0.11 valid=30s ipv6=off;
set $backend_url http://backend:8000;
```

Kubernetes에서는 CoreDNS가 `/etc/resolv.conf`를 통해 DNS를 처리하므로
`resolver` 지시어 없이 `proxy_pass`를 직접 사용합니다.

```nginx
# K8s용 - CoreDNS가 backend 서비스를 자동으로 해석
proxy_pass http://backend:8000;
```

### nginx 라우팅 규칙

| 경로 | 처리 방식 | 설명 |
|------|-----------|------|
| `/api/*` | 백엔드로 프록시 | `http://backend:8000`으로 전달 |
| `/*` | SPA 라우팅 | `index.html`로 폴백 (React Router 지원) |

---

## deployment.yaml

### 주요 설정

| 항목 | 값 | 설명 |
|------|----|------|
| 이미지 | `containerize-frontend:production` | `--target production`으로 빌드한 nginx 이미지 |
| `imagePullPolicy` | `IfNotPresent` | 로컬에 이미지가 있으면 Pull 생략 |
| 컨테이너 포트 | `4000` | nginx 리스닝 포트 |

### ConfigMap 마운트

```yaml
volumeMounts:
  - name: nginx-config
    mountPath: /etc/nginx/conf.d/default.conf
    subPath: default.conf
```

`configmap.yaml`에 정의된 nginx 설정 파일을 컨테이너의 nginx 설정 경로에 마운트하여
이미지 내부 설정을 오버라이드합니다.

### 리소스 제한

| 항목 | Request | Limit |
|------|---------|-------|
| Memory | 64Mi | 128Mi |
| CPU | 100m | 200m |

---

## service.yaml

### 주요 설정

| 항목 | 값 | 설명 |
|------|----|------|
| 서비스 타입 | `NodePort` | 클러스터 외부(브라우저)에서 직접 접근 가능 |
| 내부 포트 | `4000` | nginx 리스닝 포트 |
| 노드 포트 | **`30400`** | 외부 접근 포트 |

### 접속 URL

```
http://<NodeIP>:30400
```

- **minikube**: `http://$(minikube ip):30400`
- **온프레미스/클라우드**: 워커 노드 IP로 접근

---

## 배포 방법

```bash
# 1. 프로덕션 이미지 빌드 (프로젝트 루트에서 실행)
docker build --target production -t containerize-frontend:production ./containerize-frontend

# minikube 사용 시 로컬 이미지 사용
eval $(minikube docker-env)
docker build --target production -t containerize-frontend:production ./containerize-frontend

# 2. 매니페스트 적용 (ConfigMap → Deployment → Service 순서)
kubectl apply -f k8s/frontend/

# 3. 배포 상태 확인
kubectl get pods -l app=containerize-frontend
kubectl get service containerize-frontend

# 4. 접속 (minikube)
minikube ip   # 노드 IP 확인
# 브라우저에서 http://<NodeIP>:30400 접속

# 5. 로그 확인
kubectl logs -l app=containerize-frontend -f
```

---

## 전체 배포 순서 (백엔드 포함)

```bash
# 백엔드 먼저 배포 (프론트엔드 nginx가 백엔드 서비스를 참조하므로)
kubectl apply -f k8s/backend/
kubectl rollout status deployment/containerize-backend

# 프론트엔드 배포
kubectl apply -f k8s/frontend/
kubectl rollout status deployment/containerize-frontend
```
