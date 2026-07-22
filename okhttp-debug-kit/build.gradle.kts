plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gzq.okhttpdebugkit"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
}

