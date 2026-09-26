package com.gunha.rag.search;

/**
 * 검색으로 가져온 청크 하나. UI 에 "이 답변의 근거" 로 그대로 표시된다.
 *
 * @param source 출처 파일명 (예: 01-근무시간.md)
 * @param title  마크다운 섹션 제목
 * @param score  유사도 (1.0 에 가까울수록 유사)
 * @param text   청크 본문
 */
public record RetrievedChunk(String source, String title, double score, String text) {
}
