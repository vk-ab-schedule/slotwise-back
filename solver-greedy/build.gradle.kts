plugins {
    id("slotwise.pure-java")
}

description = "Greedy baseline solver"

dependencies {
    api(project(":domain-model"))
    testImplementation(project(":datagen"))
}
