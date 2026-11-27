import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.google.services)
}

val versionProps = Properties().apply {
    val versionFile = rootProject.file("version.properties")
    check(versionFile.exists()) { "Arquivo version.properties ausente na raiz do projeto." }
    versionFile.reader(Charsets.UTF_8).use { load(it) }
}

fun envOrProp(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() }
        ?: (findProperty(prop) as String?)?.takeIf { it.isNotBlank() }

val releaseStorePath = envOrProp("KEYSTORE_FILE", "RELEASE_STORE_FILE")

android {
    namespace = "com.example.cartelinha"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.cartelinha"
        minSdk = 29
        targetSdk = 37
        versionCode = versionProps.getProperty("VERSION_CODE").toInt()
        versionName = versionProps.getProperty("VERSION_NAME") +
            (System.getenv("VERSION_NAME_SUFFIX") ?: "")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (releaseStorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = envOrProp("KEYSTORE_PASSWORD", "RELEASE_STORE_PASSWORD")
                keyAlias = envOrProp("KEY_ALIAS", "RELEASE_KEY_ALIAS")
                keyPassword = envOrProp("KEY_PASSWORD", "RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseStorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Não incluir kotlin.** nem kotlinx.**: o R8 pode tornar classes internas
            // do Kotlin inacessíveis e o Compose (fontes/sequências) quebra na abertura.
            optimization {
                enable = true
                packageScope = setOf("androidx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
