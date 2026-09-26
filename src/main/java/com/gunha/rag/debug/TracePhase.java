package com.gunha.rag.debug;

/**
 * 디버그 타임라인에서 한 이벤트가 어느 계층에서 나온 것인지.
 *
 * <p>화면에서 계층별로 접고 펼 수 있도록 단계를 구분한다. 같은 한 번의 질문이 이 순서대로
 * 흐르므로, 어느 계층에서 기대와 달라졌는지를 위에서 아래로 좁혀 갈 수 있다.
 */
public enum TracePhase {

	/** ChatClient 로 들어간 요청. 시스템 프롬프트·사용자 질문·도구 정의가 모두 들어있다. */
	PROMPT,

	/** Gemini 와의 실제 HTTP 왕복 전문. 채팅 호출과 임베딩 호출이 모두 여기로 들어온다. */
	GEMINI_HTTP,

	/** LLM 이 검색 도구를 호출한 사실과 그 인자. */
	TOOL,

	/** 벡터 스토어 유사도 검색의 조건과 결과. */
	VECTOR_SEARCH,

	/** ChatClient 가 최종적으로 돌려준 응답과 토큰 사용량. */
	MODEL_RESPONSE,

	/** 요청 처리 중 애플리케이션·라이브러리가 남긴 로그 한 줄. */
	LOG,

	/** 처리 실패. */
	ERROR

}
