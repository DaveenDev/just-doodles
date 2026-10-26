import java.util.Properties

plugins {
    id("com.android.application")
}

// The upload key for the Play Store. signing/ holds the key and its password: back it up, never share it.
// Without it (another machine), release builds fall back to the debug key so they can still be sideloaded.
val keystoreProps = rootProject.file("signing/keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

android {
    namespace = "com.daveendev.justdoodles"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.daveendev.justdoodles"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"
    }

    signingConfigs {
        if (keystoreProps != null) {
            create("upload") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    // direct: the APK handed out by hand, with the donate page.
    // play: the Google Play build, without it (Play doesn't allow links to outside payment pages).
    flavorDimensions += "store"
    productFlavors {
        create("direct") { dimension = "store" }
        create("play") { dimension = "store" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// The one library, and only in the Play build: Google Play Billing, to sell PLUS. Play only accepts
// version 8 or newer. The plain artifact, not billing-ktx, which would pull in coroutines.
// It talks to the Play Store app, not the internet; src/play/AndroidManifest.xml strips the
// INTERNET permission its logging libraries ask for. The direct build has no dependencies at all.
dependencies {
    "playImplementation"("com.android.billingclient:billing:9.1.0")
}
