plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.5.12"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

extra["springAiVersion"] = "1.1.4"

dependencyManagement {
	imports {
		mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.ai:spring-ai-starter-model-openai")
	implementation("org.springframework.ai:spring-ai-advisors-vector-store")
	implementation("org.springframework.ai:spring-ai-starter-vector-store-qdrant")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.liquibase:liquibase-core")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

val frontendDir = layout.projectDirectory.dir("frontend")
val frontendOutputDir = layout.projectDirectory.dir("src/main/resources/static")
val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
val unixShell = listOfNotNull(System.getenv("SHELL"), "/bin/zsh", "/bin/bash", "/usr/bin/bash", "/bin/sh")
	.first { file(it).exists() }

fun Exec.runInShell(command: String) {
	if (isWindows) {
		commandLine("cmd", "/c", command)
	} else {
		val shellFlag = if (unixShell.endsWith("bash") || unixShell.endsWith("zsh")) "-lc" else "-c"
		commandLine(unixShell, shellFlag, command)
	}
}

val npmInstallFrontend by tasks.registering(Exec::class) {
	workingDir = frontendDir.asFile
	inputs.files(
		frontendDir.file("package.json"),
		frontendDir.file("package-lock.json"),
	)
	outputs.dir(frontendDir.dir("node_modules"))
	runInShell("npm ci")
}

val buildFrontend by tasks.registering(Exec::class) {
	dependsOn(npmInstallFrontend)
	workingDir = frontendDir.asFile
	inputs.files(
		frontendDir.file("package.json"),
		frontendDir.file("package-lock.json"),
		frontendDir.file("angular.json"),
		frontendDir.file("tsconfig.json"),
		frontendDir.file("tsconfig.app.json"),
	)
	inputs.dir(frontendDir.dir("public"))
	inputs.dir(frontendDir.dir("src"))
	outputs.dir(frontendOutputDir)
	runInShell("npm run build -- --output-path ../src/main/resources/static")
}

tasks.register("buildFronted") {
	group = "build"
	description = "Alias for buildFrontend."
	dependsOn(buildFrontend)
}

tasks.named("processResources") {
	dependsOn(buildFrontend)
}
