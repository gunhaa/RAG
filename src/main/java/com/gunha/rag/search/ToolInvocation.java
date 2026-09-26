package com.gunha.rag.search;

import java.util.List;

/**
 * LLM 이 검색 도구를 한 번 호출한 기록.
 *
 * <p>LLM 이 스스로 만들어 넣은 {@code query} 를 그대로 보관한다. 사용자가 던진 질문과
 * 이 질의가 어떻게 다른지 보는 것이 agentic RAG 를 이해하는 가장 빠른 길이다.
 */
public record ToolInvocation(String query, List<RetrievedChunk> chunks) {
}
