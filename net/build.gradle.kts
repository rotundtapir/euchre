// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

// Euchre's half of the online wire protocol: its lobby config, its create-lobby request, and the
// registration that teaches cardkit-net to carry EuchreAction/EuchrePlayerView. Everything
// game-independent — the envelope, the lobby messages, the enums, the WebSocket client — comes from
// cardkit-net.
//
// Pure Kotlin Multiplatform (jvm + wasmJs) so the Android app, the browser build and the server all
// speak the same types. FOSS-only (Ktor is Apache-2.0), which is what keeps the F-Droid build graph
// clean once :shared consumes it.
kotlin {
    jvmToolchain(21)
    jvm()
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":engine"))
            api(libs.cardkit.net)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit.jupiter)
            implementation(libs.kotlinx.coroutines.test)
            runtimeOnly(libs.junit.platform.launcher)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Almost all declarations; the golden tests are what actually matter here. Same bound as 500's :net.
kover {
    reports {
        verify {
            rule {
                minBound(70)
            }
        }
    }
}
