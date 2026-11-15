package com.saferenttechnologies.android.data.property

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.data.push.awaitOrNull
import com.saferenttechnologies.android.domain.model.AppPermission
import com.saferenttechnologies.android.domain.model.GeoFix
import com.saferenttechnologies.android.domain.repository.LocationProvider
import com.saferenttechnologies.android.domain.repository.PermissionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.saferenttechnologies.android.domain.model.GeoAddress
import java.util.Locale
import kotlin.coroutines.resume
import javax.inject.Inject
import javax.inject.Singleton

/** One fresh fix from FusedLocationProviderClient (never a stale cached one) + device Geocoder for the address. */
@Singleton
class FusedLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissions: PermissionManager,
    private val logger: AppLogger,
) : LocationProvider {

    @SuppressLint("MissingPermission") // checked just below
    override suspend fun currentFix(): GeoFix? {
        if (!permissions.isGranted(AppPermission.LOCATION)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cancel = CancellationTokenSource()
        val location = withTimeoutOrNull(TIMEOUT_MS) {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token).awaitOrNull()
        }
        if (location == null) cancel.cancel()
        // Never log coordinates — only whether we got a fix and its accuracy.
        logger.d(TAG, "GPS fix: ${if (location == null) "none" else "accuracy=${location.accuracy.toInt()}m"}")
        return location?.let { GeoFix(it.latitude, it.longitude, it.accuracy) }
    }

    /** Android Geocoder on the phone (Google Play services backed). Never throws; null = not found. */
    override suspend fun addressOf(latitude: Double, longitude: Double): GeoAddress? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine<Address?> { cont ->
                    geocoder.getFromLocation(
                        latitude,
                        longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        },
                    )
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }.getOrNull()
                }
            }
        }
        logger.d(TAG, "Device geocoder: ${if (address == null) "no address" else "found"}")
        return address?.toGeoAddress()
    }

    /** India-style: subLocality = area (Madhapur), locality = city (Hyderabad), adminArea = state. */
    private fun Address.toGeoAddress(): GeoAddress {
        val street = listOfNotNull(subThoroughfare, thoroughfare).joinToString(", ").ifBlank { premises.orEmpty() }
        val city = locality ?: subAdminArea
        return GeoAddress(
            addressLine = street.ifBlank { getAddressLine(0).orEmpty().substringBefore(", ${subLocality ?: city ?: ""}").trim() },
            locality = subLocality.orEmpty(),
            city = city.orEmpty(),
            state = adminArea.orEmpty(),
            pincode = postalCode.orEmpty().filter(Char::isDigit),
            formattedAddress = getAddressLine(0).orEmpty(),
        )
    }

    private companion object {
        const val TAG = "SR-AddProperty"
        const val TIMEOUT_MS = 15_000L
        const val GEOCODE_TIMEOUT_MS = 10_000L
    }
}
