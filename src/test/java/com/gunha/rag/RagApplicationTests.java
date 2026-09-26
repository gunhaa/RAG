package com.gunha.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.boot.test.context.SpringBootTest;

/**
 * 컨텍스트 로딩에는 Gemini API 키와 실행 중인 Docker 가 모두 필요하므로,
 * 키가 없는 환경에서는 건너뛴다. (키 없이도 도는 검증은 {@link com.gunha.rag.index.DocumentChunkingTest})
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class RagApplicationTests {

	@Test
	void contextLoads() {
	}

}
