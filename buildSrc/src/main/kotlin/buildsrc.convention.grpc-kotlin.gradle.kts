import com.google.protobuf.gradle.id

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("com.google.protobuf")
}

object GrpcVersions {
    const val PROTOBUF = "4.29.3"
    const val GRPC = "1.78.0"
    const val GRPC_KOTLIN = "1.5.0"
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${GrpcVersions.PROTOBUF}"
    }

    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${GrpcVersions.GRPC}"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:${GrpcVersions.GRPC_KOTLIN}:jdk8@jar"
        }
    }

    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
                id("grpckt")
            }
            task.builtins {
                id("kotlin")
            }
        }
    }
}
