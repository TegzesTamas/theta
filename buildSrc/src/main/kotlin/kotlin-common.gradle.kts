plugins{
	kotlin("jvm")
}

dependencies {
    val testImplementation: Configuration by configurations
    val implementation: Configuration by configurations

    implementation(kotlin("stdlib-jdk8"))
    testImplementation(Deps.mockitoKotlin)
}
