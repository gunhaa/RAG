package com.gunha.rag.debug;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code /api/**} 요청의 시작과 끝에 트레이스를 열고 닫는다.
 *
 * <p>컨트롤러 메서드가 아니라 필터에서 여는 이유: 예외가 났을 때
 * {@code @ExceptionHandler} 까지 같은 트레이스가 살아있어야 한다. 실패한 요청의 트레이스가
 * 가장 쓸모 있기 때문이다 — Gemini 가 404 로 거부한 응답 본문이 여기에 담긴다.
 *
 * <p>ThreadLocal 을 쓰므로 {@code finally} 에서 반드시 지운다. 톰캣은 스레드를 재사용해서,
 * 지우지 않으면 다음 요청이 이전 요청의 이벤트를 물려받는다.
 */
@Component
public class TraceFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {

		if (!request.getRequestURI().startsWith("/api/")) {
			chain.doFilter(request, response);
			return;
		}

		TraceContext.start();
		try {
			chain.doFilter(request, response);
		}
		finally {
			TraceContext.clear();
		}
	}

}
