plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
    alias(libs.plugins.shadow)
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":cache"))
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.bundles.grpcServer)
    implementation(libs.bundles.database)
    implementation(libs.bundles.logging)
    testImplementation(libs.bundles.testing)
    testImplementation(libs.postgresql) // For direct JDBC in tests
}

application {
    mainClass.set("hycurrency.service.MainKt")
}

tasks.shadowJar {
    archiveBaseName.set("hycurrency-service")
    archiveClassifier.set("all")
    mergeServiceFiles() // Required for gRPC service discovery
    manifest {
        attributes("Main-Class" to "hycurrency.service.MainKt")
    }
}
