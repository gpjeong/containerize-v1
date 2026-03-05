# 코드 리뷰 보고서 - Containerize v1

**리뷰 일자:** 2026-03-05
**리뷰어:** Code Reviewer (Claude Opus 4.6)
**대상:** containerize-backend (Spring Boot 3.3.5, Java 17), containerize-frontend (React 19, TypeScript, Vite)
**리뷰 파일 수:** 약 50개

---

## 요약

Containerize v1은 Dockerfile 자동 생성 및 Jenkins CI/CD 파이프라인 연동을 지원하는 웹 애플리케이션입니다. 전반적으로 파일 업로드 보안(확장자, MIME, Magic Number, 크기 검증), Rate Limiting, CORS 설정 등 기본적인 보안 메커니즘이 잘 갖추어져 있습니다. 그러나 XSS 취약점, 경로 순회 방어 미적용, SSL 검증 비활성화, 자격 증명 전송 방식 등에서 개선이 필요한 보안 이슈가 확인되었습니다.

| 심각도 | 건수 |
|--------|------|
| CRITICAL | 2 |
| HIGH | 5 |
| MEDIUM | 8 |
| LOW | 6 |
| **합계** | **21** |

---

## 심각도별 이슈

### CRITICAL (반드시 수정)

#### C-1. XSS(Cross-Site Scripting) 취약점 - `dangerouslySetInnerHTML` 사용

**파일:** `/containerize-frontend/src/components/modals/AlertModal.tsx:26`

```tsx
<div
  className="text-gray-600 text-center mb-6"
  dangerouslySetInnerHTML={{ __html: alert.message }}
/>
```

**문제:**
`dangerouslySetInnerHTML`을 사용하여 `alert.message`를 HTML로 직접 렌더링하고 있습니다. `JenkinsBuild.tsx:123-149`에서 `isHtml: true`로 설정된 메시지에 Jenkins API 응답값(`data.job_name`, `data.build_number`, `data.queue_id`, `data.job_url`)이 HTML 템플릿 리터럴 내부에 검증 없이 삽입됩니다. 만약 Jenkins 서버가 악의적인 데이터를 반환하거나, 공격자가 Job 이름에 `<script>` 태그를 포함시킬 경우 XSS 공격이 가능합니다.

**위험도:** 사용자 세션 탈취, 악성 스크립트 실행 가능

**개선 방안:**
- `dangerouslySetInnerHTML` 사용을 제거하고, React 컴포넌트로 구조화된 렌더링 사용
- 불가피하게 HTML 렌더링이 필요한 경우 DOMPurify 같은 새니타이저 라이브러리로 살균 처리
- `JenkinsBuild.tsx`에서 HTML 문자열 대신 구조화된 데이터 객체를 전달하고 AlertModal에서 별도의 성공 뷰를 렌더링

---

#### C-2. 경로 순회(Path Traversal) 취약점 - 파일명 미살균

**파일:** `/containerize-backend/src/main/java/com/containerize/util/SessionManager.java:88`

```java
Path filePath = sessionDir.resolve(file.getOriginalFilename());
```

**문제:**
`SecurityUtil.sanitizeFilename()` 메서드가 구현되어 있음에도 불구하고, `SessionManager.saveUploadedFile()`에서 `file.getOriginalFilename()`을 살균 없이 그대로 경로 해석에 사용하고 있습니다. `UploadController.java`에서도 `securityUtil.sanitizeFilename()`을 호출하지 않습니다. 공격자가 파일명에 `../../etc/passwd` 또는 `../../../app/entrypoint.sh`와 같은 경로 순회 문자열을 포함시키면 임의 위치에 파일을 쓸 수 있습니다.

**위험도:** 서버 파일 시스템의 임의 위치에 파일 업로드 가능, 원격 코드 실행(RCE)으로 이어질 수 있음

**개선 방안:**
- `SessionManager.saveUploadedFile()` 내부에서 `securityUtil.sanitizeFilename(file.getOriginalFilename())`을 호출하여 파일명 살균
- 또는 파일명을 UUID 등으로 대체하고 원래 파일명은 메타데이터로만 저장
- 추가로 `resolve()` 후 결과 경로가 `sessionDir` 하위에 있는지 `normalize()` + `startsWith()` 검증 추가

