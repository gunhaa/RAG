package com.gunha.rag.debug;

import java.util.List;

/**
 * 현재 요청의 {@link RequestTrace} 를 스레드에 매달아 두는 곳.
 *
 * <p>ThreadLocal 을 쓰는 이유가 있다. 기록해야 할 지점들이 서로 호출 관계로 이어지지 않는다.
 * OkHttp 인터셉터는 Spring AI 와 Google SDK 안쪽에서 불리고, 로그 appender 는 Logback 이
 * 부른다. 이들에게 트레이스 객체를 인자로 넘길 방법이 없다.
 *
 * <p><b>전제</b>: 블로킹 호출 경로(ChatClient.call())는 요청을 받은 톰캣 스레드에서 끝까지
 * 처리된다. 스트리밍(Flux)으로 바꾸면 스레드가 갈려서 이 방식이 깨진다.
 */
public final class TraceContext {

	private static final ThreadLocal<RequestTrace> CURRENT = new ThreadLocal<>();

	private TraceContext() {
	}

	static RequestTrace start() {
		RequestTrace trace = new RequestTrace();
		CURRENT.set(trace);
		return trace;
	}

	static void clear() {
		CURRENT.remove();
	}

	/** 트레이스가 없으면 {@code null}. 색인처럼 HTTP 요청 밖에서 도는 경로가 있다. */
	public static RequestTrace current() {
		return CURRENT.get();
	}

	/** 트레이스가 없어도 안전하게 호출할 수 있는 기록 헬퍼. */
	public static void record(TracePhase phase, String label, String detail, long durationMs) {
		RequestTrace trace = CURRENT.get();
		if (trace != null) {
			trace.add(phase, label, detail, durationMs);
		}
	}

	public static void record(TracePhase phase, String label, String detail) {
		record(phase, label, detail, 0L);
	}

	/** 응답에 실어 보낼 이벤트 목록. 트레이스가 없으면 빈 목록. */
	public static List<TraceEvent> events() {
		RequestTrace trace = CURRENT.get();
		return trace != null ? trace.events() : List.of();
	}

}
