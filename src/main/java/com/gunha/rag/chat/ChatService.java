package com.gunha.rag.chat;

import com.gunha.rag.RagProperties;
import com.gunha.rag.debug.TraceContext;
import com.gunha.rag.search.RuleSearchTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

	private static final Logger log = LoggerFactory.getLogger(ChatService.class);

	private final ChatClient chatClient;

	private final VectorStore vectorStore;

	private final RagProperties properties;

	public ChatService(ChatClient chatClient, VectorStore vectorStore, RagProperties properties) {
		this.chatClient = chatClient;
		this.vectorStore = vectorStore;
		this.properties = properties;
	}

	public ChatAnswer ask(String question) {
		// 요청마다 새 도구 객체를 만들어 넘긴다. 호출 기록이 이 객체에 쌓이므로
		// 빈으로 공유하면 동시 요청끼리 기록이 섞인다.
		RuleSearchTools tools = new RuleSearchTools(this.vectorStore, this.properties);

		ChatClient.ChatClientRequestSpec spec = this.chatClient.prompt().user(question);
		if (this.properties.toolsEnabled()) {
			spec = spec.tools(tools);
		}
		else {
			// negative control: 도구를 주지 않으면 LLM 은 사전 지식으로만 답한다.
			log.info("rag.tools-enabled=false → 검색 도구 없이 응답합니다 (negative control)");
		}

		// call() 은 실행하지 않는다. 지금까지 조립한 요청(시스템 프롬프트·질문·도구)을 굳혀
		// 돌려줄 뿐이고, 실제 호출은 content() 에서 일어난다.
		//
		// content() 한 번에 LLM 왕복은 여러 번이다. Spring AI 가 (모델 구현체가 아니라 advisor
		// 계층에서) "도구 호출이 없는 응답" 이 올 때까지 루프를 돌리므로 generateContent 는
		// (도구 호출 횟수 + 1) 번 나가고, 라운드마다 앞선 왕복이 전부 다시 전송된다.
		//
		// content() 가 주는 것은 최종 텍스트뿐이다. 왕복 내역과 토큰 사용량은 여기서 버려지므로
		// 아래 tools.invocations() 와 트레이스로 본다.
		String answer = spec.call().content();
		// 트레이스는 TraceFilter 가 열어 둔 것을 그대로 읽는다 (요청 밖이면 빈 목록).
		return new ChatAnswer(answer, this.properties.toolsEnabled(), tools.invocations(), TraceContext.events());
	}

}
