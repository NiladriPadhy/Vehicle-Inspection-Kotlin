import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// PDF export tuning, overridable via local.properties or environment variables.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun tunable(key: String, default: Int): Int =
    (localProps.getProperty(key) ?: System.getenv(key))?.toIntOrNull() ?: default

// JPEG quality (1-100) used when embedding photos into the PDF; lower = smaller file.
val pdfImageQuality = tunable("PDF_IMAGE_QUALITY", 70).coerceIn(1, 100)
// Max embedded pixel width for gallery thumbnails and full damage-evidence photos.
val pdfGalleryImageWidth = tunable("PDF_GALLERY_IMAGE_WIDTH", 640)
val pdfDamageImageWidth = tunable("PDF_DAMAGE_IMAGE_WIDTH", 1280)
// Hard cap on how many photos are embedded in a single report (0 = unlimited).
val pdfMaxImages = tunable("PDF_MAX_IMAGES", 120)

android {
    namespace = "com.vsp.core.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("int", "PDF_IMAGE_QUALITY", "$pdfImageQuality")
        buildConfigField("int", "PDF_GALLERY_IMAGE_WIDTH", "$pdfGalleryImageWidth")
        buildConfigField("int", "PDF_DAMAGE_IMAGE_WIDTH", "$pdfDamageImageWidth")
        buildConfigField("int", "PDF_MAX_IMAGES", "$pdfMaxImages")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.mlkit.text.recognition)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.android)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)

    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.room.testing)
}
