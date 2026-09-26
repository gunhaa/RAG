# RAG

RAG(Retrieval-Augmented Generation) 시스템을 만들기 위한 저장소입니다.

가상의 회사 **gunha company** 의 사내 규정을 벡터 DB에 넣고, Gemini 가 필요할 때
직접 검색 도구를 호출해서 답하는 챗봇입니다.

## 왜 "특이한 규칙" 인가

RAG 가 실제로 동작했는지 확인하려면, LLM 이 사전 지식으로는 절대 맞출 수 없는 내용이어야 합니다.

```
문서: "수요일은 전원 15:00에 퇴근한다."
질문: "수요일에 몇 시에 퇴근해요?"
  → "15시"      = 검색 성공 (문서에서만 나올 수 있는 답)
  → "보통 18시"  = 검색 실패 (사전 지식으로 지어낸 답)
```

그래서 `docs/` 의 규칙은 전부 일반적인 회사 관행과 다르게 작성되어 있습니다.
회의는 17분까지, 연차는 무기한 이월, 점심 법인카드는 3인 이상일 때만 승인 같은 식입니다.

## 기술 스택

| | |
|---|---|
| Java | 25 |
| 빌드 | Gradle 9.8 (Kotlin DSL) |
| Spring Boot | 4.1.1 |
| Spring AI | 2.0.1 |
| LLM | Gemini `gemini-3.5-flash` (API Key 모드) |
| 임베딩 | `gemini-embedding-001`, 1536차원 |
| 벡터 DB | PGVector (Docker Compose 자동 기동) |
| 검색 트리거 | LLM 의 Tool calling (`@Tool`) |

## 실행

Docker 가 떠 있어야 합니다. Gemini API 키는 <https://aistudio.google.com/apikey> 에서 무료로 발급받습니다.

```bash
cp .env.example .env     # .env 에 GEMINI_API_KEY=발급받은_키 를 채운다 (따옴표 없이)
./gradlew bootRun
open http://localhost:8080
```

앱이 시작되면 `compose.yaml` 의 pgvector 컨테이너가 자동으로 뜨고, `docs/*.md` 40개 청크가 색인됩니다.

### API 키 주입 경로

`.env` 는 gitignore 대상이라 키가 저장소에 들어가지 않습니다. 두 경로로 읽힙니다.

| 실행 방법 | 키가 전달되는 경로 |
|---|---|
| `./gradlew bootRun`, `./gradlew test` | `build.gradle.kts` 가 `.env` 를 파싱해 프로세스 **환경변수**로 주입 |
| IDE 에서 `RagApplication` 직접 실행 | `application.yml` 의 `spring.config.import: optional:file:.env[.properties]` |

`export GEMINI_API_KEY=...` 로 실제 환경변수를 지정하면 그 값이 `.env` 보다 우선합니다.

테스트 게이트(`@EnabledIfEnvironmentVariable`)는 진짜 환경변수를 보기 때문에, Gradle 쪽 주입이
없으면 `RagApplicationTests` 가 `.env` 만 있는 환경에서 항상 skip 됩니다.

## 디버그 콘솔 (웹 화면)

`http://localhost:8080` 은 챗봇이 아니라 **디버그 콘솔**입니다. 질문 하나가 처리되는 전 과정이
시간순 타임라인으로 나오고, 각 줄을 펼치면 전문이 보입니다.

```
+    0ms APP_HTTP         브라우저 → 앱: POST /api/chat → 200
+   52ms PROMPT           ChatClient 요청 — 시스템 프롬프트 + 사용자 질문 + 도구 정의
+ 7924ms GEMINI_HTTP      POST gemini-3.8-flash:generateContent → 200 (1.3KB)      7760ms
+ 7991ms TOOL             LLM 이 searchCompanyRules 호출 — query="수요일 퇴근 시간"
+ 8485ms GEMINI_HTTP      POST gemini-embedding-001:batchEmbedContents → 200        484ms
+ 8552ms VECTOR_SEARCH    pgvector 유사도 검색 → 4건                                558ms
+10122ms GEMINI_HTTP      POST gemini-3.8-flash:generateContent → 200 (1.5KB)      1556ms
+10131ms MODEL_RESPONSE   finishReason=STOP — 토큰 in=1171 out=82 total=1419      10078ms
```

