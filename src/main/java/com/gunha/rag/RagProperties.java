package com.gunha.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 {@code rag.*} 설정.
 *
 * @param docsLocation        색인 대상 문서 위치 (Ant 패턴)
 * @param topK                한 번 검색할 때 가져올 청크 수
 * @param similarityThreshold 이 값 미만의 유사도는 버린다 (0.0 ~ 1.0)
 * @param toolsEnabled        false 면 LLM 에게 검색 도구를 주지 않는다 (negative control 실험)
 * @param indexOnStartup      false 면 시작 시 색인을 건너뛴다
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(
		String docsLocation,
		int topK,
		double similarityThreshold,
		boolean toolsEnabled,
		boolean indexOnStartup) {
}
