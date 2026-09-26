package com.gunha.rag.index;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 마크다운 한 파일을 heading 단위 청크로 자른다.
 *
 * <p>{@link MarkdownDocumentReader} 는 heading 을 만날 때마다 새 Document 로 끊기 때문에
 * {@code ##} 섹션이 그대로 청크 경계가 된다. 여기에 {@code TokenTextSplitter} 를 추가로
 * 적용하지 않는 것이 의도다 — 규칙 하나가 두 청크로 쪼개지면 검색이 앞부분만 가져와서
 * LLM 이 조건을 빠뜨린 답을 확신하게 된다.
 */
@Component
public class MarkdownChunker {

	/** 제목만 있고 본문이 없는 조각(예: 파일 맨 위의 h1)을 걸러내는 기준. */
	static final int MIN_CHUNK_LENGTH = 30;

	public List<Document> chunk(Resource resource) {
		String source = resource.getFilename();
		MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
				.withIncludeCodeBlock(true)
				.withIncludeBlockquote(true)
				.build();

		List<Document> chunks = new ArrayList<>();
		for (Document raw : new MarkdownDocumentReader(resource, config).get()) {
			String text = raw.getText();
			if (text == null || text.strip().length() < MIN_CHUNK_LENGTH) {
				continue; // 본문 없는 제목 조각은 검색에 방해만 된다
			}

			String title = String.valueOf(raw.getMetadata().getOrDefault("title", ""));
			chunks.add(Document.builder()
					// 같은 파일·같은 제목이면 항상 같은 ID → 재시작 시 INSERT 대신 UPDATE 가 되어
					// 중복 색인이 생기지 않는다. PgVectorStore 는 ON CONFLICT DO UPDATE 로 동작한다.
					.id(deterministicId(source, title))
					.text(text)
					.metadata("source", source)
					.metadata("title", title)
					.build());
		}
		return chunks;
	}

	static String deterministicId(String source, String title) {
		byte[] key = (source + "#" + title).getBytes(StandardCharsets.UTF_8);
		return UUID.nameUUIDFromBytes(key).toString();
	}

}
