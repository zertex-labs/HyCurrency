plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    api(project(":common"))
    api(libs.bundles.kotlinxEcosystem)
    api(libs.lettuce)
    testImplementation(libs.bundles.testing)
}
