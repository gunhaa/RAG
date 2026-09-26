package com.gunha.rag.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 한 번의 HTTP 요청 동안 모인 디버그 이벤트.
 *
 * <p>요청 하나에 객체 하나다. {@link TraceContext} 가 스레드에 매달아 두므로 도구 호출이나
 * OkHttp 인터셉터처럼 호출 스택이 멀리 떨어진 지점에서도 같은 트레이스에 기록할 수 있다.
 */
public class RequestTrace {

	/**
	 * 이벤트 상한. 로그를 전부 담기 때문에 DEBUG 레벨을 넓게 열면 수백 줄이 쌓인다.
	 * 상한이 없으면 브라우저로 내려보내는 JSON 이 수 MB 가 되어 화면이 멈춘다.
	 */
	private static final int MAX_EVENTS = 600;

	private final long startedNanos = System.nanoTime();

	private final List<TraceEvent> events = Collections.synchronizedList(new ArrayList<>());

	public void add(TracePhase phase, String label, String detail) {
		add(phase, label, detail, 0L);
	}

	public void add(TracePhase phase, String label, String detail, long durationMs) {
		if (this.events.size() >= MAX_EVENTS) {
			return;
		}
		long atMs = (System.nanoTime() - this.startedNanos) / 1_000_000L;
		this.events.add(new TraceEvent(atMs, durationMs, phase, label, detail));
	}

	public List<TraceEvent> events() {
		synchronized (this.events) {
			return List.copyOf(this.events);
		}
	}

}
