// Shared settings for every module: Java toolchain, Lombok, JUnit.
// Dependency versions come from the Spring Boot BOM, so all modules use the same ones.
// The BOM only constrains versions; it does not add Spring to the classpath.

plugins {
    java
}

group = "ru.vk.itmo.scheduler"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val libs = the<VersionCatalogsExtension>().named("libs")
val springBootBom = dependencies.platform(libs.findLibrary("spring-boot-dependencies").get())

dependencies {
    listOf(
        "implementation", "compileOnly", "annotationProcessor",
        "testImplementation", "testCompileOnly", "testAnnotationProcessor",
    ).forEach { add(it, springBootBom) }

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
