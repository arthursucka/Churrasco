package com.longynus.churrasco

import org.json.JSONObject
import retrofit2.Response

/** Mensagem que o backend mandou, venha ela no corpo de sucesso ou no de erro. */
fun Response<*>.apiMessage(fallback: String): String {
    (body() as? ApiResponse<*>)?.message?.takeIf { it.isNotBlank() }?.let { return it }

    val rawError = runCatching { errorBody()?.string() }.getOrNull().orEmpty()
    if (rawError.isNotBlank()) {
        runCatching { JSONObject(rawError).optString("message") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
    }

    return fallback
}
