plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    implementation(project(":common"))
    implementation(project(":cache"))
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.bundles.grpcServer)
    implementation(libs.bundles.database)
    implementation(libs.bundles.logging)
    testImplementation(libs.bundles.testing)
}

application {
    mainClass.set("hyprofile.service.MainKt")
}
