package com.vsp.core.data.remote.rtdb

/**
 * Per-vendor Firebase configuration, injected from the app module's BuildConfig (backed by
 * local.properties). No `google-services.json` is required: a dedicated [com.google.firebase.FirebaseApp]
 * is initialized from these values so each vendor build targets its own Realtime Database.
 *
 * When [databaseUrl] is blank the app runs in offline baseline mode (RTDB disabled, graceful).
 */
data class FirebaseConfig(
    val databaseUrl: String = "",
    val projectId: String = "",
    val applicationId: String = "",
    val apiKey: String = "",
    val vendorId: String = "default",
) {
    val isConfigured: Boolean
        get() = databaseUrl.isNotBlank() && applicationId.isNotBlank() && apiKey.isNotBlank()
}
