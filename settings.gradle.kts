pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LauncherFacil"
include(":app")
// WhatsApp falso para el emulador. Solo se incluye si se pide expresamente, para que
// `gradlew installDebug` no lo instale nunca en el móvil real:
//   gradlew -PwithWhatsAppStub :whatsapp-stub:installDebug
if (providers.gradleProperty("withWhatsAppStub").isPresent) include(":whatsapp-stub")
