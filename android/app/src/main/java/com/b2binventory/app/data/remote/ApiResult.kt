package com.b2binventory.app.data.remote

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

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
    ApiResult.Err("Cannot reach the server. Check your connection and that the backend is running.", offline = true)
} catch (e: kotlinx.serialization.SerializationException) {
    ApiResult.Err("Unexpected response from the server.")
} catch (e: Exception) {
    ApiResult.Err(e.message ?: "Something went wrong")
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
        401 -> "Your session has expired. Please sign in again."
        403 -> "You do not have permission to do this."
        404 -> "Not found."
        409 -> "This conflicts with existing data."
        in 500..599 -> "The server had a problem. Try again shortly."
        else -> "Request failed ($status)"
    }
    return ApiResult.Err(parsed?.message ?: fallback, parsed?.fieldErrors ?: emptyMap(), status)
}
