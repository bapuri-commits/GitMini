plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

group = "kr.bapuri"
version = "1.1.1"

repositories {
    mavenCentral()
}

javafx {
    version = "21.0.2"
    modules("javafx.controls", "javafx.fxml")
}

application {
    mainClass.set("com.gitmini.Launcher")
}

dependencies {
    // UI Theme
    implementation("io.github.mkpaz:atlantafx-base:2.0.0")

    // JSON
    implementation("com.google.code.gson:gson:2.13.2")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.27")

    // Test
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        showExceptions = true
        showCauses = true
    }
}

// ========== jpackage: 포터블 .exe 빌드 ==========

tasks.register("jpackageImage") {
    dependsOn("installDist")
    group = "distribution"
    description = "Creates a portable app image (.exe) using jpackage"

    doLast {
        val inputDir = layout.buildDirectory.dir("install/GitMini/lib").get().asFile
        val outputDir = layout.buildDirectory.dir("jpackage").get().asFile
        val iconFile = file("src/main/resources/icons/gitmini.ico")

        // 출력 디렉토리 정리
        if (outputDir.exists()) outputDir.deleteRecursively()
        outputDir.mkdirs()

        // jpackage 버전은 순수 숫자만 허용 (SNAPSHOT 등 불가)
        val appVersion = project.version.toString()
            .replace("-SNAPSHOT", "")
            .replace(Regex("[^0-9.]"), "")
            .ifEmpty { "1.0.0" }

        val command = mutableListOf(
            "jpackage",
            "--input", inputDir.absolutePath,
            "--main-jar", "GitMini-${project.version}.jar",
            "--main-class", "com.gitmini.Launcher",
            "--name", "GitMini",
            "--app-version", appVersion,
            "--vendor", "GitMini",
            "--description", "Minimal Git Desktop Client",
            "--type", "app-image",
            "--dest", outputDir.absolutePath,
            // JavaFX 모듈 접근 허용 (non-modular 앱)
            "--java-options", "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED"
        )

        if (iconFile.exists()) {
            command.addAll(listOf("--icon", iconFile.absolutePath))
        }

        println("jpackage 실행: ${command.joinToString(" ")}")

        val process = ProcessBuilder(command)
            .inheritIO()
            .start()

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("jpackage failed with exit code $exitCode")
        }

        println("✓ App image created at: ${outputDir.resolve("GitMini")}")
    }
}
