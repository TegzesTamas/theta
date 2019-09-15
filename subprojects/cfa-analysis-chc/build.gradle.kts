plugins {
    id("java-common")
    kotlin("jvm")
}

dependencies {
    compile(project(":theta-cfa"))
    compile(project(":theta-common"))
    compile(project(":theta-core"))
    implementation(kotlin("stdlib-jdk8"))
    testCompile(project(":theta-solver"))
    testCompile(project(":theta-solver-z3"))
}
repositories {
    mavenCentral()
}