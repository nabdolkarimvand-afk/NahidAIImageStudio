plugins { id("com.android.application") }

android {
    namespace = "com.nahid.aiimagestudio"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.nahid.aiimagestudio"
        minSdk = 28
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.0"
    }
    packaging { jniLibs { useLegacyPackaging = true } }
    buildTypes { release { isMinifyEnabled = false } }
}
