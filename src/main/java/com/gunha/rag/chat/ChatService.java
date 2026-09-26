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

		String answer = spec.call().content();
		// 트레이스는 TraceFilter 가 열어 둔 것을 그대로 읽는다 (요청 밖이면 빈 목록).
		return new ChatAnswer(answer, this.properties.toolsEnabled(), tools.invocations(), TraceContext.events());
	}

}