```java
Path filePath = sessionDir.resolve(sanitizedFilename).normalize();
if (!filePath.startsWith(sessionDir)) {
    throw new InvalidFileException("Invalid file path");
}
```

---

### HIGH (수정 권고)

#### H-1. SSL 인증서 검증 완전 비활성화 - MITM 공격에 취약

**파일:**
- `/containerize-backend/src/main/java/com/containerize/service/HarborClientService.java:62-76`
- `/containerize-backend/src/main/java/com/containerize/service/JenkinsClientService.java:500-514`

```java
sc.init(null, new TrustManager[]{new X509TrustManager() {
    public X509Certificate[] getAcceptedIssuers() { return null; }
    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
}}, new SecureRandom());
httpsConn.setHostnameVerifier((hostname, session) -> true);
```

**문제:**
Harbor 클라이언트와 Jenkins 클라이언트 모두 SSL 인증서 검증을 기본적으로 비활성화(`verifySsl = false`)합니다. `HarborClientService.initialize()` 오버로드 메서드가 기본값으로 `false`를 설정하며, `JenkinsClientService`도 동일합니다. 이로 인해 중간자 공격(MITM)이 가능하며, 전송되는 자격 증명(Harbor 비밀번호, Jenkins API 토큰)이 탈취될 수 있습니다.

**위험도:** 자격 증명 탈취, 중간자 공격을 통한 빌드 파이프라인 변조 가능

**개선 방안:**
- 기본값을 `verifySsl = true`로 변경
- 자체 서명 인증서가 필요한 경우 커스텀 TrustStore를 등록하여 특정 CA만 신뢰하도록 구성
- 로그에 SSL 비활성화 경고를 명확히 출력하고, 프로덕션 환경에서는 설정 불가하도록 프로파일 제한

---

#### H-2. 자격 증명이 요청 본문(body)을 통해 평문 전송

**파일:**
- `/containerize-backend/src/main/java/com/containerize/controller/HarborController.java:37-40`
- `/containerize-backend/src/main/java/com/containerize/controller/JenkinsController.java:214-219, 257-260`

```java
String harborUsername = (String) request.get("harbor_username");
String harborPassword = (String) request.get("harbor_password");
```

**문제:**
Harbor 비밀번호와 Jenkins API 토큰이 HTTP 요청 본문의 JSON 필드로 전송됩니다. 이 값들은 서버 로그, 프록시 로그, 브라우저 개발자 도구 등에 노출될 수 있습니다. 특히 HTTPS가 아닌 환경에서는 네트워크 패킷에서 직접 읽을 수 있습니다.

**위험도:** 자격 증명 노출, 특히 로그에 기록될 경우 영구 노출

**개선 방안:**
- 자격 증명을 서버 측 설정 또는 Vault/Secret Manager에서 관리
- 불가피한 경우 HTTPS 강제 적용(HSTS 헤더 설정)
- 자격 증명이 로그에 기록되지 않도록 민감한 필드 마스킹 필터 추가
- 프론트엔드에서도 메모리에서만 관리하고 `localStorage`/`sessionStorage`에 저장하지 않도록 확인 (현재는 상태 변수로 관리 중이라 이 부분은 양호)

---

#### H-3. 세션 ID에 대한 입력 검증 누락 - 경로 순회 가능

**파일:** `/containerize-backend/src/main/java/com/containerize/controller/UploadController.java:98`

```java
@GetMapping("/download/{sessionId}")
public ResponseEntity<Resource> downloadDockerfile(@PathVariable String sessionId) {
```

**문제:**
`downloadDockerfile` 엔드포인트에서 `sessionId` 파라미터에 대한 포맷 검증이 없습니다. `SessionManager.getSessionDir()`은 단순히 `Paths.get(appConfig.getUploadDir(), sessionId)`를 반환하므로, `sessionId`에 `../../etc` 같은 경로 순회 문자열을 포함시키면 서버의 임의 파일을 읽을 수 있습니다.

**위험도:** 서버 파일 시스템의 임의 파일 읽기 가능

