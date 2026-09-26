package com.gunha.rag.search;

import java.util.ArrayList;
import java.util.List;

import com.gunha.rag.RagProperties;
import com.gunha.rag.debug.TraceContext;
import com.gunha.rag.debug.TracePhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Gemini 가 필요하다고 판단할 때 직접 호출하는 검색 도구.
 *
 * <p>도구를 하나만 두는 것이 의도다. 도구가 여러 개면 LLM 이 어느 것을 부를지 흔들려서
 * "검색이 안 됐는가" 와 "도구 선택이 틀렸는가" 를 구분할 수 없게 된다.
 *
 * <p><b>이 객체는 요청마다 새로 만들어 쓴다.</b> 스프링 빈이 아니다. 호출 기록을 인스턴스
 * 필드에 모아 그대로 응답에 실어 보내기 때문에, 빈으로 공유하면 요청 간에 기록이 섞인다.
 */
public class RuleSearchTools {

	private static final Logger log = LoggerFactory.getLogger(RuleSearchTools.class);

	private final VectorStore vectorStore;

	private final RagProperties properties;

	private final List<ToolInvocation> invocations = new ArrayList<>();

	public RuleSearchTools(VectorStore vectorStore, RagProperties properties) {
		this.vectorStore = vectorStore;
		this.properties = properties;
	}

	/**
	 * 이 description 이 곧 LLM 에게 주는 프롬프트다. Spring AI 가 이 문장을 Gemini 의
	 * function declaration 으로 그대로 보내므로, LLM 이 검색을 하지 않는 문제는 대개
	 * 코드가 아니라 이 문장을 고쳐서 해결한다.
	 */
	@Tool(name = "searchCompanyRules",
			description = """
					gunha company 의 사내 규정·제도·복지·근무 규칙 문서를 검색한다.
					근무시간, 휴가, 원격근무, 회의, 코드 리뷰, 경비, 장비, 보상, 승진, 교육 등
					회사의 규칙에 관한 질문이라면 반드시 이 도구를 먼저 사용해야 한다.
					gunha company 의 규칙은 일반적인 회사와 다르므로 추측해서는 안 된다.""")
	public String searchCompanyRules(
			@ToolParam(description = "검색할 내용. 예: '수요일 퇴근 시간', '생일 휴가 일수', '점심 법인카드 조건'")
			String query) {

		TraceContext.record(TracePhase.TOOL,
				"LLM 이 searchCompanyRules 호출 — query=\"%s\"".formatted(query),
				"""
						LLM 이 스스로 만든 검색어다. 사용자 질문과 다르다는 점이 중요하다.

						query              : %s
						topK               : %d
						similarityThreshold: %s

						이 문자열이 gemini-embedding-001 로 임베딩된 뒤 pgvector 코사인 검색에 쓰인다.
						(임베딩 HTTP 전문은 바로 다음 GEMINI_HTTP 이벤트에 있다)"""
						.formatted(query, this.properties.topK(), this.properties.similarityThreshold()));

		long startedNanos = System.nanoTime();
		List<Document> found = this.vectorStore.similaritySearch(SearchRequest.builder()
				.query(query)
				.topK(this.properties.topK())
				.similarityThreshold(this.properties.similarityThreshold())
				.build());
		long durationMs = (System.nanoTime() - startedNanos) / 1_000_000L;

		List<RetrievedChunk> chunks = toChunks(found);
		this.invocations.add(new ToolInvocation(query, chunks));
		log.debug("도구 호출 query='{}' → {}건", query, chunks.size());

		TraceContext.record(TracePhase.VECTOR_SEARCH,
				"pgvector 유사도 검색 → %d건".formatted(chunks.size()),
				searchDetail(chunks), durationMs);

		if (chunks.isEmpty()) {
			return "검색 결과가 없습니다. 사내 문서에 해당 내용이 없습니다.";
		}

		StringBuilder sb = new StringBuilder();
		for (RetrievedChunk chunk : chunks) {
			sb.append("[출처: ").append(chunk.source())
					.append(" › ").append(chunk.title()).append("]\n")
					.append(chunk.text()).append("\n\n");
		}
		return sb.toString().strip();
	}

	/** 무엇이 왜 뽑혔는지 — 유사도 순서와 실제 본문을 함께 보여준다. */
	private String searchDetail(List<RetrievedChunk> chunks) {
		if (chunks.isEmpty()) {
			return "임계값(%s) 을 넘는 청크가 없다. LLM 에게는 \"검색 결과가 없습니다\" 가 전달된다."
					.formatted(this.properties.similarityThreshold());
		}
		StringBuilder sb = new StringBuilder("유사도 내림차순. 이 본문이 그대로 도구 결과로 LLM 에게 전달된다.\n");
		for (RetrievedChunk chunk : chunks) {
			sb.append("\n[%.4f] %s › %s\n".formatted(chunk.score(), chunk.source(), chunk.title()))
					.append(chunk.text()).append('\n');
		}
		return sb.toString();
	}

	private List<RetrievedChunk> toChunks(List<Document> documents) {
		if (documents == null) {
			return List.of();
		}
		List<RetrievedChunk> chunks = new ArrayList<>(documents.size());
		for (Document document : documents) {
			Double score = document.getScore();
			chunks.add(new RetrievedChunk(
					String.valueOf(document.getMetadata().getOrDefault("source", "?")),
					String.valueOf(document.getMetadata().getOrDefault("title", "")),
					score != null ? score : 0.0,
					document.getText()));
		}
		return chunks;
	}

	/** 이번 요청에서 LLM 이 도구를 호출한 기록. 비어 있으면 검색 없이 답한 것이다. */
	public List<ToolInvocation> invocations() {
		return List.copyOf(this.invocations);
	}

}
