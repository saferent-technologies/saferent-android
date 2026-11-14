package com.saferenttechnologies.android.core.di

import javax.inject.Qualifier

/** Dispatcher for disk / network / parsing work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** BuildConfig.VERSION_NAME of the running app (e.g. "1.0.0"). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppVersionName

/** Preferences DataStore "saferent_config". */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ConfigPreferences

/** Preferences DataStore "saferent_splash" (downloaded splash assets). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SplashPreferences

/** Preferences DataStore "saferent_onboarding". */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OnboardingPreferences

/** Preferences DataStore "saferent_session". */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SessionPreferences
