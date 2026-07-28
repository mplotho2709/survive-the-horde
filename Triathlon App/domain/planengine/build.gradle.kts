plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":domain:zones"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
