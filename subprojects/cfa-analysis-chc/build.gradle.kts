plugins {
    id("java-common")
    kotlin("jvm")
}

dependencies {
    compile(project(":theta-cfa"))
    compile(project(":theta-common"))
    compile(project(":theta-core"))
    implementation(kotlin("stdlib-jdk8"))
}
repositories {
    mavenCentral()
}