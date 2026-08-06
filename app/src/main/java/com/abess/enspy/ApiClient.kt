package com.abess.enspy

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ApiClient(private val store: SecureStore) {
    private val main = Handler(Looper.getMainLooper())
    private val baseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/')

    fun url(path: String): String =
        if (path.startsWith("http")) path else "$baseUrl${if (path.startsWith("/")) path else "/$path"}"

    fun get(path: String, callback: (Int, String) -> Unit) = request("GET", path, null, callback)
    fun post(path: String, body: JSONObject, callback: (Int, String) -> Unit) =
        request("POST", path, body, callback)
    fun patch(path: String, body: JSONObject, callback: (Int, String) -> Unit) =
        request("PATCH", path, body, callback)
    fun delete(path: String, callback: (Int, String) -> Unit) = request("DELETE", path, null, callback)

    private fun request(method: String, path: String, body: JSONObject?, callback: (Int, String) -> Unit) {
        Thread {
            var connection: HttpURLConnection? = null
            val result = runCatching {
                connection = URL(url(path)).openConnection() as HttpURLConnection
                connection!!.requestMethod = method
                connection!!.connectTimeout = 12_000
                connection!!.readTimeout = 20_000
                connection!!.setRequestProperty("Accept", "application/json")
                store.get("token")?.let { connection!!.setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    connection!!.doOutput = true
                    connection!!.setRequestProperty("Content-Type", "application/json")
                    connection!!.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val status = connection!!.responseCode
                val stream = if (status in 200..399) connection!!.inputStream else connection!!.errorStream
                val text = stream?.let { BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() } }.orEmpty()
                status to text
            }.getOrElse { 0 to (it.message ?: "Connexion impossible") }
            connection?.disconnect()
            main.post { callback(result.first, result.second) }
        }.start()
    }

    fun documentsQuery(search: String, type: String, callback: (Int, String) -> Unit) {
        val params = mutableListOf<String>()
        if (search.isNotBlank()) params += "search=${URLEncoder.encode(search, "UTF-8")}"
        if (type.isNotBlank()) params += "docType=${URLEncoder.encode(type, "UTF-8")}"
        get("/api/documents${if (params.isEmpty()) "" else "?" + params.joinToString("&")}", callback)
    }
}