| 계층 | 내용 |
|---|---|
| `APP_HTTP` | 브라우저 ↔ 앱 HTTP 전문 (브라우저가 직접 그림) |
| `PROMPT` | `ChatClientRequest` — 시스템 프롬프트·질문·도구 정의가 담긴 자바 객체 |
| `TOOL` | LLM 이 만든 검색어와 `topK`·`similarityThreshold` |
| `VECTOR_SEARCH` | 유사도 순 청크와 **LLM 에게 전달된 본문 그대로** |
| `GEMINI_HTTP` | **Gemini 와 주고받은 HTTP 원문** (요청 JSON, 상태코드, 응답 본문) |
| `MODEL_RESPONSE` | 최종 응답, `finishReason`, 토큰 사용량 |
| `LOG` | 처리 중 남은 모든 로그 (기본 접힘) |

질문 한 건에 **Gemini HTTP 가 3회** 나갑니다 — 생성(도구 호출 결정) → 임베딩 → 생성(최종 답변).
임베딩 요청의 `outputDimensionality: 1536` 처럼 설정이 실제로 어떻게 전송되는지 확인할 수 있습니다.

API 키가 실리는 `x-goog-api-key` 헤더와 `?key=` 쿼리는 `***masked***` 로 치환됩니다.

### 실패했을 때가 더 중요합니다

요청이 실패해도 트레이스는 내려옵니다. Spring AI 는 원인을 `Failed to generate content` 한 줄로
덮어버리는데, 콘솔에는 Gemini 가 보낸 응답 본문이 그대로 남습니다.

```
+53543ms GEMINI_HTTP   POST gemini-3.8-flash:generateContent → 429 (1.3KB)
  → RESOURCE_EXHAUSTED
    quota: GenerateRequestsPerDayPerProjectPerModel-FreeTier | value: 20
```

무료 등급은 **모델별 하루 20회**입니다. 한도는 모델마다 따로 계산되므로, 소진되면 다른 flash
모델로 바꿔 계속 실험할 수 있습니다.

### 어떻게 잡는가

- `GeminiHttpInterceptor` — Google GenAI SDK 가 내부적으로 OkHttp 를 쓰고
  `ClientOptions.customHttpClient()` 로 교체할 수 있어서, 인터셉터로 전문을 캡처합니다.
- `GenAiTraceConfig` — 채팅과 임베딩은 서로 다른 경로로 클라이언트를 만듭니다. 두 빈을 모두
  오버라이드해 **하나의 OkHttpClient 를 공유**시켜야 두 종류 호출이 한 타임라인에 모입니다.
- `TraceLogAppender` — Logback 루트 로거에 붙어 로그를 타임라인에 끼워 넣습니다. 따라서
  `LOG` 에 무엇이 보이는지는 아래 로그 레벨 설정이 결정합니다.
- `TraceContext` — ThreadLocal. 블로킹 호출 경로가 톰캣 스레드 하나로 끝나는 것에 의존하므로
  `.stream()` 으로 바꾸면 이 방식이 깨집니다.

## 로그 레벨

기본 실행은 `com.gunha.rag` 패키지만 DEBUG 입니다. 더 깊이 볼 때는 `debug` 프로파일을 켭니다.

```bash
./gradlew bootRun --args="--spring.profiles.active=debug"
```

`application-debug.yml` 이 아래를 열고, `ChatDebugConfig` 가 `SimpleLoggerAdvisor` 를 붙입니다.

| 로거 | 무엇을 볼 수 있나 |
|---|---|
| `org.springframework.ai.chat.client.advisor` | **ChatClient 로 오간 프롬프트·응답 원문** (도구 결과가 프롬프트에 실제로 들어갔는지) |
| `org.springframework.ai.model.tool` | LLM 의 도구 호출 요청 → `@Tool` 메서드 실행 → 결과 반환 |
| `org.springframework.ai.vectorstore.pgvector` | 유사도 검색 요청, 스키마 초기화 |
| `org.springframework.jdbc.core.JdbcTemplate` | pgvector 로 나가는 SQL |
| `org.springframework.boot.docker.compose` | 컨테이너 기동·헬스체크 대기 |

