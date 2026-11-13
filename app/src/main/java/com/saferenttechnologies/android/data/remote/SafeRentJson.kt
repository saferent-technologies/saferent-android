package com.saferenttechnologies.android.data.remote

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/** Single Json configuration shared by Retrofit and the config cache parser. */
object SafeRentJson {
    @OptIn(ExperimentalSerializationApi::class)
    val instance: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }
}
