plugins {
    id("buildsrc.convention.grpc-kotlin")
}

dependencies {
    api(libs.bundles.grpcRuntime)
    api(libs.kotlinxCoroutines)
}
