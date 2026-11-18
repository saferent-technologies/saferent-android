package com.saferenttechnologies.android.presentation.panorama

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager

/**
 * Can this phone take a SafeRent 360 photo? Needs a back camera and a gyroscope-based rotation
 * sensor (to know how far the phone has turned). Without them the 360 option is disabled.
 */
object PanoramaSupport {
    fun isSupported(context: Context): Boolean {
        val pm = context.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) return false
        if (!pm.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)) return false
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
        return rotationSensor(sensors) != null
    }

    /** Gyro-only "game" rotation (smooth, no compass jumps), else the normal one. */
    fun rotationSensor(sensors: SensorManager): Sensor? =
        sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
}
