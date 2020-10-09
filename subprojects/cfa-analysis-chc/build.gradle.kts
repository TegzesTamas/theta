plugins {
    id("java-common")
	id("kotlin-common")
    id("cli-tool")
}

dependencies {
    implementation(project(":theta-cfa"))
    implementation(project(":theta-solver"))
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":theta-solver-z3"))
}
repositories {
    mavenCentral()
}
application {
    mainClassName = "hu.bme.mit.theta.cfa.analysis.chc.MainKt"
}