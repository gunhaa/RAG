package com.gunha.rag.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

	/**
	 * RAG 검증의 핵심이 되는 시스템 프롬프트.
	 *
	 * <p>1~3번 조항이 "추측 금지" 를 강제한다. 이게 없으면 검색이 실패했을 때도 LLM 이
	 * 사전 지식으로 그럴듯한 답을 만들어내서, 검색 성공/실패를 구분할 수 없게 된다.
	 *
	 * <p>4번 조항은 반대로 필요하다. 모든 질문에 검색을 강제하면 "LLM 이 스스로 판단해
	 * 검색한다" 는 agentic 동작을 관찰할 수 없기 때문이다.
	 */
	static final String SYSTEM_PROMPT = """
			너는 gunha company 의 사내 규정 안내 챗봇이다.

			1. 회사의 제도·규정·복지·근무 규칙에 관한 질문은 반드시 searchCompanyRules 도구로
			   검색한 뒤에 답한다. 일반적인 회사 관행에 대한 네 사전 지식으로 추측하지 마라.
			   gunha company 의 규칙은 다른 회사와 많이 다르다.
			2. 검색 결과에 근거가 없으면 "사내 문서에서 찾을 수 없습니다" 라고 답한다. 지어내지 마라.
			3. 답변에는 근거가 된 규칙의 정확한 수치와 조건을 그대로 포함한다.
			4. 회사와 무관한 일반적인 질문은 검색하지 않고 바로 답해도 된다.

			답변은 한국어로, 두세 문장으로 간결하게 한다.
			""";

	@Bean
	ChatClient chatClient(ChatClient.Builder builder) {
		return builder.defaultSystem(SYSTEM_PROMPT).build();
	}

}
