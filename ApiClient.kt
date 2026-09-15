package com.myaiaassistant.v4.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Minimal HTTP client for the V4 backend. Uses HttpURLConnection so no extra
 * networking library is required beyond kotlinx-coroutines.
 */
object ApiClient {

    sealed class Result {
        data class Success(val reply: String) : Result()
        data class Failure(val message: String) : Result()
    }

    /** Sends the user's text + short history to POST /command and returns the AI reply. */
    suspend fun sendCommand(
        baseUrl: String,
        text: String,
        history: List<Pair<String, String>>
    ): Result = withContext(Dispatchers.IO) {
        try {
            val url = URL(joinUrl(baseUrl, "/command"))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = TimeUnit.SECONDS.toMillis(15).toInt()
                readTimeout = TimeUnit.SECONDS.toMillis(30).toInt()
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            val historyArray = JSONArray()
            history.takeLast(10).forEach { (role, content) ->
                historyArray.put(JSONObject().put("role", role).put("content", content))
            }
            val body = JSONObject()
                .put("text", text)
                .put("history", historyArray)
                .toString()

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()

            if (code in 200..299) {
                val reply = JSONObject(responseText).optString("reply", "")
                Result.Success(reply)
            } else {
                Result.Failure("Server error ($code): $responseText")
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Network error")
        }
    }

    /** Sends a base64 PNG screenshot to POST /analyze-screen and returns the AI reply. */
    suspend fun analyzeScreen(
        baseUrl: String,
        imageBase64: String,
        prompt: String = "Describe what is visible and suggest safe next steps."
    ): Result = withContext(Dispatchers.IO) {
        try {
            val url = URL(joinUrl(baseUrl, "/analyze-screen"))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = TimeUnit.SECONDS.toMillis(15).toInt()
                readTimeout = TimeUnit.SECONDS.toMillis(45).toInt()
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            val body = JSONObject()
                .put("image_base64", imageBase64)
                .put("prompt", prompt)
                .toString()

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()

            if (code in 200..299) {
                val reply = JSONObject(responseText).optString("reply", "")
                Result.Success(reply)
            } else {
                Result.Failure("Server error ($code): $responseText")
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Network error")
        }
    }

    private fun joinUrl(base: String, path: String): String =
        base.trimEnd('/') + path
}
