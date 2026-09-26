package com.gunha.rag.debug;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 요청 처리 중 남은 로그를 그대로 타임라인에 끼워 넣는다.
 *
 * <p>덕분에 "애플리케이션 내부에서 생성하는 모든 과정" 이 화면에 들어온다. 우리 코드가 남긴
 * 로그뿐 아니라 {@code debug} 프로파일에서 열어둔 Spring AI 내부 로그와 pgvector 로 나가는
 * SQL 까지 포함된다. 즉 화면에 무엇이 보이는지는 로그 레벨 설정이 결정한다.
 *
 * <p>Logback appender 는 로그를 남긴 스레드에서 동기적으로 불린다. 그래서 그 시점의
 * {@link TraceContext} 가 곧 이 요청의 트레이스다. 요청 밖(시작 시 색인 등)의 로그는
 * 트레이스가 없으므로 자동으로 버려진다.
 */
@Component
public class TraceLogAppender extends AppenderBase<ILoggingEvent> {

	/** 로그 한 줄이 지나치게 길면(예: 임베딩 벡터 덤프) 화면이 무너진다. */
	private static final int MAX_MESSAGE_CHARS = 20_000;

	@PostConstruct
	void attachToRootLogger() {
		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		setContext(context);
		setName("traceLogAppender");
		start();
		context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(this);
	}

	@Override
	protected void append(ILoggingEvent event) {
		if (TraceContext.current() == null) {
			return;
		}

		String detail = truncate(event.getFormattedMessage());
		IThrowableProxy throwable = event.getThrowableProxy();
		if (throwable != null) {
			detail += "\n\n" + throwable.getClassName() + ": " + throwable.getMessage();
		}

		TracePhase phase = (event.getLevel() == Level.ERROR) ? TracePhase.ERROR : TracePhase.LOG;
		TraceContext.record(phase, event.getLevel() + " " + shortLoggerName(event.getLoggerName()), detail);
	}

	/** {@code o.s.a.c.c.advisor.SimpleLoggerAdvisor} 처럼 앞쪽 패키지를 줄인다. */
	private String shortLoggerName(String loggerName) {
		int lastDot = loggerName.lastIndexOf('.');
		if (lastDot < 0) {
			return loggerName;
		}
		StringBuilder sb = new StringBuilder();
		for (String part : loggerName.substring(0, lastDot).split("\\.")) {
			if (!part.isEmpty()) {
				sb.append(part.charAt(0)).append('.');
			}
		}
		return sb + loggerName.substring(lastDot + 1);
	}

	private String truncate(String message) {
		if (message == null) {
			return "";
		}
		return message.length() <= MAX_MESSAGE_CHARS
				? message
				: message.substring(0, MAX_MESSAGE_CHARS) + "\n… 이후 잘림";
	}

}
