package com.vsp.core.data.io

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first local storage for inspection artifacts.
 *
 * Images are written to app-private internal storage (already OS-sandboxed) so they remain
 * directly readable by the image loader for previews. Sensitive report JSON is additionally
 * encrypted at rest via Jetpack Security [EncryptedFile]. See plan.md "Security".
 */
@Singleton
class FileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private fun inspectionDir(inspectionId: String): File =
        File(context.filesDir, "inspections/$inspectionId").apply { mkdirs() }

    fun imageFile(inspectionId: String, imageId: String): File =
        File(inspectionDir(inspectionId), "images/$imageId.jpg").apply { parentFile?.mkdirs() }

    fun saveImage(inspectionId: String, imageId: String, bytes: ByteArray): String {
        val file = imageFile(inspectionId, imageId)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    fun copyImageFrom(inspectionId: String, imageId: String, source: File): String {
        val file = imageFile(inspectionId, imageId)
        source.copyTo(file, overwrite = true)
        return file.absolutePath
    }

    fun delete(path: String): Boolean = runCatching { File(path).delete() }.getOrDefault(false)

    /** Removes all on-disk artifacts (images, encrypted report) for an inspection. */
    fun deleteInspection(inspectionId: String): Boolean =
        runCatching { File(context.filesDir, "inspections/$inspectionId").deleteRecursively() }
            .getOrDefault(false)

    /** Encrypts and stores report JSON at rest; returns the file path. */
    fun writeEncryptedReport(inspectionId: String, json: String): String {
        val target = File(inspectionDir(inspectionId), "report.json.enc")
        if (target.exists()) target.delete()
        val encrypted = EncryptedFile.Builder(
            context,
            target,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
        encrypted.openFileOutput().use { it.write(json.toByteArray()) }
        return target.absolutePath
    }

    fun readEncryptedReport(path: String): String {
        val file = File(path)
        val encrypted = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
        return encrypted.openFileInput().use { it.readBytes().decodeToString() }
    }
}
