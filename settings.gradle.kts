pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "dream_player_android"

include(":app")

// core: JNI contract + the Rust .so, domain models, repositories, background playback.
include(":core:ffi")
include(":core:model")
include(":core:data")
include(":core:playback")

// helper libraries without UI.
include(":lib:common")
include(":lib:covers")

// shared interface building blocks (theme, unified list, sheet, player bar...).
include(":ui:kit")

// tabs / screens.
include(":feature:playlists")
include(":feature:queue")
include(":feature:song")
include(":feature:editor")
include(":feature:meta")
include(":feature:exchange")
include(":feature:settings")