틀린 답이 나왔을 때 **검색이 실패한 것인지, 검색은 됐는데 LLM 이 근거를 무시한 것인지**는
최종 답변만 봐서는 구분할 수 없습니다. advisor 로그의 프롬프트에 청크 본문이 들어있는지
확인하면 바로 갈립니다.

IntelliJ 에서는 Run Configuration 의 **Active profiles** 에 `debug` 를 넣으면 됩니다.
Run 과 Debug 버튼의 차이는 디버거 연결 여부뿐이고, 로그 레벨은 양쪽 모두 동일하게 적용됩니다.

## 확인해 볼 질문

| 질문 | 기대 답변 |
|---|---|
| 수요일에 몇 시에 퇴근해요? | 15시 |
| 회의는 최대 몇 분까지 가능해요? | 17분 |
| 점심 법인카드 쓰는 조건이 뭐예요? | 3인 이상 + 다른 팀 1명 포함 |
| 키보드 지원 한도가 얼마예요? | 40만원 |
| 생일에 휴가 며칠 써요? | 2일 (당일 + 그 주 금요일) |
| 버그 찾으면 뭐 받아요? | 쿠폰 3장, 10장이면 휴가 1일 |
| 주차비 지원되나요? | "사내 문서에서 찾을 수 없습니다" (지어내면 실패) |
| 오늘 날씨 어때? | 검색 없이 답변 (도구 호출 안 함) |

화면에는 답변과 함께 **LLM 이 스스로 만든 검색어와 찾아온 근거 청크의 유사도**가 표시됩니다.

## RAG 효과 증명 (negative control)

검색 도구를 빼고 같은 질문을 던져 봅니다.

```bash
./gradlew bootRun --args="--rag.tools-enabled=false"
```

"보통 18시 퇴근", "연차에서 차감" 같은 일반론이 나오면, 앞의 정확한 답이 검색 덕분이었음이 증명됩니다.

## 설정

| 프로퍼티 | 설명 |
|---|---|
| `rag.top-k` | 한 번 검색할 때 가져올 청크 수 (기본 4) |
| `rag.similarity-threshold` | 이 값 미만 유사도는 버림 (기본 0.5) |
| `rag.tools-enabled` | `false` 면 LLM 에게 검색 도구를 주지 않음 |
| `rag.index-on-startup` | `false` 면 시작 시 색인 건너뜀 |

## 테스트

```bash
./gradlew test
```

`DocumentChunkingTest` 는 API 키·Docker 없이 돌아가며, **"규칙 1개 = 청크 1개"** 가 지켜지는지
검증합니다. 규칙 하나가 두 청크로 쪼개지면 검색이 앞부분만 가져와 LLM 이 예외 조항을 빠뜨린
답을 확신하게 되므로, 문서를 고칠 때마다 이 테스트를 먼저 돌려 봅니다.

## 알아둘 함정

- `spring.ai.google.genai` 아래에 `project-id` 나 `location` 을 설정하면 Vertex AI 모드로
  전환되어 Developer API Key 가 **400 으로 거부**됩니다. API Key 모드에서는 `api-key` 만 씁니다.
- 채팅과 임베딩은 스타터가 분리되어 있어 API 키를 **양쪽에 각각** 지정해야 합니다.
- Spring AI 2.0.1 의 기본 임베딩 모델 `text-embedding-004` 는 **2026-01-14 에 Google 이
  서비스를 종료**했습니다. `gemini-embedding-001` 로 교체해야 합니다.
- `gemini-embedding-001` 의 기본 차원은 3072 인데 **pgvector HNSW 인덱스 상한은 2000** 입니다.
  MRL 로 학습된 모델이라 `dimensions: 1536` 으로 잘라 써도 성능이 유지됩니다.
- `gemini-2.5-flash` 는 2026-09 에 **신규 사용자 차단**되어 `404 ... no longer available to
  new users` 를 던집니다. 그런데 `ListModels` 응답에는 **여전히 목록에 남아 있습니다.**
  모델이 실제로 쓸 수 있는지는 목록이 아니라 `generateContent` 를 한 번 호출해 봐야 압니다.

```bash
# 이 키로 그 모델이 진짜 되는지 확인하는 방법
KEY=$(grep '^GEMINI_API_KEY=' .env | cut -d= -f2-)
curl -sS "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$KEY" \
  -H 'Content-Type: application/json' -d '{"contents":[{"parts":[{"text":"1+1"}]}]}'
```
