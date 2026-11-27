// For domain-model, solvers and datagen: plain Java libraries that must not depend on Spring
// (D-10). The check fails the build if a Spring jar gets onto the classpath, even transitively.
// The Spring Boot BOM from slotwise.java-conventions is allowed: it carries versions, not code.

plugins {
    id("slotwise.java-conventions")
    `java-library`
}

val modulePath = project.path

val verifyNoSpring = tasks.register("verifyNoSpring") {
    group = "verification"
    description = "Fails if a Spring jar is on the runtime classpath of this module."
    val artifacts = configurations.runtimeClasspath.flatMap { it.incoming.artifacts.resolvedArtifacts }
    inputs.files(configurations.runtimeClasspath)
    doLast {
        val springJars = artifacts.get()
            .mapNotNull { it.id.componentIdentifier as? ModuleComponentIdentifier }
            .filter { it.group.startsWith("org.springframework") }
            .map { "${it.group}:${it.module}" }
        if (springJars.isNotEmpty()) {
            throw GradleException("Spring must not be a dependency of $modulePath: $springJars")
        }
    }
}

tasks.named("check") {
    dependsOn(verifyNoSpring)
}
