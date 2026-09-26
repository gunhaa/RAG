package com.gunha.rag.index;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gemini API 키도 Docker 도 필요 없는 오프라인 검증.
 *
 * <p>"규칙 1개 = 청크 1개" 가 지켜지는지가 이 프로젝트 정확도의 전제이므로, 문서를 고칠 때마다
 * 청킹이 의도대로 되는지 여기서 먼저 확인한다.
 */
class DocumentChunkingTest {

	private final MarkdownChunker chunker = new MarkdownChunker();

	private Resource[] docs() throws IOException {
		return new PathMatchingResourcePatternResolver().getResources("file:./docs/*.md");
	}

	@Test
	@DisplayName("docs 의 모든 마크다운이 읽히고 청크가 만들어진다")
	void 문서를_모두_읽는다() throws IOException {
		Resource[] docs = docs();
		assertThat(docs).isNotEmpty();

		for (Resource doc : docs) {
			assertThat(this.chunker.chunk(doc))
					.as("%s 의 청크", doc.getFilename())
					.isNotEmpty();
		}
	}

	@Test
	@DisplayName("모든 청크가 source·title 메타데이터와 본문을 갖는다")
	void 청크마다_출처와_제목이_붙는다() throws IOException {
		for (Resource doc : docs()) {
			for (Document chunk : this.chunker.chunk(doc)) {
				assertThat(chunk.getMetadata().get("source")).isEqualTo(doc.getFilename());
				assertThat((String) chunk.getMetadata().get("title")).isNotBlank();
				assertThat(chunk.getText()).hasSizeGreaterThanOrEqualTo(MarkdownChunker.MIN_CHUNK_LENGTH);
			}
		}
	}

	@Test
	@DisplayName("하나의 규칙이 여러 청크로 쪼개지지 않는다 (예외 조항 유실 방지)")
	void 규칙이_쪼개지지_않는다() throws IOException {
		Resource 근무시간 = new PathMatchingResourcePatternResolver()
				.getResource("file:./docs/01-근무시간.md");

		List<Document> chunks = this.chunker.chunk(근무시간);

		// '수요일 조기 퇴근제' 섹션의 핵심 수치(15:00)와 "예외는 없다"가 같은 청크 안에 있어야 한다.
		Document 수요일 = chunks.stream()
				.filter(c -> String.valueOf(c.getMetadata().get("title")).contains("수요일"))
				.findFirst()
				.orElseThrow();
		assertThat(수요일.getText()).contains("15:00").contains("예외는 없다");
	}

	@Test
	@DisplayName("같은 파일·같은 제목이면 항상 같은 ID (재시작 시 중복 색인 방지)")
	void ID가_결정적이다() throws IOException {
		Resource doc = new PathMatchingResourcePatternResolver().getResource("file:./docs/02-휴가제도.md");

		List<String> first = this.chunker.chunk(doc).stream().map(Document::getId).toList();
		List<String> second = this.chunker.chunk(doc).stream().map(Document::getId).toList();

		assertThat(first).isEqualTo(second).doesNotHaveDuplicates();
	}

}
