package com.b2binventory.app.data.remote

import android.util.Log
import com.b2binventory.app.BuildConfig
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Err(
        val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
        val status: Int? = null,
        /** The server could not be reached; the request may be queued and retried. */
        val offline: Boolean = false,
    ) : ApiResult<Nothing>
}

class ApiFailure(val error: ApiResult.Err) : Exception(error.message)

/** Runs a request and converts every failure into a readable [ApiResult.Err]. */
suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Ok(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    e.toErr()
} catch (e: ApiFailure) {
    e.error
} catch (e: IOException) {
    Log.w(TAG, "Request to ${BuildConfig.API_BASE_URL} failed", e)
    e.toNetworkErr()
} catch (e: kotlinx.serialization.SerializationException) {
    Log.w(TAG, "Unexpected response", e)
    ApiResult.Err("Unexpected response from the server.")
} catch (e: Exception) {
    Log.w(TAG, "Request failed", e)
    ApiResult.Err(e.message ?: "Something went wrong")
}

private const val TAG = "ApiCall"

/** Says why the server could not be reached; all of these may be queued and retried. */
fun IOException.toNetworkErr(): ApiResult.Err {
    val server = BuildConfig.API_BASE_URL.removePrefix("http://").removePrefix("https://").trimEnd('/')
    val message = when (this) {
        is SocketTimeoutException -> "The server took too long to respond. Try again."
        is UnknownHostException -> "No internet connection, or the server address $server can't be found."
        is ConnectException, is NoRouteToHostException ->
            "Cannot reach the server at $server. Make sure the backend is running and this device is on the same network."
        is SSLException -> "A secure connection to the server could not be made."
        else -> "Cannot reach the server. Check your connection and that the backend is running."
    }
    return ApiResult.Err(message, offline = true)
}

/** For endpoints that return Response<Unit>. */
fun retrofit2.Response<*>.requireSuccess() {
    if (!isSuccessful) throw HttpException(this)
}

fun HttpException.toErr(): ApiResult.Err {
    val raw = response()?.errorBody()?.string()
    val parsed = raw?.let { runCatching { JsonConfig.json.decodeFromString(ErrorBody.serializer(), it) }.getOrNull() }
    val status = code()
    val fallback = when (status) {
        400, 422 -> "Please check the details you entered."
        401 -> "Your session has expired. Please sign in again."
        403 -> "You do not have permission to do this."
        404 -> "Not found."
        409 -> "This conflicts with existing data."
        502, 503, 504 -> "The server is starting up or temporarily unavailable. Try again in a moment."
        in 500..599 -> "The server had a problem. Try again shortly."
        else -> "Request failed ($status)"
    }
    return ApiResult.Err(parsed?.message ?: fallback, parsed?.fieldErrors ?: emptyMap(), status)
}
