package app.campusbazaar.data.api

import android.content.Context
import app.campusbazaar.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A failure the UI can show as-is: [message] is written for students. */
class AppException(
    override val message: String,
    val code: String = "error",
    val status: Int = 0,
) : Exception(message) {
    val isNetwork get() = code == "network"
    val isUnauthorized get() = status == 401
}

val appJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
}

/** Runs an API call and converts transport/HTTP errors into [AppException] with a readable message. */
suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: HttpException) {
    val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
    val detail = body?.let { runCatching { appJson.decodeFromString(ApiErrorBody.serializer(), it).error }.getOrNull() }
    val fallback = when (e.code()) {
        401 -> "Your session has expired. Sign in again."
        404 -> "That's no longer available."
        429 -> "Too many requests. Try again in a minute."
        in 500..599 -> "CampusBazaar is having trouble right now. Try again shortly."
        else -> "Something went wrong. Try again."
    }
    Result.failure(AppException(detail?.message?.takeIf { it.isNotBlank() } ?: fallback, detail?.code ?: "http_${e.code()}", e.code()))
} catch (e: IOException) {
    Result.failure(AppException("You're offline or the connection dropped. Check your internet and try again.", "network"))
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(AppException("Something went wrong. Try again.", "unknown"))
}

object ApiClient {
    fun okHttp(context: Context, tokenProvider: () -> String?, onUnauthorized: () -> Unit): OkHttpClient {
        val auth = Interceptor { chain ->
            val token = tokenProvider()
            val request = chain.request().newBuilder().apply {
                if (token != null) header("Authorization", "Bearer $token")
                header("Accept", "application/json")
                header("X-App-Version", BuildConfig.VERSION_NAME)
            }.build()
            val response = chain.proceed(request)
            if (response.code == 401 && token != null && !request.url.encodedPath.startsWith("/api/auth/")) onUnauthorized()
            response
        }
        // Offline browsing: if a GET fails at the transport level, serve the last cached copy.
        val offlineFallback = Interceptor { chain ->
            val request = chain.request()
            try {
                chain.proceed(request)
            } catch (e: IOException) {
                if (request.method != "GET") throw e
                val cached = chain.proceed(
                    request.newBuilder().cacheControl(CacheControl.Builder().onlyIfCached().maxStale(7, TimeUnit.DAYS).build()).build(),
                )
                if (cached.code == 504) {
                    cached.close()
                    throw e
                }
                cached
            }
        }
        return OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http"), 20L * 1024 * 1024))
            .addInterceptor(offlineFallback)
            .addInterceptor(auth)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun api(client: OkHttpClient): CampusBazaarApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
        .client(client)
        .addConverterFactory(appJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(CampusBazaarApi::class.java)
}