**개선 방안:**
- `sessionId`가 UUID 포맷인지 정규식으로 검증 (예: `@Pattern(regexp = "[a-f0-9\\-]{36}")`)
- `SessionManager.getSessionDir()`에서 반환된 경로가 `uploadDir` 하위인지 `normalize()` + `startsWith()` 검증

```java
public Path getSessionDir(String sessionId) {
    Path base = Paths.get(appConfig.getUploadDir()).normalize();
    Path resolved = base.resolve(sessionId).normalize();
    if (!resolved.startsWith(base)) {
        throw new InvalidFileException("Invalid session ID");
    }
    return resolved;
}
```

---

#### H-4. `window` 전역 객체를 통한 컴포넌트 간 데이터 공유

**파일:** `/containerize-frontend/src/components/ConfigForm.tsx:164`

```tsx
(window as any).__getDockerConfig = getConfigForJenkins;
```

**파일:** `/containerize-frontend/src/components/JenkinsBuild.tsx:44-49`

```tsx
const getDockerConfig = (): Record<string, any> | null => {
    if (typeof (window as any).__getDockerConfig === 'function') {
      return (window as any).__getDockerConfig();
    }
    return null;
};
```

**문제:**
`window` 전역 객체에 함수를 할당하여 컴포넌트 간 데이터를 공유하고 있습니다. 이는 다음과 같은 보안 및 아키텍처 문제를 야기합니다:
1. **보안:** 브라우저 콘솔이나 악성 확장 프로그램에서 `window.__getDockerConfig`를 오버라이드하여 설정을 변조할 수 있음
2. **아키텍처:** React의 단방향 데이터 흐름 원칙을 위반하며, TypeScript 타입 안정성을 무력화(`as any` 사용)
3. **메모리 누수:** 컴포넌트 언마운트 시 전역 참조가 정리되지 않음

**위험도:** 설정 변조를 통한 악의적 Dockerfile 생성, 메모리 누수

**개선 방안:**
- AppContext에 Docker 설정 상태를 추가하여 Context를 통해 공유
- 또는 `useRef`를 상위 컴포넌트에서 관리하고 props로 전달
- `window` 전역 객체 사용 제거

---

#### H-5. `@Valid` 어노테이션 미적용으로 입력 검증 우회

**파일:**
- `/containerize-backend/src/main/java/com/containerize/controller/HarborController.java:35, 69`
- `/containerize-backend/src/main/java/com/containerize/controller/JenkinsController.java:39, 114, 213, 255, 287`

**문제:**
Harbor 및 Jenkins 관련 엔드포인트에서 `Map<String, Object>` 또는 `Map<String, String>`을 직접 파라미터로 사용하고 있어 `@Valid`를 통한 Bean Validation이 적용되지 않습니다. `GenerateRequest`와 달리 이 엔드포인트들에는 null 체크, 포맷 검증, 길이 제한 등이 전혀 없습니다. `NullPointerException`이나 `ClassCastException`이 발생하면 500 에러가 그대로 클라이언트에 노출됩니다.

**위험도:** 서버 크래시, 예상치 못한 오류 노출

**개선 방안:**
- 각 엔드포인트에 전용 DTO 클래스를 정의하고 `@NotBlank`, `@Size`, `@Pattern` 등의 Bean Validation 어노테이션 적용
- 또는 컨트롤러 메서드 시작 시 수동으로 null 체크 및 포맷 검증 수행
- `Map<String, Object>`의 직접 사용을 최소화

---

### MEDIUM (개선 고려)

#### M-1. Rate Limiter의 메모리 누수 가능성 - 버킷 정리 미구현

**파일:** `/containerize-backend/src/main/java/com/containerize/config/RateLimitInterceptor.java:26`

```java
private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
```

**문제:**
IP별로 생성되는 `TokenBucket`이 `ConcurrentHashMap`에 무한히 축적됩니다. 장시간 운영 시 서로 다른 IP에서 접근한 모든 버킷이 메모리에 남아 OOM(Out-Of-Memory) 위험이 있습니다.

