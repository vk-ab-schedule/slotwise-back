plugins {
    id("slotwise.java-conventions")
    alias(libs.plugins.spring.boot)
}

description = "Spring Boot application: API, security, persistence, solver jobs"

dependencies {
    implementation(project(":domain-model"))
    implementation(project(":solver-greedy"))
    implementation(project(":datagen"))

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation(libs.springdoc.openapi.webmvc.ui)
    developmentOnly(platform(libs.spring.boot.dependencies))
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}

// One executable jar with a stable name, so the Dockerfile does not depend on the version.
tasks.bootJar {
    archiveFileName = "slotwise-back.jar"
}

tasks.jar {
    enabled = false
}

tasks.register<Test>("exportOpenApi") {
    group = "documentation"
    description = "Export springdoc JSON to build/openapi/slotwise.json"
    dependsOn(tasks.testClasses)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter {
        includeTestsMatching("*OpenApiContractTests.exportsConsumerUsableSpecificationWithoutPostMvpOperations")
    }
    val schemaFile = layout.buildDirectory.file("openapi/slotwise.json")
    systemProperty("slotwise.openapi.output", schemaFile.get().asFile.absolutePath)
    environment("SPRING_DOCKER_COMPOSE_ENABLED", "false")
    outputs.file(schemaFile)
}
