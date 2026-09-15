package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable as JavaSerializable

/**
 * Annotation marker for serializable data structures.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Serializable

@Serializable
@JsonClass(generateAdapter = true)
data class AIProxyRequest(
    @Json(name = "prompt") val prompt: String,
    @Json(name = "deviceId") val deviceId: String? = null
) : JavaSerializable

@Serializable
@JsonClass(generateAdapter = true)
data class AIProxyResponse(
    @Json(name = "candidates") val candidates: List<AICandidate>? = null,
    @Json(name = "error") val error: String? = null
) : JavaSerializable

@Serializable
@JsonClass(generateAdapter = true)
data class AICandidate(
    @Json(name = "content") val content: AIContent? = null
) : JavaSerializable

@Serializable
@JsonClass(generateAdapter = true)
data class AIContent(
    @Json(name = "parts") val parts: List<AIPart>? = null
) : JavaSerializable

@Serializable
@JsonClass(generateAdapter = true)
data class AIPart(
    @Json(name = "text") val text: String? = null
) : JavaSerializable
