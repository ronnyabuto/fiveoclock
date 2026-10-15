import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fivepm.clock"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.fivepm.clock"
        minSdk = 33
        targetSdk = 36
        versionCode = 4
        versionName = "1.1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
    signingConfigs {
        create("release") {
            val path = providers.environmentVariable("FIVEPM_KEYSTORE").orNull
            if (path != null) {
                storeFile = file(path)
                storePassword = providers.environmentVariable("FIVEPM_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("FIVEPM_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("FIVEPM_KEY_PASSWORD").orNull
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        getByName("test") {
            java.srcDir("src/sharedTest/java")
            resources.srcDir("src/sharedTest/resources")
            resources.srcDir("src/main/assets")
        }
        getByName("androidTest") {
            java.srcDir("src/sharedTest/java")
            assets.srcDir("src/sharedTest/resources")
        }
    }
    lint {
        warningsAsErrors = true
        // The toolchain is deliberately pinned; version discovery is separate from code lint.
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
    }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

tasks.register("verifyReleaseSigning") {
    doLast {
        check(listOf("FIVEPM_KEYSTORE", "FIVEPM_STORE_PASSWORD", "FIVEPM_KEY_ALIAS", "FIVEPM_KEY_PASSWORD")
            .all { !providers.environmentVariable(it).orNull.isNullOrBlank() }) {
            "Set FIVEPM_KEYSTORE, FIVEPM_STORE_PASSWORD, FIVEPM_KEY_ALIAS and FIVEPM_KEY_PASSWORD."
        }
        check(android.signingConfigs.getByName("release").storeFile?.isFile == true) {
            "Set FIVEPM_KEYSTORE, FIVEPM_STORE_PASSWORD, FIVEPM_KEY_ALIAS and FIVEPM_KEY_PASSWORD."
        }
    }
}
val verifyWidgetData = tasks.register("verifyWidgetData") {
    val repository = rootProject.projectDir.parentFile
    val sources = listOf("src/domain/time.ts", "src/domain/selection.ts", "package-lock.json", "scripts/export-widget-data.ts")
        .map { repository.resolve(it) }
    val generated = listOf(file("src/main/assets/zones.json"), file("src/sharedTest/resources/parity-fixtures.json"))
    inputs.files(sources + generated)
    doLast {
        val hash = MessageDigest.getInstance("SHA-256")
        sources.forEach { hash.update(it.readBytes()) }
        val digest = hash.digest().joinToString("") { "%02x".format(it) }
        generated.forEach {
            check(it.isFile && Regex(""""sourceSha256": "([a-f0-9]{64})"""").find(it.readText())?.groupValues?.get(1) == digest) {
                "Widget data is missing or stale. Run npm run android:prepare from the repository root."
            }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(verifyWidgetData) }
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn("verifyReleaseSigning")
}
