package com.gunha.rag.chat;

import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * {@code debug} 프로파일에서만 켜지는 관찰 장치.
 *
 * <p>최종 답변만 보면 "검색이 실패해서 틀린 답" 인지 "검색은 됐는데 LLM 이 근거를 무시한 답"
 * 인지 구분할 수 없다. {@link SimpleLoggerAdvisor} 가 ChatClient 로 들어가는 프롬프트와
 * 나오는 응답 원문을 남기므로, 도구 결과가 프롬프트에 실제로 들어갔는지 확인할 수 있다.
 *
 * <p>주의: {@code Advisor} 를 빈으로 올려도 ChatClient 에 자동으로 붙지 않는다.
 * {@code ChatClientAutoConfiguration} 이 빌더에 적용하는 것은 {@link ChatClientBuilderCustomizer} 와
 * {@code ChatClientCustomizer} 뿐이므로, 반드시 이 훅을 통해 등록해야 한다.
 */
@Configuration
@Profile("debug")
public class ChatDebugConfig {

	/**
	 * 오토컨피그가 만든 {@code ChatClient.Builder} 에 로깅 advisor 를 얹는다.
	 * {@link ChatClientConfig} 가 이 빌더를 그대로 받아 쓰므로 운영 코드는 손대지 않는다.
	 */
	@Bean
	ChatClientBuilderCustomizer loggingAdvisorCustomizer() {
		return builder -> builder.defaultAdvisors(new SimpleLoggerAdvisor());
	}

}
