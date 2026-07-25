plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Sin plugin de Android y sin dependencias de androidx: el dominio no puede tocar el
// framework aunque alguien lo intente. Es la garantía por construcción de la regla
// del plan, en vez de un acuerdo que se va olvidando.
dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnit()
    testLogging { events("passed", "failed", "skipped") }
}
