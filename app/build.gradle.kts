import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "protect.card_locker"
    compileSdk = 35

    defaultConfig {
        applicationId = "me.hackerchick.catima"
        minSdk = 21
        targetSdk = 35
        versionCode = 150
        versionName = "2.36.0"

        vectorDrawables.useSupportLibrary = true
        multiDexEnabled = true

        resourceConfigurations += listOf("ar", "be", "bg", "bn", "bn-rIN", "bs", "cs", "da", "de", "el-rGR", "en", "eo", "es", "es-rAR", "et", "fa", "fi", "fr", "gl", "he-rIL", "hi", "hr", "hu", "in-rID", "is", "it", "ja", "ko", "lt", "lv", "nb-rNO", "nl", "oc", "pl", "pt", "pt-rBR", "pt-rPT", "ro-rRO", "ru", "sk", "sl", "sr", "sv", "ta", "tr", "uk", "vi", "zh-rCN", "zh-rTW")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("boolean", "showDonate", "true")
        buildConfigField("boolean", "showRateOnGooglePlay", "false")
        buildConfigField("boolean", "useAcraCrashReporter", "true")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    // Two flavor dimensions: type (foss/gplay) and recording (standard/recording)
    flavorDimensions.addAll(listOf("type", "recording"))

    productFlavors {
        // Type dimension: foss vs gplay
        create("foss") {
            dimension = "type"
            isDefault = true
        }
        create("gplay") {
            dimension = "type"

            // Google doesn't allow donation links
            buildConfigField("boolean", "showDonate", "false")
            buildConfigField("boolean", "showRateOnGooglePlay", "true")

            // Google Play already sends crashes to the Google Play Console
            buildConfigField("boolean", "useAcraCrashReporter", "false")
        }

        // Recording dimension: standard (production-ready) vs recording (with FlowMonitor)
        create("standard") {
            dimension = "recording"
            isDefault = true
            // No special config - production-ready code without FlowMonitor
        }
        create("recording") {
            dimension = "recording"
            applicationIdSuffix = ".recording"
            versionNameSuffix = "-recording"

            // Build config flag to detect recording mode at runtime (if needed)
            buildConfigField("boolean", "FLOW_RECORDING_ENABLED", "true")
        }
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    compileOptions {
        encoding = "UTF-8"

        // Flag to enable support for the new language APIs
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("test") {
            resources.srcDirs("src/test/res")
        }

        // Recording flavor automatically uses src/recording/java
        // Gradle detects it based on flavor name
    }

    // Starting with Android Studio 3 Robolectric is unable to find resources.
    // The following allows it to find the resources.
    testOptions.unitTests.isIncludeAndroidResources = true
    tasks.withType<Test>().configureEach {
        testLogging {
            events("started", "passed", "skipped", "failed")
        }
    }

    lint {
        lintConfig = file("lint.xml")
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    // AndroidX
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-remoteviews:1.1.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("androidx.palette:palette:1.0.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation("com.google.android.material:material:1.12.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // Third-party
    implementation("com.journeyapps:zxing-android-embedded:4.3.0@aar")
    implementation("com.github.yalantis:ucrop:2.2.10")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.apache.commons:commons-csv:1.9.0")
    implementation("com.jaredrummler:colorpicker:1.1.0")
    implementation("net.lingala.zip4j:zip4j:2.11.5")

    // Crash reporting
    implementation("ch.acra:acra-mail:5.13.1")
    implementation("ch.acra:acra-dialog:5.13.1")

    // Testing
    val androidXTestVersion = "1.7.0"
    val junitVersion = "4.13.2"
    testImplementation("androidx.test:core:$androidXTestVersion")
    testImplementation("junit:junit:$junitVersion")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation ("org.mockito:mockito-core:5.18.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.0.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("com.google.truth:truth:1.1.3")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("app.cash.turbine:turbine:0.13.0")
    androidTestImplementation("androidx.test:core:$androidXTestVersion")
    androidTestImplementation("junit:junit:$junitVersion")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:$androidXTestVersion")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")


tasks.register("copyRawResFiles", Copy::class) {
    from(
        layout.projectDirectory.file("../CHANGELOG.md"),
        layout.projectDirectory.file("../PRIVACY.md")
    )
    into(layout.projectDirectory.dir("src/main/res/raw"))
    rename { it.lowercase() }
}.also {
    tasks.preBuild.dependsOn(it)
    tasks.getByName<Delete>("clean") {
        val filesNamesToDelete = listOf("CHANGELOG", "PRIVACY")
        filesNamesToDelete.forEach { fileName ->
            delete(layout.projectDirectory.file("src/main/res/raw/${fileName.lowercase()}.md"))
        }
    }
}}
