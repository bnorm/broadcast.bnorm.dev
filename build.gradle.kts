import io.ktor.plugin.features.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin")
}

application.mainClass = "dev.bnorm.broadcast.MainKt"

dependencies {
    val ktor_version = "3.1.1"
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")
    implementation("io.ktor:ktor-server-sse:$ktor_version")
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-server-config-yaml:$ktor_version")

//    implementation("ch.qos.logback:logback-classic:$logback_version")
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
