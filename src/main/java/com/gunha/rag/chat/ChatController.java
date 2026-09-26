package com.gunha.rag.chat;

import java.util.List;

import com.gunha.rag.debug.TraceContext;
import com.gunha.rag.debug.TraceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

	private static final Logger log = LoggerFactory.getLogger(ChatController.class);

	private final ChatService chatService;

	public ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	@PostMapping("/chat")
	public ChatAnswer chat(@RequestBody ChatRequest request) {
		return this.chatService.ask(request.question());
	}

	/**
	 * Gemini 호출 실패(잘못된 API 키, 할당량 초과 등)를 화면에서 바로 읽을 수 있게 돌려준다.
	 * 기본 500 응답은 본문이 비어 있어서 원인 파악에 시간이 걸린다.
	 */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleFailure(Exception ex) {
		log.error("채팅 처리 실패", ex);
		String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
		if (message.contains("API key not valid")) {
			message = "GEMINI_API_KEY 가 유효하지 않습니다. 환경변수를 확인하세요. (" + message + ")";
		}
		// 실패한 요청의 트레이스가 가장 쓸모 있다. Gemini 가 거부한 응답 본문이 여기 들어있다.
		// TraceFilter 가 아직 트레이스를 닫지 않았으므로 이 시점에도 읽을 수 있다.
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
				.body(new ErrorResponse(message, TraceContext.events()));
	}

	public record ChatRequest(String question) {
	}

	public record ErrorResponse(String message, List<TraceEvent> trace) {
	}

}
