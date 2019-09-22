plugins {
    id("java-common")
    kotlin("jvm")
}

dependencies {
    implementation(project(":theta-cfa"))
    implementation(project(":theta-solver"))
    implementation(kotlin("stdlib-jdk8"))
    testImplementation(project(":theta-solver"))
    testImplementation(project(":theta-solver-z3"))
}
repositories {
    mavenCentral()
}