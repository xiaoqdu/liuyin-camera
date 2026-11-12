package com.example.watermark.domain

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class LocationProvider(private val context: Context) {

    /** 一次定位结果：经纬度 + 反查地址（地址可能为空）。 */
    data class Fix(
        val latitude: Double,
        val longitude: Double,
        val address: String?,
        /** 海拔（米）；系统没给就是 null，此时回落到气压计读数。 */
        val altitude: Double? = null
    ) {
        /** 与参考样式一致的「经纬度: 22.945185°N,113.943431°E」。 */
        fun coordinateText(): String {
            val lat = String.format(Locale.US, "%.6f", kotlin.math.abs(latitude))
            val lng = String.format(Locale.US, "%.6f", kotlin.math.abs(longitude))
            return "经纬度: $lat°${if (latitude >= 0) "N" else "S"},$lng°${if (longitude >= 0) "E" else "W"}"
        }
    }

    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    companion object {
        private const val TAG = "LocationProvider"
    }

    suspend fun getBestAddress(): String? = getBestFix()?.address

    /** 取一次定位（经纬度 + 地址），全部在 IO 线程，避免主线程阻塞。 */
    suspend fun getBestFix(): Fix? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "No location permission")
            return@withContext null
        }

        Log.d(TAG, "Trying to get location...")
        val location = tryGetLocation()
        if (location == null) {
            Log.w(TAG, "Failed to get location from all providers")
            return@withContext null
        }

        Fix(
            latitude = location.latitude,
            longitude = location.longitude,
            address = tryGeocode(location),
            altitude = if (location.hasAltitude()) location.altitude else null
        )
    }

    private fun isLocationEnabled(): Boolean {
        return try {
            val mode = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE
            )
            mode != Settings.Secure.LOCATION_MODE_OFF
        } catch (e: Exception) {
            Log.w(TAG, "Cannot check location mode: ${e.message}")
            true
        }
    }

    private fun enableLocation() {
        try {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            Log.d(TAG, "Opened location settings for user to enable")
            Thread.sleep(2000)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open location settings: ${e.message}")
        }
    }

    private suspend fun tryGetLocation(): Location? {
        // 1. Try Fused Location (Google Play Services)
        val fusedLocation = tryGetFusedLocation()
        if (fusedLocation != null) {
            Log.d(TAG, "Got location from FusedProvider")
            return fusedLocation
        }

        // 2. Try Huawei HiLocation (Pangu) - available on Huawei devices without GMS
        val huaweiLocation = tryGetHuaweiLocation()
        if (huaweiLocation != null) {
            Log.d(TAG, "Got location from Huawei HiLocation")
            return huaweiLocation
        }

        // 3. Try LocationManager (native Android)
        val lmLocation = tryGetLocationManagerLocation()
        if (lmLocation != null) {
            Log.d(TAG, "Got location from LocationManager")
            return lmLocation
        }

        // 4. Try to force GPS by registering a listener
        val forcedLocation = tryForceGPS()
        if (forcedLocation != null) {
            Log.d(TAG, "Got location from forced GPS")
            return forcedLocation
        }

        return null
    }

    private suspend fun tryGetFusedLocation(): Location? {
        return try {
            val fusedClient = com.google.android.gms.location.LocationServices
                .getFusedLocationProviderClient(context)
            suspendCancellableCoroutine<Location?> { cont ->
                fusedClient.lastLocation
                    .addOnCompleteListener { task ->
                        // 缓存定位超过 5 分钟就不信：换了城市还打旧坐标，成片就是假的
                        if (task.isSuccessful && task.result != null && isFresh(task.result)) {
                            cont.resume(task.result)
                        } else {
                            Log.w(TAG, "FusedLocation lastLocation failed: ${task.exception?.message}")
                            cont.resume(null)
                        }
                    }
            }
        } catch (e: Exception) {
            Log.w(TAG, "FusedLocation not available: ${e.message}")
            null
        }
    }

    private suspend fun tryGetHuaweiLocation(): Location? {
        return try {
            val hiLocationClass = Class.forName("com.huawei.hms.location.LocationServices")
            val fusedClientMethod = hiLocationClass.getMethod(
                "getFusedLocationProviderClient", Context::class.java
            )
            val fusedClient = fusedClientMethod.invoke(null, context)

            val lastLocationMethod = fusedClient.javaClass.getMethod("lastLocation")
            val task = lastLocationMethod.invoke(fusedClient) as com.google.android.gms.tasks.Task<*>

            suspendCancellableCoroutine<Location?> { cont ->
                task.addOnSuccessListener { location ->
                    if (location is Location && isValidLocation(location)) {
                        cont.resume(location)
                    } else {
                        cont.resume(null)
                    }
                }.addOnFailureListener { e ->
                    Log.w(TAG, "HiLocation failed: ${e.message}")
                    cont.resume(null)
                }
            }
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "Huawei HiLocation API not available")
            null
        } catch (e: Exception) {
            Log.w(TAG, "HiLocation error: ${e.message}")
            null
        }
    }

    private fun tryGetLocationManagerLocation(): Location? {
        val enabledProviders = locationManager.getProviders(true).toList()
        val allProviders = locationManager.getProviders(false).toList()

        Log.d(TAG, "Enabled providers: ${enabledProviders.size}, All providers: ${allProviders.size}")
        if (enabledProviders.isEmpty()) {
            Log.d(TAG, "No enabled providers found, all providers: ${allProviders.joinToString()}")
        }

        val priorityProviders = mutableListOf<String>()

        if (enabledProviders.contains(LocationManager.GPS_PROVIDER)) {
            priorityProviders.add(LocationManager.GPS_PROVIDER)
        }
        if (enabledProviders.contains(LocationManager.NETWORK_PROVIDER)) {
            priorityProviders.add(LocationManager.NETWORK_PROVIDER)
        }

        for (provider in enabledProviders) {
            if (provider !in priorityProviders) {
                priorityProviders.add(provider)
            }
        }

        if (priorityProviders.isEmpty()) {
            priorityProviders.addAll(allProviders)
        }

        Log.d(TAG, "Trying providers: ${priorityProviders.joinToString()}")

        for (provider in priorityProviders) {
            try {
                val location = locationManager.getLastKnownLocation(provider)
                if (location != null && isValidLocation(location) && isFresh(location)) {
                    Log.d(TAG, "Found location from $provider: ${location.latitude},${location.longitude}")
                    return location
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get location from $provider: ${e.message}")
            }
        }

        return null
    }

    private suspend fun tryForceGPS(): Location? {
        return try {
            val result = suspendCancellableCoroutine<Location?> { cont ->
                var delivered = false

                val listener = LocationListener { location ->
                    if (!delivered && isValidLocation(location)) {
                        delivered = true
                        cont.resume(location)
                    }
                }

                val providers = listOfNotNull(
                    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        LocationManager.GPS_PROVIDER
                    } else null,
                    if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        LocationManager.NETWORK_PROVIDER
                    } else null
                )

                if (providers.isNotEmpty()) {
                    try {
                        locationManager.requestLocationUpdates(
                            providers.first(), 0, 0f, listener
                        )
                    } catch (e: SecurityException) {
                        cont.resume(null)
                        return@suspendCancellableCoroutine
                    }
                } else {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }

                cont.invokeOnCancellation {
                    try {
                        locationManager.removeUpdates(listener)
                    } catch (_: Exception) {
                    }
                }
            }

            Thread.sleep(3000)
            result
        } catch (e: Exception) {
            Log.w(TAG, "Force GPS failed: ${e.message}")
            null
        }
    }

    private fun isValidLocation(location: Location): Boolean {
        return location.latitude != 0.0 || location.longitude != 0.0
    }

    /** 缓存定位的时效判断：超过 5 分钟当作过期，宁可等一次实时定位。 */
    private fun isFresh(location: Location): Boolean =
        System.currentTimeMillis() - location.time <= 5 * 60_000L

    private fun tryGeocode(location: Location): String? {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(
                location.latitude, location.longitude, 1
            )

            if (addresses != null && addresses.size > 0) {
                val addr = addresses[0]
                val city = addr.locality
                val district = addr.subAdminArea
                val country = addr.countryName

                val result = buildString {
                    if (!city.isNullOrEmpty()) {
                        append(city)
                    }
                    if (!district.isNullOrEmpty()) {
                        if (length > 0) append(' ')
                        append(district)
                    }
                    if (length == 0 && !district.isNullOrEmpty()) {
                        append(district)
                    }
                    if (length == 0 && !country.isNullOrEmpty()) {
                        append(country)
                    }
                }
                if (result.isNotEmpty()) return result
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geocoding failed: ${e.message}", e)
        }

        return "未知位置"
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
}
