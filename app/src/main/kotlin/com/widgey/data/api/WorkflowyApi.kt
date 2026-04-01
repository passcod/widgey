package com.widgey.data.api

import android.util.Log
import com.widgey.data.api.dto.CreateNodeResponse
import com.widgey.data.api.dto.NodeDto
import com.widgey.data.api.dto.NodesListResponse
import com.widgey.data.api.dto.SingleNodeResponse
import com.widgey.data.api.dto.StatusResponse
import com.widgey.data.api.dto.TargetsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class WorkflowyApi(
    private val getApiKey: suspend () -> String?
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val jsonMediaType = "application/json".toMediaType()

    companion object {
        private const val TAG = "WorkflowyApi"
        private const val BASE_URL = "https://workflowy.com/api/v1"
    }

    sealed class ApiResult<out T> {
        data class Success<T>(val data: T) : ApiResult<T>()
        data class Error(val code: Int, val message: String) : ApiResult<Nothing>()
        data class NetworkError(val exception: Exception) : ApiResult<Nothing>()
        data object Unauthorized : ApiResult<Nothing>()
        data object NotFound : ApiResult<Nothing>()
    }

    private suspend fun <T> executeRequest(
        request: Request,
        parseResponse: (String) -> T
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        val url = request.url.toString()
        Log.d(TAG, "--> ${request.method} $url")
        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            Log.d(TAG, "<-- ${response.code} $url (${body.length} bytes)")

            when (response.code) {
                200, 201 -> {
                    try {
                        ApiResult.Success(parseResponse(body))
                    } catch (e: Exception) {
                        Log.e(TAG, "<-- Parse error $url: ${e.message}\nBody: $body", e)
                        ApiResult.Error(response.code, "Failed to parse response: ${e.message}")
                    }
                }
                401 -> {
                    Log.w(TAG, "<-- 401 Unauthorized $url")
                    ApiResult.Unauthorized
                }
                404 -> {
                    Log.w(TAG, "<-- 404 Not Found $url")
                    ApiResult.NotFound
                }
                else -> {
                    Log.e(TAG, "<-- ${response.code} Error $url\nBody: $body")
                    ApiResult.Error(response.code, body)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "<-- Network error $url: ${e.message}", e)
            ApiResult.NetworkError(e)
        } catch (e: Exception) {
            Log.e(TAG, "<-- Unexpected error $url: ${e.message}", e)
            ApiResult.NetworkError(IOException(e.message, e))
        }
    }

    private suspend fun buildAuthorizedRequest(url: String): Request.Builder? {
        val apiKey = getApiKey()
        if (apiKey == null) {
            Log.w(TAG, "buildAuthorizedRequest: no API key set")
            return null
        }
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
    }

    /**
     * Validate the API key by fetching targets
     */
    suspend fun validateApiKey(apiKey: String): ApiResult<TargetsResponse> {
        val request = Request.Builder()
            .url("$BASE_URL/targets")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()

        return executeRequest(request) { body ->
            json.decodeFromString<TargetsResponse>(body)
        }
    }

    /**
     * Get a single node by ID
     */
    suspend fun getNode(nodeId: String): ApiResult<NodeDto> {
        val requestBuilder = buildAuthorizedRequest("$BASE_URL/nodes/$nodeId")
            ?: return ApiResult.Unauthorized

        val request = requestBuilder.get().build()

        return executeRequest(request) { body ->
            json.decodeFromString<SingleNodeResponse>(body).node
        }
    }

    /**
     * Get list of nodes by parent ID
     * Use parentId = "None" for top-level nodes
     */
    suspend fun getNodes(parentId: String?): ApiResult<List<NodeDto>> {
        val url = if (parentId == null) {
            "$BASE_URL/nodes?parent_id=None"
        } else {
            "$BASE_URL/nodes?parent_id=$parentId"
        }

        val requestBuilder = buildAuthorizedRequest(url)
            ?: return ApiResult.Unauthorized

        val request = requestBuilder.get().build()

        return executeRequest(request) { body ->
            json.decodeFromString<NodesListResponse>(body).nodes
        }
    }

    /**
     * Get top-level nodes
     */
    suspend fun getTopLevelNodes(): ApiResult<List<NodeDto>> = getNodes(null)

    /**
     * Create a new node
     */
    suspend fun createNode(
        parentId: String?,
        name: String,
        note: String? = null,
        position: String = "top"
    ): ApiResult<String> {
        val requestBuilder = buildAuthorizedRequest("$BASE_URL/nodes")
            ?: return ApiResult.Unauthorized

        val bodyJson = buildString {
            append("{")
            append("\"parent_id\":${if (parentId == null) "\"None\"" else "\"$parentId\""}")
            append(",\"name\":${json.encodeToString(kotlinx.serialization.serializer<String>(), name)}")
            if (note != null) {
                append(",\"note\":${json.encodeToString(kotlinx.serialization.serializer<String>(), note)}")
            }
            append(",\"position\":\"$position\"")
            append("}")
        }

        val request = requestBuilder
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        return executeRequest(request) { body ->
            json.decodeFromString<CreateNodeResponse>(body).itemId
        }
    }

    /**
     * Update a node's content
     */
    suspend fun updateNode(
        nodeId: String,
        name: String? = null,
        note: String? = null
    ): ApiResult<Unit> {
        val requestBuilder = buildAuthorizedRequest("$BASE_URL/nodes/$nodeId")
            ?: return ApiResult.Unauthorized

        val bodyJson = buildString {
            append("{")
            var first = true
            if (name != null) {
                append("\"name\":${json.encodeToString(kotlinx.serialization.serializer<String>(), name)}")
                first = false
            }
            if (note != null) {
                if (!first) append(",")
                append("\"note\":${json.encodeToString(kotlinx.serialization.serializer<String>(), note)}")
            }
            append("}")
        }

        val request = requestBuilder
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        return executeRequest(request) { body ->
            val response = json.decodeFromString<StatusResponse>(body)
            if (response.status != "ok") {
                throw IOException("Unexpected status: ${response.status}")
            }
        }
    }

    /**
     * Get targets (used for API key validation)
     */
    suspend fun getTargets(): ApiResult<TargetsResponse> {
        val requestBuilder = buildAuthorizedRequest("$BASE_URL/targets")
            ?: return ApiResult.Unauthorized

        val request = requestBuilder.get().build()

        return executeRequest(request) { body ->
            json.decodeFromString<TargetsResponse>(body)
        }
    }
}
