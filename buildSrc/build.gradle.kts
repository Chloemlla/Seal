plugins { `kotlin-dsl` }

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
}

kotlin { jvmToolchain(21) }

// buildSrc only ships shared Kotlin (Version.kt), not Gradle plugins.
// Avoid noisy "No valid plugin descriptors were found in META-INF/gradle-plugins".
tasks.matching { it.name == "validatePlugins" }.configureEach {
    enabled = false
}
