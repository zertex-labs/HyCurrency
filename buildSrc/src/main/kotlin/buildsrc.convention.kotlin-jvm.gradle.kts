plugins {
    kotlin("jvm")
    `java-library`
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-receivers",
            "-Xjsr305=strict"
        )
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
