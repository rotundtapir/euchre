// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

// Euchre's server binary: cardkit-server composed with EuchreDescriptor, and nothing else. Rooms,
// lobbies, seat hosting, reconnect, snapshots and anti-abuse are all generic and live in cardkit.
//
// The heap is deliberately small: this shares a 1 vCPU / 1 GB VPS with the 500 server, and the two
// are sized as a pair (see 500's docs/server-runbook.md). Ktor CIO plus a pure-Kotlin engine idles
// between human taps, so the constraint is memory, not CPU.
kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":net"))
    implementation(project(":engine"))
    implementation(project(":ai"))
    implementation(libs.cardkit.server)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.ktor.client.cio)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "io.github.rotundtapir.euchre.server.MainKt"
    applicationDefaultJvmArgs = listOf("-Xms32m", "-Xmx144m", "-XX:MaxMetaspaceSize=80m")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
