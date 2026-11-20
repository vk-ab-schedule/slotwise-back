plugins {
    id("slotwise.pure-java")
}

description = "CP-SAT scheduling solver"

dependencies {
    api(project(":domain-model"))
    implementation(libs.ortools)
    testImplementation(project(":datagen"))
}

tasks.test {
    maxHeapSize = "2g"
}
