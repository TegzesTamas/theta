plugins{
	kotlin("jvm")
}

dependencies {
    val testImplementation: Configuration by configurations

    testImplementation(Deps.mockitoKotlin)
}