**개선 방안:**
- 주기적으로 만료된 버킷을 정리하는 스케줄된 태스크 추가
- Caffeine, Guava Cache 등 TTL 기반 캐시로 교체
- 또는 `lastRefillTime` 기준으로 일정 시간 이상 경과한 엔트리 자동 삭제

---

#### M-2. 오류 메시지에 내부 구현 정보 노출

**파일:**
- `/containerize-backend/src/main/java/com/containerize/controller/UploadController.java:88`
- `/containerize-backend/src/main/java/com/containerize/exception/GlobalExceptionHandler.java:119-122`

```java
throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upload failed: " + e.getMessage());
```

```java
response.put("detail", "Internal server error: " + ex.getMessage());
```

**문제:**
Java 예외 메시지(`e.getMessage()`)가 클라이언트 응답에 그대로 포함됩니다. 스택 트레이스, 클래스명, 파일 경로 등의 내부 정보가 노출되어 공격자에게 시스템 구조를 파악할 수 있는 단서를 제공합니다.

**개선 방안:**
- 프로덕션 환경에서는 일반적인 오류 메시지만 반환 (예: "Internal server error")
- 상세 정보는 서버 로그에만 기록
- `GlobalExceptionHandler`의 catchall 핸들러에서 `ex.getMessage()`를 제거하고 고정 메시지 사용

---

#### M-3. `ScheduledExecutorService` 종료 훅 미등록

**파일:** `/containerize-backend/src/main/java/com/containerize/util/SessionManager.java:39, 190-202`

```java
this.scheduledExecutor = Executors.newScheduledThreadPool(1);
```

**문제:**
`SessionManager`에 `shutdown()` 메서드가 구현되어 있으나, `@PreDestroy`나 `DisposableBean` 인터페이스를 통해 Spring 컨테이너 종료 시 자동으로 호출되지 않습니다. 애플리케이션 종료 시 스레드 풀이 정상적으로 종료되지 않아 리소스 누수가 발생합니다.

**개선 방안:**
- `shutdown()` 메서드에 `@PreDestroy` 어노테이션 추가
- 또는 `DisposableBean` 인터페이스 구현

```java
@PreDestroy
public void shutdown() { ... }
```

---

#### M-4. 사용자 정의 JSON 파서의 불안정성

**파일:** `/containerize-backend/src/main/java/com/containerize/service/JenkinsClientService.java:520-568`

```java
private Map<String, Object> parseJsonResponse(String json) {
    // Simple regex-based JSON parsing for crumb and build info
    Pattern numberPattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\\d+)");
    ...
}
```

**문제:**
정규식 기반의 수동 JSON 파서를 사용하고 있습니다. 이 접근법은 다음과 같은 문제를 야기합니다:
1. 중첩된 객체, 배열, 이스케이프된 문자열을 올바르게 처리하지 못함
2. 잘못된 필드 매핑으로 인한 예기치 않은 동작 가능
3. `HarborClientService`에서는 이미 `ObjectMapper`를 사용하고 있어 일관성 부족

**개선 방안:**
- `ObjectMapper`(Jackson)를 사용하여 JSON 파싱 통일
- `HarborClientService`처럼 `objectMapper.readValue(response, Map.class)` 사용

---

#### M-5. 생성된 Dockerfile 세션의 자동 정리 미적용

**파일:** `/containerize-backend/src/main/java/com/containerize/controller/DockerfileController.java:153-157`

```java
String sessionId = UUID.randomUUID().toString();
sessionManager.saveDockerfile(sessionId, dockerfileContent);
```

**문제:**
`/api/generate` 엔드포인트에서 생성되는 Dockerfile 세션에 대해 `scheduleCleanup()`이 호출되지 않습니다. `/api/upload/java`에서는 `sessionManager.scheduleCleanup(sessionId)`를 호출하지만, generate 엔드포인트는 누락되어 있어 생성된 Dockerfile 파일이 디스크에 영구 축적됩니다.

**개선 방안:**
- `DockerfileController.generateDockerfile()`에서도 `sessionManager.scheduleCleanup(sessionId)` 호출 추가
- 또는 애플리케이션 시작 시 오래된 세션 디렉토리를 정리하는 초기화 로직 추가

---

#### M-6. `@Autowired` 필드 주입 대신 생성자 주입 권장

