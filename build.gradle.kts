plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.gunha"
version = "0.0.1-SNAPSHOT"
description = "gunha company RAG chatbot"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

extra["springAiVersion"] = "2.0.1"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.ai:spring-ai-markdown-document-reader")
	implementation("org.springframework.ai:spring-ai-starter-model-google-genai")
	implementation("org.springframework.ai:spring-ai-starter-model-google-genai-embedding")
	implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")

	// compose.yaml 의 pgvector 를 자동 기동하는 지원. 빌드 산출물(jar)에는 넣지 않되
	// 테스트에서도 컨테이너가 떠야 하므로 development + test 양쪽에만 올린다.
	testAndDevelopmentOnly("org.springframework.boot:spring-boot-docker-compose")
	testAndDevelopmentOnly("org.springframework.ai:spring-ai-spring-boot-docker-compose")

	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
	}
}

/**
 * 프로젝트 루트의 `.env` 를 읽어 Map 으로 만든다.
 *
 * 앱은 GEMINI_API_KEY 를 환경변수로 받는데, 매 셸마다 export 하는 것은 번거롭고
 * 셸 히스토리에 키가 남는다. `.env`(gitignore 대상)에 한 번 적어두고 Gradle 이
 * bootRun / test 프로세스에 넘겨준다. 형식은 `KEY=value`, `#` 은 주석.
 */
fun dotenv(): Map<String, String> {
	val envFile = file(".env")
	if (!envFile.exists()) {
		return emptyMap()
	}
	return envFile.readLines()
		.map(String::trim)
		.filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
		.associate { line ->
			val (key, value) = line.split("=", limit = 2)
			key.trim() to value.trim().removeSurrounding("\"").removeSurrounding("'")
		}
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
	environment(dotenv())
}

tasks.withType<Test> {
	useJUnitPlatform()
	// 키가 없으면 RagApplicationTests 는 스스로 skip 된다 (@EnabledIfEnvironmentVariable).
	environment(dotenv())
}
