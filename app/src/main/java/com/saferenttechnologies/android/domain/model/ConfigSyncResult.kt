package com.saferenttechnologies.android.domain.model

/** Outcome of a config sync. A sync never throws and never clears the cache. */
sealed interface ConfigSyncResult {
    /** 200 — new config saved. */
    data class Updated(val version: Int) : ConfigSyncResult

    /** 304 — server config unchanged, cache kept. */
    data object NotModified : ConfigSyncResult

    /** Already synced in this app process — no request was made. */
    data object AlreadySynced : ConfigSyncResult

    /** Error / offline — cache kept untouched. */
    data class Failed(val error: AppError) : ConfigSyncResult
}
