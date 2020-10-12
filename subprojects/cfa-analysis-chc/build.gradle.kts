plugins {
    id("java-common")
	id("kotlin-common")
}

dependencies {
    implementation(project(":theta-cfa"))
    implementation(project(":theta-solver"))
    implementation(kotlin("stdlib-jdk8"))
    testImplementation(project(":theta-solver-z3"))
}
