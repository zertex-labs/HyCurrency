plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(project(":common"))
    api(libs.kotlinxCoroutines)
    api(libs.bundles.grpcClient)
    testImplementation(libs.bundles.testing)
}
