plugins {
    id("slotwise.pure-java")
}

description = "Seeded generator of synthetic datasets"

dependencies {
    api(project(":domain-model"))
}
