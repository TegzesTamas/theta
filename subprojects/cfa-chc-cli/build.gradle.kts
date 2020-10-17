plugins {
    id("java-common")
    id("kotlin-common")
    id("cli-tool")
}

dependencies {
    implementation(project(":theta-cfa"))
    implementation(project(":theta-solver-z3"))
    implementation(project(":theta-cfa-analysis-chc"))
    implementation("com.amihaiemil.web:eo-yaml:5.1.6")
}

application {
    mainClassName = "hu.bme.mit.theta.cfa.chc.cli.CfaChcCli"
}