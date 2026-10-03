plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidLibrary {
        namespace = "app.quenya.ui"
        compileSdk { version = release(libs.versions.compileSdk.get().toInt()) { minorApiLevel = 0 } }
        minSdk = libs.versions.minSdk.get().toInt()
        androidResources { enable = true }       // needed for Compose Multiplatform resources
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sqldelight.runtime)
        }
        androidMain.dependencies { implementation(libs.sqldelight.android.driver) }
        iosMain.dependencies { implementation(libs.sqldelight.native.driver) }
    }
}

compose.resources {
    packageOfResClass = "app.quenya.ui.resources"
    generateResClass = always
}

sqldelight {
    databases { create("QuenyaDb") { packageName.set("app.quenya.db") } }
}
