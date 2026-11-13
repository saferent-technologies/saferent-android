package com.saferenttechnologies.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Hilt entry point. Config sync is NOT triggered here yet (wired later). */
@HiltAndroidApp
class SafeRentApp : Application()