**파일:**
- `/containerize-backend/src/main/java/com/containerize/controller/UploadController.java:30-40`
- `/containerize-backend/src/main/java/com/containerize/controller/DockerfileController.java:33-42`
- `/containerize-backend/src/main/java/com/containerize/controller/JenkinsController.java:26-30`

```java
@Autowired
private SecurityUtil securityUtil;

@Autowired
private SessionManager sessionManager;
```

**문제:**
필드 주입(`@Autowired`)은 Spring 공식 문서에서 권장하지 않는 방식입니다. 테스트 시 의존성 대체가 어렵고, 필수 의존성이 누락되어도 컴파일 시점에 감지할 수 없으며, 불변(final) 필드 사용이 불가능합니다.

**개선 방안:**
- 생성자 주입(Constructor Injection)으로 전환
- `@RequiredArgsConstructor`(Lombok)와 `final` 필드 조합 사용

---

#### M-7. HarborController/JenkinsController에서 서비스 인스턴스를 매 요청마다 생성

**파일:**
- `/containerize-backend/src/main/java/com/containerize/controller/HarborController.java:44, 83`
- `/containerize-backend/src/main/java/com/containerize/controller/JenkinsController.java:168, 224, 262, 295`

```java
HarborClientService harborClient = new HarborClientService();
harborClient.initialize(baseHarborUrl, harborUsername, harborPassword);
```

**문제:**
매 요청마다 `new HarborClientService()` 또는 `new JenkinsClientService()`를 생성하고 있습니다. 이는 Spring DI를 우회하며, 각 요청마다 SSL 컨텍스트를 재생성하여 불필요한 오버헤드를 발생시킵니다. 또한 테스트 시 모킹이 어렵습니다.

**개선 방안:**
- Factory 패턴 또는 `@Scope("prototype")` Bean으로 전환
- 또는 클라이언트 빌더 서비스를 Spring Bean으로 등록

---

#### M-8. `octet-stream` Content-Type 미지원

**파일:** `/containerize-backend/src/main/java/com/containerize/util/SecurityUtil.java:50-55`

**문제:**
일부 브라우저는 `.jar` 또는 `.war` 파일을 업로드할 때 `application/octet-stream`을 Content-Type으로 설정합니다. 현재 허용 목록에 이 타입이 포함되어 있지 않아 정상적인 파일 업로드가 거부될 수 있습니다. 다만 Magic Number 검증으로 보완되므로 보안 위험보다는 사용성 문제입니다.

**개선 방안:**
- `application/octet-stream`을 허용 목록에 추가하되, Magic Number 검증을 반드시 병행
- 또는 Content-Type 검증의 우선순위를 낮추고 Magic Number 검증을 주요 방어선으로 설정

---

### LOW (선택적 개선)

#### L-1. 중복 코드 - 설정 구성 로직

**파일:** `/containerize-frontend/src/components/ConfigForm.tsx:94-118, 134-161`

**문제:**
`handleGenerate` 함수와 `getConfigForJenkins` 함수의 설정 구성 로직이 완전히 동일합니다. DRY 원칙에 따라 하나의 함수로 통합해야 합니다.

**개선 방안:**
- 공통 함수를 추출하여 양쪽에서 호출

---

#### L-2. 중복 코드 - Jenkins Pipeline 생성 로직

**파일:**
- `/containerize-backend/src/main/java/com/containerize/controller/JenkinsController.java:39-102`
- `/containerize-backend/src/main/java/com/containerize/controller/JenkinsController.java:113-204`

**문제:**
`previewPipeline`과 `triggerJenkinsBuild` 메서드에서 Dockerfile 생성 및 Pipeline 종류 선택 로직이 거의 동일하게 반복됩니다.

**개선 방안:**
- 공통 로직을 private 헬퍼 메서드로 추출

---

#### L-3. 하드코딩된 기본값

**파일:**
- `/containerize-backend/src/main/java/com/containerize/service/DockerfileGeneratorService.java:200-211`
- `/containerize-backend/src/main/java/com/containerize/controller/JenkinsController.java:218`

```java
case "python" -> "python:3.11-slim";
case "java" -> "eclipse-temurin:17-jre-alpine";
```

