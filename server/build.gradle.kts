plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":common"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.contentnegotiation)
    implementation(libs.ktor.server.statuspages)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.serialization.json)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.datetime)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.logback)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.testhost)
    testImplementation(libs.ktor.client.contentnegotiation)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
}

application {
    mainClass.set("com.example.todo.server.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
    // This machine's Docker Engine has a min API version of 1.40, but docker-java
    // (used by Testcontainers) defaults to v1.32 and gets a 400. Pin the client's
    // API version via the system property docker-java reads.
    systemProperty("api.version", "1.44")
}
