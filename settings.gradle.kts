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

rootProject.name = "VisionBoard"

include(":app")
include(":core:common")
include(":core:camera")
include(":core:vision")
include(":core:tracking")
include(":core:geometry")
include(":core:bluetooth")
include(":core:network")
include(":core:database")
include(":core:domain")
include(":core:repository")
include(":core:di")
include(":core:settings")
include(":core:utils")
include(":ui:splash")
include(":ui:onboarding")
include(":ui:calibration")
include(":ui:preview")
include(":ui:typing")
include(":ui:pairing")
include(":ui:settings")
include(":ui:developer")
include(":ui:theme")
include(":ui:components")
