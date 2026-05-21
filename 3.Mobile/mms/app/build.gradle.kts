import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.mkac.meikomms"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mkac.meikomms"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        val dateFormat = SimpleDateFormat("dd - MM - yyyy", Locale.getDefault())
        val buildTime = dateFormat.format(Date())
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }


    applicationVariants.all {
        val variant = this
        variant.outputs.map { it as BaseVariantOutputImpl }
            .forEach { output ->
                val date = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(Date())
                val outputFileName =  "MES_MMS_${date}.apk"
                    println("OutputFileName: $outputFileName")
                    output.outputFileName = outputFileName
                }
    }


}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation ("org.jetbrains.kotlin:kotlin-script-runtime:1.8.0");
    implementation("com.google.android.flexbox:flexbox:3.0.0");
    implementation("com.squareup.okhttp3:okhttp:4.12.0");
    implementation("com.squareup.picasso:picasso:2.71828");
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("com.burhanrashid52:photoeditor:3.0.2");
    implementation("com.google.mlkit:barcode-scanning:17.0.0")
    // CameraX dependencies
    implementation("androidx.camera:camera-core:1.0.2");
    implementation("androidx.camera:camera-camera2:1.0.2");
    implementation("androidx.camera:camera-lifecycle:1.0.2");
    implementation("androidx.camera:camera-view:1.0.0-alpha26");
    implementation("androidx.camera:camera-extensions:1.0.0-alpha26");
    implementation ("io.socket:socket.io-client:2.1.0");
    implementation ("androidx.room:room-runtime:2.6.1");
    annotationProcessor ("androidx.room:room-compiler:2.6.1");
    implementation ("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.0.7")
    implementation ("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation ("com.google.zxing:core:3.4.1")
    implementation("com.github.bumptech.glide:glide:4.15.1")
    annotationProcessor("com.github.bumptech.glide:compiler:4.15.1")
    implementation ("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0-alpha01")
    implementation("com.mikepenz:materialdrawer:9.0.1")
    implementation ("androidx.drawerlayout:drawerlayout:1.1.1")
    implementation ("com.google.android.material:material:1.4.0")
    implementation("me.relex:circleindicator:2.1.6")
    implementation ("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.github.evrencoskun:TableView:v0.8.9.4")
    implementation ("androidx.appcompat:appcompat:1.6.1")
    implementation ("androidx.core:core:1.12.0")


}