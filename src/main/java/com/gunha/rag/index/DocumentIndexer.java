package com.gunha.rag.index;

import java.util.ArrayList;
import java.util.List;

import com.gunha.rag.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * 앱이 뜰 때 {@code docs/*.md} 를 읽어 벡터 스토어에 색인한다.
 *
 * <p>청크마다 결정적 ID 를 쓰기 때문에(자세한 내용은 {@link MarkdownChunker}) 여러 번
 * 재시작해도 같은 행이 갱신될 뿐 중복이 쌓이지 않는다.
 */
@Component
public class DocumentIndexer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DocumentIndexer.class);

	private final VectorStore vectorStore;

	private final MarkdownChunker chunker;

	private final RagProperties properties;

	public DocumentIndexer(VectorStore vectorStore, MarkdownChunker chunker, RagProperties properties) {
		this.vectorStore = vectorStore;
		this.chunker = chunker;
		this.properties = properties;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		if (!this.properties.indexOnStartup()) {
			log.info("rag.index-on-startup=false → 색인을 건너뜁니다.");
			return;
		}

		Resource[] resources = new PathMatchingResourcePatternResolver()
				.getResources(this.properties.docsLocation());
		if (resources.length == 0) {
			log.warn("색인할 문서가 없습니다. location={}", this.properties.docsLocation());
			return;
		}

		long startedAt = System.currentTimeMillis();
		List<Document> chunks = new ArrayList<>();
		for (Resource resource : resources) {
			List<Document> fileChunks = this.chunker.chunk(resource);
			log.debug("{} → 청크 {}개", resource.getFilename(), fileChunks.size());
			chunks.addAll(fileChunks);
		}

		// 임베딩 API 호출이 여기서 일어난다. 문서 수십 개면 몇 초, 비용은 1원 미만이다.
		this.vectorStore.add(chunks);
		log.info("색인 완료: 문서 {}개 → 청크 {}개, {}ms",
				resources.length, chunks.size(), System.currentTimeMillis() - startedAt);
	}

}
