package ir.codemarket.app

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

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
        } catch (e: Exception) { Pair(null, -1) }
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
        } catch (e: Exception) { Pair(null, -1) }
    }

    fun uploadFile(endpoint: String, token: String, zipFile: File, logoFile: File, fields: Map<String, String>): Pair<String?, Int> {
        val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        
        fields.forEach { (key, value) -> multipartBuilder.addFormDataPart(key, value) }
        multipartBuilder.addFormDataPart("zip_file", zipFile.name, zipFile.asRequestBody("application/zip".toMediaType()))
        multipartBuilder.addFormDataPart("logo", logoFile.name, logoFile.asRequestBody("image/png".toMediaType()))

        val request = Request.Builder()
            .url("$baseUrl$endpoint")
            .post(multipartBuilder.build())
            .header("Authorization", "Bearer $token")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                Logger.logNetwork(endpoint, "MULTIPART", body, response.code)
                Pair(body, response.code)
            }
        } catch (e: Exception) { Pair(null, -1) }
    }
}