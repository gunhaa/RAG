package com.gunha.rag.debug;

/**
 * 디버그 타임라인의 한 줄.
 *
 * @param atMs       요청 시작 시점부터의 경과 시간(ms)
 * @param durationMs 이 작업에 걸린 시간(ms). 측정 대상이 아니면 0
 * @param phase      어느 계층에서 나온 이벤트인지
 * @param label      한 줄 요약 (타임라인에 접힌 상태로 보이는 부분)
 * @param detail     펼쳤을 때 보이는 전문. HTTP 는 req/res 원문이 그대로 들어간다
 */
public record TraceEvent(long atMs, long durationMs, TracePhase phase, String label, String detail) {
}
