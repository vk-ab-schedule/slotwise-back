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