```java
String jenkinsUsername = (String) request.getOrDefault("jenkins_username", "admin");
```

**문제:**
Docker 베이스 이미지 버전, 기본 사용자 이름 등이 코드에 하드코딩되어 있습니다. 이미지 버전이 변경되거나 기본값 조정이 필요할 때 코드 변경이 필요합니다.

**개선 방안:**
- `application.yml`에서 설정값으로 관리

---

#### L-4. `FileAnalyzerService.parseManifest()`에서 InputStream 미닫힘

**파일:** `/containerize-backend/src/main/java/com/containerize/service/FileAnalyzerService.java:83-85`

```java
String manifestData = new String(
    jar.getInputStream(manifestEntry).readAllBytes(),
    StandardCharsets.UTF_8
);
```

**문제:**
`jar.getInputStream(manifestEntry)`의 반환값이 try-with-resources로 관리되지 않아 InputStream이 명시적으로 닫히지 않습니다. `ZipFile`이 닫힐 때 내부 스트림이 함께 정리되기는 하지만 명시적 정리가 바람직합니다.

**개선 방안:**
```java
try (InputStream is = jar.getInputStream(manifestEntry)) {
    String manifestData = new String(is.readAllBytes(), StandardCharsets.UTF_8);
}
```

---

#### L-5. 프론트엔드 Vite 설정 누락 확인

**파일:** `/containerize-frontend/src/api/client.ts:12`

```typescript
const api = axios.create({
  baseURL: '/api',
```

**문제:**
API 클라이언트의 `baseURL`이 상대 경로(`/api`)로 설정되어 있어 Vite 개발 서버의 프록시 설정에 의존합니다. Vite 설정 파일에서 프록시가 올바르게 구성되어 있는지 확인이 필요합니다. CORS 설정에 `localhost:5173`(Vite 기본 포트)이 포함되어 있어 직접 호출도 가능하지만, 개발/프로덕션 환경 전환 시 혼동이 생길 수 있습니다.

**개선 방안:**
- Vite 설정에서 `/api` 프록시 구성 확인
- 환경변수(`VITE_API_URL`)를 활용한 동적 baseURL 설정

---

#### L-6. 테스트 커버리지 부족

**파일:** `/containerize-backend/src/test/java/com/containerize/`

**문제:**
현재 테스트 파일은 4개(`DockerfileControllerTest`, `DockerfileGeneratorServiceTest`, `FileAnalyzerServiceTest`, `SecurityUtilTest`)만 존재합니다. 다음 영역에 대한 테스트가 없습니다:
- `HarborController`, `JenkinsController`, `UploadController`
- `HarborClientService`, `JenkinsClientService`
- `SessionManager` (세션 생성/정리/경로 순회 방어)
- `RateLimitInterceptor`
- 프론트엔드 테스트 전무

**개선 방안:**
- 보안 관련 로직(`SessionManager` 경로 순회, `SecurityUtil`)에 대한 테스트 우선 추가
- Controller 통합 테스트(`@WebMvcTest`) 추가
- 프론트엔드에 Jest/Vitest 기반 단위 테스트 도입

---

## 보안 체크리스트 요약

| 항목 | 상태 | 비고 |
|------|------|------|
| 파일 확장자 검증 | 양호 | `.jar`, `.war`만 허용 |
| MIME 타입 검증 | 양호 | `application/java-archive`, `application/zip` 허용 |
| Magic Number 검증 | 양호 | ZIP 매직 넘버(PK) 확인 |
| 파일 크기 제한 | 양호 | 500MB 제한, Spring 설정 및 코드 양쪽 검증 |
| 파일명 살균 | **미흡** | `sanitizeFilename()` 존재하나 미사용 (C-2) |
| 경로 순회 방어 | **미흡** | sessionId 및 파일명 미검증 (C-2, H-3) |
| CORS 설정 | 양호 | 환경변수 기반 허용 오리진 관리 |
| Rate Limiting | 양호 | IP 기반 Token Bucket, 일반/중 요청 분리 |
| XSS 방어 | **미흡** | `dangerouslySetInnerHTML` 사용 (C-1) |
| CSRF 방어 | 해당없음 | REST API이며 쿠키 미사용 |
| 하드코딩된 시크릿 | 양호 | 코드 내 하드코딩된 비밀 없음 |
| SSL/TLS | **미흡** | 기본값 SSL 검증 비활성화 (H-1) |
| 입력 검증 | **부분적** | 일부 엔드포인트 `@Valid` 미적용 (H-5) |
| 에러 핸들링 | 양호 | `GlobalExceptionHandler` 존재, 다만 정보 노출 주의 (M-2) |
| 세션/파일 정리 | **부분적** | upload는 정리, generate는 누락 (M-5) |

