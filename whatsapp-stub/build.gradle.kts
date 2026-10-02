// Falso WhatsApp SOLO para probar en el emulador: usa el paquete com.whatsapp y
// muestra el intent que recibe. No instalar nunca en un móvil con WhatsApp real.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.launcherfacil.whatsappstub"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.whatsapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "stub"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
