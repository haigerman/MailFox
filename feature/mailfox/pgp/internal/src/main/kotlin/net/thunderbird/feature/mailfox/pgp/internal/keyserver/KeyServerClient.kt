package net.thunderbird.feature.mailfox.pgp.internal.keyserver

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal interface KeyServerClient {
    /**
     * Downloads all key data published for [emailAddress].
     *
     * @throws IOException if keys.openpgp.org could not be reached.
     */
    fun fetchKeys(emailAddress: String): List<ByteArray>

    /**
     * Uploads [armoredCertificate] to keys.openpgp.org and requests the verification email for [emailAddress].
     *
     * @throws IOException on network or server errors.
     */
    fun publish(armoredCertificate: String, emailAddress: String): PublishStatus
}

internal enum class PublishStatus {
    /** The key is published and can be found by its email address. */
    PUBLISHED,

    /** The key was uploaded; the owner has to confirm the address via the verification email. */
    VERIFICATION_PENDING,
}

internal class OkHttpKeyServerClient(
    private val keysOpenPgpOrgBaseUrl: String = KeyServerUrls.KEYS_OPENPGP_ORG,
    private val wkdUrls: (String) -> List<String> = KeyServerUrls::wkd,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build(),
) : KeyServerClient {

    override fun fetchKeys(emailAddress: String): List<ByteArray> {
        val keys = mutableListOf<ByteArray>()

        // The WKD is optional for domains. Errors (no such host, 404, ...) just mean "no key there".
        for (url in wkdUrls(emailAddress)) {
            val data = runCatching { get(url) }.getOrNull()
            if (data != null) {
                keys.add(data)
                break
            }
        }

        get(KeyServerUrls.keysOpenPgpOrgByEmail(emailAddress, keysOpenPgpOrgBaseUrl))?.let { keys.add(it) }

        return keys
    }

    /**
     * Returns the response body or `null` if there is nothing (HTTP 404).
     */
    private fun get(url: String): ByteArray? {
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            return when {
                response.isSuccessful -> response.body.bytes().takeIf { it.isNotEmpty() }
                response.code == HTTP_NOT_FOUND -> null
                else -> throw IOException("HTTP ${response.code} for $url")
            }
        }
    }

    override fun publish(armoredCertificate: String, emailAddress: String): PublishStatus {
        val uploadResponse = postJson(
            url = KeyServerUrls.keysOpenPgpOrgUpload(keysOpenPgpOrgBaseUrl),
            body = JsonObject(mapOf("keytext" to JsonPrimitive(armoredCertificate))),
        )
        if (uploadResponse.statusOf(emailAddress) == STATUS_PUBLISHED) return PublishStatus.PUBLISHED

        val token = uploadResponse["token"]?.jsonPrimitive?.content ?: throw IOException("No upload token")
        val verifyResponse = postJson(
            url = KeyServerUrls.keysOpenPgpOrgRequestVerify(keysOpenPgpOrgBaseUrl),
            body = JsonObject(
                mapOf(
                    "token" to JsonPrimitive(token),
                    "addresses" to JsonArray(listOf(JsonPrimitive(emailAddress))),
                    "locale" to JsonArray(listOf(JsonPrimitive("de_DE"))),
                ),
            ),
        )

        return if (verifyResponse.statusOf(emailAddress) == STATUS_PUBLISHED) {
            PublishStatus.PUBLISHED
        } else {
            PublishStatus.VERIFICATION_PENDING
        }
    }

    private fun postJson(url: String, body: JsonObject): JsonObject {
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $text")
            return Json.parseToJsonElement(text).jsonObject
        }
    }

    private fun JsonObject.statusOf(emailAddress: String): String? {
        val status = this["status"] as? JsonObject ?: return null
        return status.entries.firstOrNull { it.key.equals(emailAddress, ignoreCase = true) }
            ?.value?.jsonPrimitive?.content
    }

    private companion object {
        const val TIMEOUT_SECONDS = 15L
        const val HTTP_NOT_FOUND = 404
        const val STATUS_PUBLISHED = "published"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
