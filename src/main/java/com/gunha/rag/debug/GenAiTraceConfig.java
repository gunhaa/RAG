package com.gunha.rag.debug;

import java.io.IOException;
import java.time.Duration;

import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import okhttp3.OkHttpClient;

import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gemini 호출을 들여다볼 수 있게 SDK 클라이언트를 직접 조립한다.
 *
 * <p>오토컨피그가 만드는 {@code Client} 는 OkHttp 클라이언트를 내부에서 생성해버려서 끼어들
 * 틈이 없다. 그래서 여기서 {@code @ConditionalOnMissingBean} 을 이겨내고 같은 타입의 빈을
 * 직접 정의한다.
 *
 * <p><b>왜 빈이 두 개인가</b>: Spring AI 는 채팅과 임베딩에 서로 다른 경로로 클라이언트를
 * 만든다. 채팅은 {@code Client} 빈을 쓰고, 임베딩은
 * {@code GoogleGenAiEmbeddingConnectionDetails} 안에서 자체 생성한다. 채팅 쪽만 바꾸면
 * 임베딩 HTTP 는 잡히지 않으므로, 임베딩에도 같은 {@code Client} 를 주입해 인터셉터를
 * 공유시킨다. 그 결과 {@code :generateContent} 와 {@code :embedContent} 가 한 타임라인에
 * 시간순으로 모인다.
 */
@Configuration
public class GenAiTraceConfig {

	@Bean
	OkHttpClient geminiHttpClient(GeminiHttpInterceptor interceptor) {
		return new OkHttpClient.Builder()
				.addInterceptor(interceptor)
				// 기본값(10초)으로는 도구 호출이 끼는 긴 응답에서 끊긴다.
				.readTimeout(Duration.ofSeconds(180))
				.callTimeout(Duration.ofSeconds(240))
				.build();
	}

	@Bean
	Client googleGenAiClient(OkHttpClient geminiHttpClient, GoogleGenAiConnectionProperties properties) {
		return Client.builder()
				.apiKey(properties.getApiKey())
				.clientOptions(ClientOptions.builder().customHttpClient(geminiHttpClient).build())
				.build();
	}

	@Bean
	GoogleGenAiEmbeddingConnectionDetails googleGenAiEmbeddingConnectionDetails(Client googleGenAiClient,
			GoogleGenAiEmbeddingConnectionProperties properties) throws IOException {
		return GoogleGenAiEmbeddingConnectionDetails.builder()
				.apiKey(properties.getApiKey())
				.genAiClient(googleGenAiClient)
				.build();
	}

	/**
	 * Advisor 를 빈으로 올려도 ChatClient 에 자동으로 붙지 않는다. 오토컨피그가 빌더에
	 * 적용하는 것은 {@link ChatClientBuilderCustomizer} 뿐이므로 이 훅으로 등록한다.
	 */
	@Bean
	ChatClientBuilderCustomizer promptTraceCustomizer() {
		return builder -> builder.defaultAdvisors(new PromptTraceAdvisor());
	}

}
