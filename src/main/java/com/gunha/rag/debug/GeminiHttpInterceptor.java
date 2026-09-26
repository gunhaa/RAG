package com.gunha.rag.debug;

import java.io.IOException;
import java.util.Set;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;

import org.springframework.stereotype.Component;

/**
 * Gemini 로 나가는 HTTP 요청·응답 전문을 그대로 트레이스에 담는다.
 *
 * <p>Spring AI 의 advisor 로는 여기까지 볼 수 없다. advisor 는 {@code ChatClientRequest} 라는
 * 자바 객체만 보여주고, 그것이 실제로 어떤 JSON 으로 직렬화돼 나갔는지는 알려주지 않는다.
 * 모델이 400/404 를 던질 때 원인은 대개 그 JSON 에 있다.
 *
 * <p>Google GenAI SDK 는 내부적으로 OkHttp 를 쓰고, {@code ClientOptions.customHttpClient()} 로
 * 클라이언트를 갈아끼울 수 있다. 그 구멍이 없었다면 JVM 프록시를 세우는 수밖에 없었다.
 *
 * <p>채팅과 임베딩이 같은 {@code Client} 를 공유하므로({@link GenAiTraceConfig})
 * {@code :generateContent} 와 {@code :embedContent} 가 한 타임라인에 시간순으로 섞여 들어온다.
 */
@Component
public class GeminiHttpInterceptor implements Interceptor {

	/** 임베딩 응답은 1536개 float 가 들어와 한 건이 20KB 를 넘는다. 화면이 죽지 않게 자른다. */
	private static final long MAX_BODY_BYTES = 128 * 1024L;

	private static final String MASK = "***masked***";

	/** API 키가 실려 나가는 헤더. 화면·로그에 노출되면 안 된다. */
	private static final Set<String> SECRET_HEADERS = Set.of("x-goog-api-key", "authorization");

	@Override
	public Response intercept(Chain chain) throws IOException {
		Request request = chain.request();

		long startedNanos = System.nanoTime();
		Response response;
		try {
			response = chain.proceed(request);
		}
		catch (IOException | RuntimeException ex) {
			// 타임아웃·DNS 실패는 응답이 없다. 요청 전문만이라도 남겨야 원인을 볼 수 있다.
			TraceContext.record(TracePhase.GEMINI_HTTP,
					"%s %s → 전송 실패: %s".formatted(request.method(), endpoint(request), ex.getClass().getSimpleName()),
					requestTranscript(request) + "\n\n--- 응답 없음 ---\n\n" + ex,
					(System.nanoTime() - startedNanos) / 1_000_000L);
			throw ex;
		}
		long durationMs = (System.nanoTime() - startedNanos) / 1_000_000L;

		// 트레이스가 없으면(시작 시 색인 등) 굳이 본문을 복사하지 않는다.
		if (TraceContext.current() == null) {
			return response;
		}

		// peekBody 는 스트림을 소비하지 않는 복사본이다. response.body().string() 을 쓰면
		// SDK 가 읽을 것이 남지 않아 "closed" 오류가 난다.
		String responseBody = response.peekBody(MAX_BODY_BYTES).string();

		String transcript = requestTranscript(request)
				+ "\n\n--- %dms ---\n\n".formatted(durationMs)
				+ responseTranscript(response, responseBody);

		TraceContext.record(TracePhase.GEMINI_HTTP,
				"%s %s → %d (%s)".formatted(request.method(), endpoint(request), response.code(),
						size(responseBody)),
				transcript, durationMs);

		return response;
	}

	private String requestTranscript(Request request) {
		StringBuilder sb = new StringBuilder();
		sb.append(request.method()).append(' ').append(maskUrl(request.url().toString())).append('\n');
		appendHeaders(sb, request.headers());
		String body = requestBody(request);
		if (!body.isEmpty()) {
			sb.append('\n').append(body);
		}
		return sb.toString();
	}

	private String responseTranscript(Response response, String body) {
		StringBuilder sb = new StringBuilder();
		sb.append(response.protocol().toString().toUpperCase()).append(' ').append(response.code());
		if (!response.message().isBlank()) {
			sb.append(' ').append(response.message());
		}
		sb.append('\n');
		appendHeaders(sb, response.headers());
		if (!body.isEmpty()) {
			sb.append('\n').append(body);
			if (body.length() >= MAX_BODY_BYTES) {
				sb.append("\n\n… 이후 잘림 (상한 ").append(MAX_BODY_BYTES / 1024).append("KB)");
			}
		}
		return sb.toString();
	}

	private void appendHeaders(StringBuilder sb, Headers headers) {
		for (int i = 0; i < headers.size(); i++) {
			String name = headers.name(i);
			String value = SECRET_HEADERS.contains(name.toLowerCase()) ? MASK : headers.value(i);
			sb.append(name).append(": ").append(value).append('\n');
		}
	}

	private String requestBody(Request request) {
		RequestBody body = request.body();
		if (body == null) {
			return "";
		}
		try {
			Buffer buffer = new Buffer();
			body.writeTo(buffer);
			return buffer.readUtf8();
		}
		catch (IOException ex) {
			return "(요청 본문을 읽을 수 없음: " + ex.getMessage() + ")";
		}
	}

	/** 키가 쿼리 파라미터로 실려도 새지 않도록 막는다. */
	private String maskUrl(String url) {
		return url.replaceAll("([?&]key=)[^&]*", "$1" + MASK);
	}

	/** {@code models/gemini-3.8-flash:generateContent} 처럼 마지막 경로만. */
	private String endpoint(Request request) {
		java.util.List<String> segments = request.url().pathSegments();
		return segments.isEmpty() ? request.url().encodedPath() : segments.get(segments.size() - 1);
	}

	private String size(String body) {
		int bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
		return bytes < 1024 ? bytes + "B" : "%.1fKB".formatted(bytes / 1024.0);
	}

}