---

## 양호한 사항

다음 항목들은 잘 구현되어 있어 별도 개선이 불필요합니다:

1. **다층 파일 검증:** 확장자, Content-Type, Magic Number, 파일 크기를 4단계로 검증
2. **GlobalExceptionHandler:** 다양한 예외 유형에 대한 일관된 응답 형식
3. **Rate Limiting:** IP 기반 Token Bucket 방식, 일반/중요 요청 분리
4. **CORS 설정:** 환경변수를 통한 유연한 오리진 관리
5. **Docker 이미지 보안:** 멀티스테이지 빌드, 비루트 사용자, Health Check 적용
6. **프론트엔드 상태 관리:** `useReducer` + Context API를 활용한 명확한 상태 관리
7. **TypeScript 타입 정의:** 인터페이스를 통한 API 응답/요청 타입 안정성
8. **Jinja2 템플릿 엔진:** Jinjava를 통한 안전한 Dockerfile 템플릿 렌더링
9. **프론트엔드에 `console.log` 부재:** 프로덕션 코드에 디버그 출력이 없음
10. **`.gitignore` 구성:** `.env`, `uploads/`, `.DS_Store` 등 적절히 제외

---

## 개선 권고사항 (우선순위순)

### 즉시 수정 (Sprint 1)

1. **C-1 XSS 수정:** `dangerouslySetInnerHTML` 제거, React 컴포넌트로 구조화된 렌더링
2. **C-2 경로 순회 수정:** `SessionManager.saveUploadedFile()`에서 `sanitizeFilename()` 적용 및 경로 정규화 검증
3. **H-3 세션 ID 검증:** UUID 포맷 검증 및 경로 정규화

### 단기 수정 (Sprint 2)

4. **H-1 SSL 검증:** 기본값을 `true`로 변경, 자체 서명 인증서용 커스텀 TrustStore 지원
5. **H-4 전역 상태 공유:** `window.__getDockerConfig` 제거, Context API를 통한 데이터 공유
6. **H-5 입력 검증:** Harbor/Jenkins 엔드포인트에 전용 DTO 및 `@Valid` 적용
7. **M-5 세션 정리:** `DockerfileController.generateDockerfile()`에 `scheduleCleanup()` 추가

### 중기 개선 (Sprint 3)

8. **M-1 Rate Limiter:** TTL 기반 캐시로 교체
9. **M-2 에러 메시지:** 프로덕션 환경에서 내부 정보 미노출
10. **M-3 종료 훅:** `@PreDestroy` 추가
11. **M-4 JSON 파서:** Jackson `ObjectMapper`로 통일
12. **L-6 테스트:** 보안 로직 우선 테스트 추가

---

## 결론

**판정: REQUEST CHANGES**

Containerize v1은 기본적인 보안 메커니즘이 잘 갖추어져 있으나, **XSS 취약점(C-1)과 경로 순회 취약점(C-2)** 이 두 가지 CRITICAL 이슈가 존재합니다. 특히 C-2는 이미 구현된 `sanitizeFilename()` 메서드가 실제로 사용되지 않는 누락으로 인한 것이며, 적용만 하면 바로 해결됩니다. H-1(SSL 검증 비활성화)과 H-3(세션 ID 경로 순회) 역시 프로덕션 배포 전 반드시 수정되어야 합니다.

CRITICAL/HIGH 이슈를 해결한 후 MEDIUM 이슈들을 순차적으로 개선하면 프로덕션 수준의 보안과 코드 품질을 달성할 수 있습니다.
