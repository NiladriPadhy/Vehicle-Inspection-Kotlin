package com.vsp.core.data.portability

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Directory ↔ zip helpers used by export/import. */
object Zipper {

    fun zipDir(sourceDir: File, outFile: File) {
        ZipOutputStream(outFile.outputStream().buffered()).use { zos ->
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = sourceDir.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                zos.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    /** Extracts a zip into [targetDir], guarding against path-traversal (zip-slip). */
    fun unzip(zipFile: File, targetDir: File) {
        val canonicalTarget = targetDir.canonicalFile
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (!outFile.canonicalFile.path.startsWith(canonicalTarget.path)) {
                    throw SecurityException("Illegal zip entry path: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
