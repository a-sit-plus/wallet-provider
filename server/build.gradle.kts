plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    kotlin("plugin.serialization") version "1.9.0"
    application
}

group = "at.asitplus.walletprovider"
val artifactVersion: String by extra
version = artifactVersion

application {
    mainClass.set("at.asitplus.walletprovider.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(libs.warden.supreme)
    implementation(libs.vck)
    implementation(libs.vck.openid)
    implementation(libs.signum)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.html)
    implementation(libs.ktor.yaml)
    implementation(libs.kotlin.html)
    implementation(libs.kotlin.json)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.h2)
    implementation(libs.napier)

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.test)
}