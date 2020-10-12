plugins {
    id("java-common")
    id("kotlin-common")
    id("cli-tool")
}

dependencies {
    implementation(project(":theta-cfa-analysis-chc"))
}

application {
    mainClassName = "hu.bme.mit.theta.cfa.chc.cli.CfaChcCliKt"
}