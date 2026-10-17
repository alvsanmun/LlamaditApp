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

rootProject.name = "LlamaditApp"
include(":app")
// WhatsApp falso para el emulador. Solo se incluye si se pide expresamente, para que
// `gradlew installDebug` no lo instale nunca en el móvil real:
//   gradlew -PwithWhatsAppStub :whatsapp-stub:installDebug
// (La carpeta solo existe en el PC de desarrollo, no en el repositorio.)
if (providers.gradleProperty("withWhatsAppStub").isPresent && file("whatsapp-stub").exists()) include(":whatsapp-stub")
