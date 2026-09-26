package com.gunha.rag.debug;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.Ordered;

/**
 * Spring AI 계층의 요청·응답을 트레이스에 담는다.
 *
 * <p>{@link GeminiHttpInterceptor} 가 보여주는 것은 직렬화된 JSON 이고, 이쪽은 Spring AI 가
 * 들고 있는 자바 객체다. 둘을 나란히 보는 것이 핵심이다. 프롬프트에는 있는데 JSON 에는
 * 없다면 직렬화 문제이고, JSON 에도 있는데 답이 틀렸다면 모델 문제다.
 *
 * <p>순서를 가장 바깥(HIGHEST_PRECEDENCE 근처)으로 두었다. 안쪽에 있는
 * {@code ToolCallingAdvisor} 가 도구 호출 왕복을 자체적으로 돌리므로, 바깥에서는 "최초 요청"
 * 과 "최종 응답" 한 쌍만 잡힌다. 중간 왕복은 HTTP 전문 쪽에서 전부 보인다.
 */
public class PromptTraceAdvisor implements CallAdvisor {

	@Override
	public String getName() {
		return "promptTrace";
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 100;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		TraceContext.record(TracePhase.PROMPT,
				"ChatClient 요청 — 시스템 프롬프트 + 사용자 질문 + 도구 정의",
				String.valueOf(request));

		long startedNanos = System.nanoTime();
		ChatClientResponse response = chain.nextCall(request);
		long durationMs = (System.nanoTime() - startedNanos) / 1_000_000L;

		TraceContext.record(TracePhase.MODEL_RESPONSE, summarize(response), String.valueOf(response), durationMs);
		return response;
	}

	/** 펼치지 않고도 토큰 사용량과 종료 이유를 알 수 있게 한 줄로 만든다. */
	private String summarize(ChatClientResponse response) {
		ChatResponse chatResponse = (response != null) ? response.chatResponse() : null;
		if (chatResponse == null) {
			return "ChatClient 응답 (본문 없음)";
		}

		StringBuilder sb = new StringBuilder("ChatClient 응답");
		if (chatResponse.getResult() != null && chatResponse.getResult().getMetadata() != null) {
			String finishReason = chatResponse.getResult().getMetadata().getFinishReason();
			if (finishReason != null && !finishReason.isBlank()) {
				sb.append(" — finishReason=").append(finishReason);
			}
		}
		if (chatResponse.getMetadata() != null) {
			Usage usage = chatResponse.getMetadata().getUsage();
			if (usage != null) {
				sb.append(" — 토큰 in=").append(usage.getPromptTokens())
						.append(" out=").append(usage.getCompletionTokens())
						.append(" total=").append(usage.getTotalTokens());
			}
		}
		return sb.toString();
	}

}
