plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    js(IR) {
        moduleName = "web"
        browser {
            commonWebpackConfig {
                outputFileName = "web.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(project(":client-core"))
                implementation(compose.runtime)
                implementation(compose.html.core)
                implementation(libs.ktor.client.js)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
