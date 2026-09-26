package com.gunha.rag.chat;

import java.util.List;

import com.gunha.rag.debug.TraceEvent;
import com.gunha.rag.search.ToolInvocation;

/**
 * {@code POST /api/chat} 응답.
 *
 * @param answer       LLM 의 최종 답변
 * @param toolsEnabled 이번 요청에서 LLM 에게 검색 도구를 주었는지 (negative control 구분용)
 * @param invocations  LLM 이 실제로 검색한 기록. 비어 있으면 검색 없이 답한 것이다.
 * @param trace        이 요청을 처리하는 동안의 전 과정. 화면의 디버그 타임라인이 이걸 그린다.
 */
public record ChatAnswer(String answer, boolean toolsEnabled, List<ToolInvocation> invocations,
		List<TraceEvent> trace) {
}
