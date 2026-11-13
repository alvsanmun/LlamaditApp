import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// App del cuidador: una ventana con la web de cuidador/ dentro (gestión a distancia). Así no
// hace falta publicar la web en ningún sitio: la app se conecta directamente al proyecto de
// Firebase de la familia, igual que la web.
android {
    namespace = "com.launcherfacil.cuidador"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.launcherfacil.cuidador"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"
    }

    // Misma clave que el launcher (keystore.properties, fuera de git).
    val keystoreFile = rootProject.file("keystore.properties")
    val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) } }
    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // La web se copia de cuidador/ al compilar (un solo código para la web y la app).
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/web"))
}

val copyCaregiverWeb = tasks.register<Sync>("copyCaregiverWeb") {
    from(rootProject.file("cuidador")) {
        // El service worker no hace falta dentro de la app; los .bat/.ps1 son de la web local.
        exclude("sw.js", "*.bat", "*.ps1", "LEEME.txt")
    }
    into(layout.buildDirectory.dir("generated/web/cuidador"))
}
tasks.named("preBuild") { dependsOn(copyCaregiverWeb) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.webkit)
}
