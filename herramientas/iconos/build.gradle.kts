plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

/**
 * Herramienta de admin, no parte de la tienda.
 *
 * Existe solo para rasterizar los iconos de las apps del catálogo usando el propio
 * motor de Android, que es el mismo que los dibujará en el móvil. Nada de este módulo
 * entra en el APK de DracApps.
 */
android {
    namespace = "com.marcmayol.dracapps.iconos"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
