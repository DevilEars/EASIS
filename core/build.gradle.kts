plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidLibrary {
        namespace = "app.quenya.core"
        compileSdk { version = release(libs.versions.compileSdk.get().toInt()) { minorApiLevel = 0 } }
        minSdk = libs.versions.minSdk.get().toInt()
    }
    jvm()                       // fast logic tests on any machine: ./gradlew :core:jvmTest
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies { implementation(libs.kotlinx.serialization.json) }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}
