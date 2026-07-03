pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "VehicleInspection"

// Application
include(":app")

// Core layer modules
include(":core:model")
include(":core:common")
include(":core:domain")
include(":core:data")
include(":core:datastore")
include(":core:ui")
include(":core:testing")

// NOTE: Feature presentation code (auth, dashboard, start, identify, olddocs, capture,
// imagedetail, review, verification, report) lives inside :app under
// com.vsp.inspection.feature.* packages, and camera/AI/VIN/sync/report logic lives in
// :core:data. This intentionally avoids separate :feature:* / :core:camera Gradle modules to
// keep the build reliable while preserving Clean-Architecture layering (presentation → domain
// ← data via Hilt). See specs/001-vehicle-inspection-app/tasks.md (Implementation status).
