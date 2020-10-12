plugins {
    id("java-common")
	id("kotlin-common")
}

dependencies {
    implementation(project(":theta-cfa"))
    implementation(project(":theta-solver"))
    testImplementation(project(":theta-solver-z3"))
}
