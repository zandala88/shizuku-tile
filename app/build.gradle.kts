import java.util.Properties

plugins { id("com.android.application") }

val auth = Properties().apply {
    rootProject.file("auth.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val signing = Properties().apply {
    rootProject.file("signing.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val initialToken = auth.getProperty("auth", "").replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "io.github.local.shizukutoggle"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.local.shizukutoggle"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"
        buildConfigField("String", "INITIAL_AUTH_TOKEN", "\"$initialToken\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        if (signing.isNotEmpty()) create("localRelease") {
            storeFile = file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (signing.isNotEmpty()) signingConfig = signingConfigs.getByName("localRelease")
        }
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
