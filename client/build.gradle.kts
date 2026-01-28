plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.shadow)
}

dependencies {
    api(project(":common"))
    api(libs.kotlinxCoroutines)
    api(libs.bundles.grpcClient)
    testImplementation(libs.bundles.testing)
}

tasks.shadowJar {
    archiveBaseName.set("hycurrency-client")
    archiveClassifier.set("all")
    mergeServiceFiles()  // Required for gRPC to find service providers
    minimize()
}