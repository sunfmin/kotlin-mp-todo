import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

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
                // API is on 8080; serve the web app on 8081 to avoid a clash.
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).copy(
                    port = 8081,
                    open = false,
                )
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
