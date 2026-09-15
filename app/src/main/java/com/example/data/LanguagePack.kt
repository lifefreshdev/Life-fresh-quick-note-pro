package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Configuration for downloadable language packs.
 * Remote downloading is disabled by default until an approved HTTPS endpoint is configured.
 */
object LanguagePackConfig {
    const val SCHEMA_VERSION = 1
    const val MANIFEST_VERSION = 1
    const val MAX_PACK_FILE_SIZE_BYTES = 2 * 1024 * 1024L // 2 MB maximum size

    /**
     * The HTTPS URL for the remote language manifest.
     * Isolated behind this configuration property. Left null until an approved server is deployed.
     */
    val REMOTE_MANIFEST_URL: String? = null
}

/**
 * In-memory representation of a validated, downloaded language pack.
 * Strictly DATA ONLY — contains no executable code or scripts.
 */
data class LanguagePack(
    val schemaVersion: Int,
    val languageCode: String,
    val displayName: String,
    val nativeName: String,
    val localeTag: String,
    val isRtl: Boolean,
    val packVersion: Int,
    val strings: Map<String, String>
) {
    fun toMetadata(status: LanguagePackStatus = LanguagePackStatus.INSTALLED): LanguagePackMetadata {
        return LanguagePackMetadata(
            code = languageCode,
            displayName = displayName,
            nativeName = nativeName,
            localeTag = localeTag,
            isRtl = isRtl,
            isBundled = false,
            packVersion = packVersion,
            status = status
        )
    }
}

/**
 * Remote manifest item describing an available downloadable language pack.
 */
data class RemoteLanguagePackInfo(
    val languageCode: String,
    val displayName: String,
    val nativeName: String,
    val localeTag: String,
    val isRtl: Boolean,
    val packVersion: Int,
    val downloadUrl: String,
    val sha256Checksum: String? = null,
    val minAppVersion: Int = 1
) {
    fun toMetadata(status: LanguagePackStatus = LanguagePackStatus.AVAILABLE): LanguagePackMetadata {
        return LanguagePackMetadata(
            code = languageCode,
            displayName = displayName,
            nativeName = nativeName,
            localeTag = localeTag,
            isRtl = isRtl,
            isBundled = false,
            packVersion = packVersion,
            status = status,
            downloadUrl = downloadUrl,
            sha256Checksum = sha256Checksum
        )
    }
}

/**
 * Remote manifest structure.
 */
data class LanguageManifest(
    val manifestVersion: Int,
    val packs: List<RemoteLanguagePackInfo>
)

/**
 * Security and validation engine for language packs.
 */
object LanguagePackValidator {

    /**
     * Validates and parses a language pack JSON string.
     * Enforces schemaVersion == 1, HTTPS-only rules, required metadata, non-empty translation strings,
     * and optional SHA-256 integrity verification.
     */
    fun validateAndParsePack(
        jsonString: String,
        expectedChecksum: String? = null
    ): Result<LanguagePack> {
        return runCatching {
            if (jsonString.isBlank()) {
                throw IllegalArgumentException("Language pack content cannot be empty.")
            }

            if (jsonString.toByteArray(Charsets.UTF_8).size > LanguagePackConfig.MAX_PACK_FILE_SIZE_BYTES) {
                throw IllegalArgumentException("Language pack exceeds maximum allowable file size.")
            }

            // SHA-256 Checksum verification
            if (!expectedChecksum.isNullOrBlank()) {
                val calculatedHash = calculateSha256(jsonString)
                if (!calculatedHash.equals(expectedChecksum.trim(), ignoreCase = true)) {
                    throw SecurityException("SHA-256 checksum mismatch. Expected: $expectedChecksum, Actual: $calculatedHash")
                }
            }

            val root = JSONObject(jsonString)

            val schemaVersion = root.optInt("schemaVersion", -1)
            if (schemaVersion != LanguagePackConfig.SCHEMA_VERSION) {
                throw IllegalArgumentException("Unsupported schemaVersion: $schemaVersion. Expected: ${LanguagePackConfig.SCHEMA_VERSION}")
            }

            val languageCode = root.optString("languageCode", "").trim()
            if (languageCode.isEmpty() || !languageCode.matches(Regex("^[a-zA-Z0-9_-]{2,15}$"))) {
                throw IllegalArgumentException("Invalid or missing languageCode: $languageCode")
            }

            val displayName = root.optString("displayName", "").trim()
            if (displayName.isEmpty()) {
                throw IllegalArgumentException("Missing required metadata: displayName")
            }

            val nativeName = root.optString("nativeName", "").trim()
            if (nativeName.isEmpty()) {
                throw IllegalArgumentException("Missing required metadata: nativeName")
            }

            val localeTag = root.optString("localeTag", "").trim()
            if (localeTag.isEmpty()) {
                throw IllegalArgumentException("Missing required metadata: localeTag")
            }

            val isRtl = root.optBoolean("isRtl", false)
            val packVersion = root.optInt("packVersion", 1)

            val stringsObj = root.optJSONObject("strings")
                ?: throw IllegalArgumentException("Missing 'strings' translation dictionary object.")

            val stringsMap = mutableMapOf<String, String>()
            val keys = stringsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = stringsObj.optString(key, null)
                if (value != null) {
                    stringsMap[key] = value
                }
            }

            if (stringsMap.isEmpty()) {
                throw IllegalArgumentException("Language pack contains no valid string entries.")
            }

            LanguagePack(
                schemaVersion = schemaVersion,
                languageCode = languageCode,
                displayName = displayName,
                nativeName = nativeName,
                localeTag = localeTag,
                isRtl = isRtl,
                packVersion = packVersion,
                strings = stringsMap
            )
        }
    }

    /**
     * Validates and parses a remote manifest JSON string.
     */
    fun parseManifest(jsonString: String): Result<LanguageManifest> {
        return runCatching {
            val root = JSONObject(jsonString)
            val manifestVersion = root.optInt("manifestVersion", 1)
            val packsArray = root.optJSONArray("packs") ?: JSONArray()

            val packs = mutableListOf<RemoteLanguagePackInfo>()
            for (i in 0 until packsArray.length()) {
                val item = packsArray.getJSONObject(i)
                val code = item.optString("languageCode", "").trim()
                val downloadUrl = item.optString("downloadUrl", "").trim()

                // Security: Enforce HTTPS only
                if (!downloadUrl.startsWith("https://", ignoreCase = true)) {
                    continue // Skip non-HTTPS entries
                }

                if (code.isNotEmpty()) {
                    packs.add(
                        RemoteLanguagePackInfo(
                            languageCode = code,
                            displayName = item.optString("displayName", code),
                            nativeName = item.optString("nativeName", code),
                            localeTag = item.optString("localeTag", code),
                            isRtl = item.optBoolean("isRtl", false),
                            packVersion = item.optInt("packVersion", 1),
                            downloadUrl = downloadUrl,
                            sha256Checksum = item.optString("sha256", null),
                            minAppVersion = item.optInt("minAppVersion", 1)
                        )
                    )
                }
            }

            LanguageManifest(
                manifestVersion = manifestVersion,
                packs = packs
            )
        }
    }

    /**
     * Calculates SHA-256 hash of a string.
     */
    fun calculateSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Calculates SHA-256 hash of a byte array.
     */
    fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
