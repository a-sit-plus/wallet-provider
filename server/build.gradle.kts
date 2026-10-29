import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSpring)
    alias(libs.plugins.serialization)
    alias(libs.plugins.springBoot)
}

group = "at.asitplus.walletprovider"
val artifactVersion: String by extra
version = artifactVersion

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Gradle platforms instead of the dependency-management plugin: they never downgrade versions that VC-K or
    // Warden Supreme need (e.g. kotlinx-serialization, coroutines).
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(platform(libs.spring.cloud.dependencies))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation(libs.spring.cloud.starter.config)
    implementation(libs.webjars.locator)
    implementation(libs.webjars.bootstrap)
    implementation(libs.kotlin.reflect)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    implementation(libs.warden.supreme)
    implementation(libs.warden.config.spring)
    implementation(libs.vck)
    implementation(libs.vck.openid)
    implementation(libs.signum)
    implementation(libs.kotlin.json)
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly(libs.h2)
    runtimeOnly(libs.postgresql)
    implementation(libs.napier)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(libs.kotlin.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
