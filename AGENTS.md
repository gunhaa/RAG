# AGENTS.md

gunha company 사내 규정 RAG 챗봇. Gemini 가 `@Tool` 로 벡터 검색을 직접 호출한다.
웹 화면(`:8080`)은 챗봇이 아니라 디버그 콘솔이다 — 관찰 장치를 걷어내는 변경은 하지 말 것.

Java 25 · Spring Boot 4.1.1 · Spring AI 2.0.1 · Gradle(Kotlin DSL) · pgvector.

## 명령

| | |
|---|---|
| 실행 | `./gradlew bootRun` |
| 전 계층 로그 | `./gradlew bootRun --args="--spring.profiles.active=debug"` |
| 색인 건너뛰기 | `./gradlew bootRun --args="--rag.index-on-startup=false"` |
| negative control | `./gradlew bootRun --args="--rag.tools-enabled=false"` |
| 테스트 (Docker 필요) | `./gradlew test` |
| DB | `docker compose exec pgvector psql -U rag -d rag` |

## 어디를 볼 것인가

| 작업 | 위치 |
|---|---|
| 설정·모델·차원·로그 레벨 | `resources/application*.yml` — 함정마다 이유가 주석에 있다 |
| 청킹 규칙, 색인 | `index/` |
| 검색 도구, 유사도 조건 | `search/` |
| 시스템 프롬프트, 응답 조립 | `chat/` |
| 트레이스 수집 (HTTP 전문·프롬프트·로그) | `debug/` |
| 디버그 콘솔 화면 | `resources/static/index.html` |
| `.env` 주입 로직 | `build.gradle.kts` 의 `dotenv()` |

## 함정

### API 키

- `.env` 값에 따옴표를 쓰지 않는다. Gradle 은 벗기지만 Spring properties 파서는 값으로 취급해서
  실행 경로마다 다르게 해석된다.
- 주입 경로가 두 개다 — Gradle 실행은 `build.gradle.kts` 환경변수, IDE 직접 실행은
  `application.yml` 의 `spring.config.import`. 한쪽만 고치면 다른 경로가 깨진다.
- HTTP 전문을 캡처하는 코드는 `x-goog-api-key`·`authorization` 헤더와 `?key=` 를 마스킹한다.

### Gemini 모델

- `ListModels` 응답은 접근 권한을 보장하지 않는다. `gemini-2.5-flash` 는 목록에 있으면서
  `generateContent` 는 404 다. 모델을 바꿨으면 실제 생성 호출로 확인한다.
- Spring AI 는 아래 셋을 모두 `Failed to generate content` 로 덮는다. HTTP 코드로 갈라야 한다.
  - `404` 모델명·접근 권한 → 설정 수정
  - `503` 일시 과부하 → 재시도
  - `429` 쿼터. 무료 등급 **모델별 하루 20회**. 모델을 바꾸면 한도가 새로 시작된다
- `spring.ai.google.genai` 아래에 `project-id` 나 `location` 을 넣으면 Vertex AI 모드로 바뀌어
  Developer API Key 가 400 으로 거부된다.

### 임베딩 · pgvector

- `dimensions: 1536` 은 임베딩 옵션 · `vectorstore.pgvector.dimensions` · DB 의 `vector(1536)`
  3곳이 일치해야 한다. HNSW 인덱스 상한이 2000 이라 모델 기본값 3072 를 쓸 수 없다.
- 임베딩 모델이나 차원을 바꾸면 전체 재색인이 필요하다. 섞이면 검색이 조용히 망가진다.
- 임베딩 API 호출은 `VectorStore` 뒤에 숨어 있다. `add()` 와 `similaritySearch()` 가 호출 지점이며
  코드에서 `EmbeddingModel` 을 직접 쓰는 곳은 없다.
- SQL 정렬은 거리 오름차순(`ORDER BY embedding <=> ?`)이어야 HNSW 를 탄다. `1 - (...)` 내림차순은
  전체 스캔이다.
- `metadata` 는 `json` 이라 `->>` 만 된다. `@>`·GIN 인덱스는 쓸 수 없다.
- 유사도 범위가 다르다 — 질문 대 청크 0.57~0.68, 청크 대 청크 0.80 이상.
  `rag.similarity-threshold` 는 전자 기준이다.

### Spring AI

- `Advisor` 를 `@Bean` 으로 올려도 ChatClient 에 붙지 않는다. 오토컨피그가 적용하는 것은
  `ChatClientBuilderCustomizer` 뿐이다 (`ChatClientCustomizer` 는 deprecated).
- 채팅과 임베딩이 서로 다른 경로로 `com.google.genai.Client` 를 만든다. HTTP 를 가로채려면
  `Client` 빈과 `GoogleGenAiEmbeddingConnectionDetails` 를 모두 오버라이드해 `OkHttpClient` 를
  공유시켜야 한다.
- 도구 호출은 HTTP 2 왕복이다. 질문 1건에 `generateContent` 2회 + `embedContent` 1회 이상.
- `debug/TraceContext` 는 블로킹 호출이 톰캣 스레드 하나로 끝나는 것에 의존한다. `.stream()` 으로
  바꾸면 트레이스가 비어버린다.

### 테스트

- `DocumentChunkingTest` 는 키·Docker 없이 돈다. 문서를 고치면 이걸 먼저 돌린다 — 규칙 하나가
  두 청크로 쪼개지면 검색이 앞부분만 가져와 예외 조항이 유실된다.
- `RagApplicationTests` 는 진짜 OS 환경변수 `GEMINI_API_KEY` 로 게이트된다.
  `spring.config.import` 로는 통과하지 못한다.
- `spring.docker.compose.skip.in-tests: false` 가 필요하다. 기본값이면 컨테이너가 안 떠서
  `Failed to determine a suitable driver class` 로 깨진다.

## 스타일

들여쓰기는 탭, Spring Framework 컨벤션. 의존성 주입은 생성자 주입 + `private final`
(`@Autowired` 미사용). `search/RuleSearchTools` 는 요청마다 `new` 하는 비-빈이라 예외다.
