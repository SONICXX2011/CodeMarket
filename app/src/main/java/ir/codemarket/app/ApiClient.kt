package ir.codemarket.app

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

object ApiClient {
    private val client = OkHttpClient()
    private val baseUrl = NativeLib.getBaseUrl()

    fun postRequest(endpoint: String, payload: String, token: String? = null): Pair<String?, Int> {
        val request = Request.Builder()
            .url("$baseUrl$endpoint")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                Logger.logNetwork(endpoint, payload, body, response.code)
                Pair(body, response.code)
            }
        } catch (e: Exception) {
            Logger.logEvent("Network Error", "Failed: ${e.message}")
            Pair(null, -1)
        }
    }

    fun getRequest(endpoint: String, token: String): Pair<String?, Int> {
        val request = Request.Builder()
            .url("$baseUrl$endpoint")
            .get()
            .header("Authorization", "Bearer $token")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                Logger.logNetwork(endpoint, "GET", body, response.code)
                Pair(body, response.code)
            }
        } catch (e: Exception) {
            Pair(null, -1)
        }
    }
}