package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection

/**
 * Runtime manager for downloadable language packs.
 * Manages loading, downloading, caching, atomic file writes, and string resolution.
 * Bundled languages (English, Hindi, Urdu, Tamil) are protected from deletion.
 */
object LanguagePackManager {
    private const val TAG = "LanguagePackManager"
    private const val PACKS_DIR = "language_packs"
    private const val BUFFER_SIZE = 8192

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val installedPacks = ConcurrentHashMap<String, LanguagePack>()
    private val remotePackList = ConcurrentHashMap<String, RemoteLanguagePackInfo>()

    private val _languagePacksFlow = MutableStateFlow<List<LanguagePackMetadata>>(emptyList())
    val languagePacksFlow: StateFlow<List<LanguagePackMetadata>> = _languagePacksFlow.asStateFlow()

    private var isInitialized = false

    /**
     * Initializes the LanguagePackManager.
     * Loads previously installed packs from app-private storage.
     */
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        loadInstalledPacks(context.applicationContext)
        refreshMetadataList()
    }

    private fun getStorageDir(context: Context): File {
        val dir = File(context.filesDir, PACKS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getPackFile(context: Context, languageCode: String): File {
        return File(getStorageDir(context), "pack_${languageCode.lowercase()}.json")
    }

    /**
     * Loads installed language packs from internal app storage.
     */
    fun loadInstalledPacks(context: Context) {
        try {
            val dir = getStorageDir(context)
            val files = dir.listFiles { _, name -> name.startsWith("pack_") && name.endsWith(".json") }
            if (files != null) {
                for (file in files) {
                    try {
                        val content = file.readText(Charsets.UTF_8)
                        val result = LanguagePackValidator.validateAndParsePack(content)
                        result.onSuccess { pack ->
                            installedPacks[pack.languageCode.lowercase()] = pack
                            Log.d(TAG, "Loaded language pack: ${pack.languageCode} (${pack.displayName})")
                        }.onFailure { e ->
                            Log.w(TAG, "Failed to parse stored pack ${file.name}: ${e.message}")
                            // Clean up corrupted file
                            file.delete()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading language pack file ${file.name}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing language packs directory", e)
        }
        refreshMetadataList()
    }

    /**
     * Rebuilds the authoritative list of language metadata for UI consumption.
     */
    fun refreshMetadataList() {
        val list = mutableListOf<LanguagePackMetadata>()

        // 1. Add all 4 core bundled languages (always installed & non-removable)
        for (lang in AppLanguage.ALL) {
            list.add(lang.toMetadata())
        }

        // 2. Add installed downloaded packs
        for ((_, pack) in installedPacks) {
            if (AppLanguage.ALL.none { it.code.equals(pack.languageCode, ignoreCase = true) }) {
                list.add(pack.toMetadata(LanguagePackStatus.INSTALLED))
            }
        }

        // 3. Add available remote packs that are not yet installed
        for ((code, remoteInfo) in remotePackList) {
            if (list.none { it.code.equals(code, ignoreCase = true) }) {
                list.add(remoteInfo.toMetadata(LanguagePackStatus.AVAILABLE))
            }
        }

        _languagePacksFlow.value = list
    }

    /**
     * Installs a language pack locally from a JSON string.
     * Uses atomic file writing (.tmp -> rename).
     */
    fun installPackDirectly(
        context: Context,
        jsonString: String,
        expectedChecksum: String? = null
    ): Result<LanguagePack> {
        val validationResult = LanguagePackValidator.validateAndParsePack(jsonString, expectedChecksum)
        if (validationResult.isFailure) {
            return validationResult
        }

        val pack = validationResult.getOrThrow()
        val languageCode = pack.languageCode.lowercase()

        // Reject overwriting bundled languages
        if (AppLanguage.ALL.any { it.code.equals(languageCode, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Cannot overwrite bundled core language: $languageCode"))
        }

        try {
            val dir = getStorageDir(context)
            val tempFile = File(dir, "pack_${languageCode}.json.tmp")
            val targetFile = getPackFile(context, languageCode)

            tempFile.writeText(jsonString, Charsets.UTF_8)

            // Atomic rename
            if (!tempFile.renameTo(targetFile)) {
                // Fallback copy if rename fails on some file systems
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            installedPacks[languageCode] = pack
            refreshMetadataList()
            Log.d(TAG, "Successfully installed language pack: $languageCode")
            return Result.success(pack)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save language pack $languageCode", e)
            return Result.failure(e)
        }
    }

    /**
     * Downloads a language pack from an HTTPS URL.
     * Validates HTTPS, file size, SHA-256 checksum, schema, and installs atomically.
     */
    fun downloadPack(
        context: Context,
        packMetadata: LanguagePackMetadata,
        onProgress: (Float) -> Unit = {},
        onResult: (Boolean, String?) -> Unit
    ) {
        val downloadUrl = packMetadata.downloadUrl
        if (downloadUrl.isNullOrBlank()) {
            if (LanguagePackConfig.REMOTE_MANIFEST_URL == null) {
                onResult(false, "Remote language pack server is not configured.")
            } else {
                onResult(false, "Download URL is missing for ${packMetadata.displayName}.")
            }
            return
        }

        // Security: Enforce HTTPS
        if (!downloadUrl.startsWith("https://", ignoreCase = true)) {
            onResult(false, "Insecure HTTP downloads are strictly rejected. HTTPS required.")
            return
        }

        updatePackStatus(packMetadata.code, LanguagePackStatus.DOWNLOADING, progress = 0f)

        scope.launch {
            var tempFile: File? = null
            var connection: HttpsURLConnection? = null
            try {
                val url = URL(downloadUrl)
                connection = withContext(Dispatchers.IO) {
                    url.openConnection() as HttpsURLConnection
                }
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.instanceFollowRedirects = true
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("Server returned HTTP $responseCode")
                }

                val totalLength = connection.contentLengthLong
                if (totalLength > LanguagePackConfig.MAX_PACK_FILE_SIZE_BYTES) {
                    throw IllegalStateException("File size exceeds maximum allowable limit (2MB).")
                }

                val dir = getStorageDir(context)
                tempFile = File(dir, "download_${packMetadata.code.lowercase()}_${System.currentTimeMillis()}.tmp")

                var bytesDownloaded = 0L
                val buffer = ByteArray(BUFFER_SIZE)

                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead

                            if (bytesDownloaded > LanguagePackConfig.MAX_PACK_FILE_SIZE_BYTES) {
                                throw IllegalStateException("Download exceeded size limit.")
                            }

                            if (totalLength > 0) {
                                val progress = (bytesDownloaded.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
                                withContext(Dispatchers.Main) {
                                    updatePackStatus(packMetadata.code, LanguagePackStatus.DOWNLOADING, progress = progress)
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }

                val jsonContent = tempFile.readText(Charsets.UTF_8)
                val validationResult = LanguagePackValidator.validateAndParsePack(
                    jsonString = jsonContent,
                    expectedChecksum = packMetadata.sha256Checksum
                )

                if (validationResult.isFailure) {
                    throw validationResult.exceptionOrNull() ?: Exception("Validation failed.")
                }

                val pack = validationResult.getOrThrow()
                val targetFile = getPackFile(context, pack.languageCode)

                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                installedPacks[pack.languageCode.lowercase()] = pack
                withContext(Dispatchers.Main) {
                    refreshMetadataList()
                    onResult(true, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for ${packMetadata.code}", e)
                tempFile?.delete()
                withContext(Dispatchers.Main) {
                    updatePackStatus(packMetadata.code, LanguagePackStatus.FAILED, error = e.localizedMessage)
                    onResult(false, e.localizedMessage ?: "Download failed.")
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    /**
     * Removes an installed downloaded language pack.
     * Bundled languages (English, Hindi, Urdu, Tamil) are NEVER removable.
     * If the language being removed is currently active, it is safely switched to English first.
     */
    fun removePack(context: Context, languageCode: String): Boolean {
        val code = languageCode.lowercase()

        // 1. Protection: Bundled languages are never removable
        if (AppLanguage.ALL.any { it.code.equals(code, ignoreCase = true) }) {
            Log.w(TAG, "Attempted to remove bundled language: $code. Action rejected.")
            return false
        }

        // 2. If active, switch to English first and persist
        val activeLanguage = AppLanguageManager.getLanguage(context)
        val activeCode = AppLanguageManager.getLanguageCode(context)
        if (activeCode.equals(code, ignoreCase = true) || activeLanguage.code.equals(code, ignoreCase = true)) {
            Log.i(TAG, "Active language $code is being removed. Safely falling back to English.")
            AppLanguageManager.setLanguage(context, AppLanguage.ENGLISH)
        }

        // 3. Delete pack file from storage
        val packFile = getPackFile(context, code)
        if (packFile.exists()) {
            packFile.delete()
        }

        // 4. Remove from in-memory cache
        installedPacks.remove(code)

        // 5. Update UI state
        refreshMetadataList()
        Log.i(TAG, "Successfully removed language pack: $code")
        return true
    }

    /**
     * Retrieves a localized string from an installed downloaded language pack.
     */
    fun getString(languageCode: String, key: String): String? {
        return installedPacks[languageCode.lowercase()]?.strings?.get(key)
    }

    /**
     * Checks if a language pack is installed.
     */
    fun isPackInstalled(languageCode: String): Boolean {
        if (AppLanguage.ALL.any { it.code.equals(languageCode, ignoreCase = true) }) {
            return true
        }
        return installedPacks.containsKey(languageCode.lowercase())
    }

    /**
     * Retrieves metadata for a specific language code.
     */
    fun getMetadata(languageCode: String): LanguagePackMetadata? {
        val bundled = AppLanguage.ALL.firstOrNull { it.code.equals(languageCode, ignoreCase = true) }
        if (bundled != null) return bundled.toMetadata()

        val installed = installedPacks[languageCode.lowercase()]
        if (installed != null) return installed.toMetadata(LanguagePackStatus.INSTALLED)

        return remotePackList[languageCode.lowercase()]?.toMetadata(LanguagePackStatus.AVAILABLE)
    }

    private fun updatePackStatus(
        code: String,
        status: LanguagePackStatus,
        progress: Float = 0f,
        error: String? = null
    ) {
        val currentList = _languagePacksFlow.value
        _languagePacksFlow.value = currentList.map { item ->
            if (item.code.equals(code, ignoreCase = true)) {
                item.copy(
                    status = status,
                    downloadProgress = progress,
                    errorMessage = error
                )
            } else {
                item
            }
        }
    }
}
