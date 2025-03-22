import io.ktor.plugin.features.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin")
}

application.mainClass = "dev.bnorm.broadcast.MainKt"

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-sse")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-config-yaml")

    implementation("org.slf4j:slf4j-simple:2.0.16")

//    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktor_version")
//    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlin_version")
//    testImplementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
    jvmToolchain(17)
}

ktor {
    docker {
        jreVersion = JavaVersion.VERSION_21
        localImageName = "broadcast-server"

        externalRegistry = DockerImageRegistry.externalRegistry(
            username = providers.environmentVariable("GITHUB_USERNAME"),
            password = providers.environmentVariable("GITHUB_PASSWORD"),
            hostname = provider { "ghcr.io" },
            namespace = provider { "bnorm" },
            project = provider { "broadcast-server" },
        )
    }
}
