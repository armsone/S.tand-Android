package com.armsone.stand.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.armsone.stand.model.TvUiModePolicy
import java.io.Closeable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CurrentWeather(
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double,
    val precipitationMillimeters: Double,
    val weatherCode: Int,
    val isDay: Boolean,
) {
    val summary: String
        get() = WmoKoreanSummary.forCode(weatherCode)
}

enum class WeatherAvailability {
    IDLE,
    REQUESTING_LOCATION,
    LOADING,
    AVAILABLE,
    LOCATION_DENIED,
    PROVIDER_UNAVAILABLE,
    OFFLINE,
    FAILED,
    CLOSED,
}

private data class WeatherCoordinate(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Foreground-only weather source. Runtime permission ownership deliberately stays with the caller.
 *
 * [refreshIfNeeded] never displays a permission dialog and only uses the coarse network provider.
 * Call [close] from the owning ViewModel's `onCleared` to cancel location and network work.
 */
class WeatherService(context: Context) : Closeable {
    private val applicationContext = context.applicationContext
    private val isTelevision = TvUiModePolicy.isTelevision(applicationContext.resources.configuration)
    private val locationManager = applicationContext.getSystemService(
        Context.LOCATION_SERVICE,
    ) as LocationManager
    private val geocoder = Geocoder(applicationContext, Locale.KOREA)
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("WeatherService"),
    )
    private val callbackHandler = Handler(Looper.getMainLooper())
    private val callbackExecutor = Executor { command ->
        if (!callbackHandler.post(command)) command.run()
    }

    private val mutableWeather = MutableStateFlow<CurrentWeather?>(null)
    private val mutableLocationName = MutableStateFlow<String?>(null)
    private val mutableAvailability = MutableStateFlow(WeatherAvailability.IDLE)
    private val mutableLastUpdated = MutableStateFlow<Instant?>(null)

    val weather: StateFlow<CurrentWeather?> = mutableWeather.asStateFlow()
    val locationName: StateFlow<String?> = mutableLocationName.asStateFlow()
    val availability: StateFlow<WeatherAvailability> = mutableAvailability.asStateFlow()
    val lastUpdated: StateFlow<Instant?> = mutableLastUpdated.asStateFlow()

    private val isClosed = AtomicBoolean(false)
    private val isLocationEnabled = AtomicBoolean(true)
    private val requestGeneration = AtomicLong(0L)
    private val activeConnection = AtomicReference<HttpURLConnection?>(null)
    private val resourceLock: Any = this

    private var isForeground = false
    private var hasLocationPermission = false
    private var lastSuccessCoordinate: WeatherCoordinate? = null
    private var lastAttemptRealtimeMillis: Long? = null
    private var lastFailureRealtimeMillis: Long? = null
    private var pendingMovementRefresh = false
    private var pendingMovementLocation: Location? = null
    private var pendingForceRefresh = false
    private var movementUpdateFailed = false
    private var continuousLocationListener: LocationListener? = null
    private var tickerJob: Job? = null

    private var pendingLocationRequestId: Long? = null
    private var pendingCancellationSignal: CancellationSignal? = null
    private var pendingLocationListener: LocationListener? = null
    private var locationTimeoutJob: Job? = null
    private var refreshJob: Job? = null

    @Synchronized
    fun onAppForeground(hasPermission: Boolean) {
        if (isClosed.get()) return
        isForeground = true
        hasLocationPermission = hasPermission

        if (!isLocationEnabled.get()) {
            mutableAvailability.value = WeatherAvailability.IDLE
            return
        }

        if (!hasLocationPermission) {
            mutableAvailability.value = WeatherAvailability.LOCATION_DENIED
            return
        }

        startContinuousLocationObserverLocked()
        startTickerLocked()
        checkAndTriggerDueRefreshLocked()
    }

    @Synchronized
    fun onAppBackground() {
        if (isClosed.get()) return
        isForeground = false
        pendingMovementLocation = null
        stopContinuousLocationObserverLocked()
        stopTickerLocked()
        invalidateAndCancelActiveWork()
        if (mutableAvailability.value in IN_FLIGHT_AVAILABILITIES) {
            mutableAvailability.value = if (mutableWeather.value == null) {
                WeatherAvailability.IDLE
            } else {
                WeatherAvailability.AVAILABLE
            }
        }
    }

    @Synchronized
    fun updatePermissions(hasPermission: Boolean) {
        if (isClosed.get()) return
        val changed = hasLocationPermission != hasPermission
        hasLocationPermission = hasPermission

        if (!hasLocationPermission) {
            stopContinuousLocationObserverLocked()
            stopTickerLocked()
            invalidateAndCancelActiveWork()
            mutableAvailability.value = WeatherAvailability.LOCATION_DENIED
            return
        }

        if (changed && isForeground && isLocationEnabled.get()) {
            startContinuousLocationObserverLocked()
            startTickerLocked()
            checkAndTriggerDueRefreshLocked()
        }
    }

    /**
     * Refreshes stale weather without requesting permissions itself.
     *
     * A cached successful result remains visible when a later refresh fails. [availability] reports
     * why the refresh failed so the UI can distinguish stale data from a current result.
     */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun refreshIfNeeded(
        hasLocationPermission: Boolean,
        force: Boolean = false,
    ) {
        if (isClosed.get()) return
        this.hasLocationPermission = hasLocationPermission

        if (!isLocationEnabled.get()) {
            stopContinuousLocationObserverLocked()
            stopTickerLocked()
            invalidateAndCancelActiveWork()
            clearCachedWeather()
            mutableAvailability.value = WeatherAvailability.IDLE
            return
        }

        if (!hasLocationPermission) {
            stopContinuousLocationObserverLocked()
            stopTickerLocked()
            invalidateAndCancelActiveWork()
            mutableAvailability.value = WeatherAvailability.LOCATION_DENIED
            return
        }

        if (force) {
            pendingForceRefresh = true
            checkAndTriggerDueRefreshLocked()
            return
        }

        val isStale = mutableLastUpdated.value == null || !WeatherCachePolicy.isFresh(
            mutableLastUpdated.value,
            Instant.now(),
        )

        if (!isStale && !pendingMovementRefresh && !movementUpdateFailed) {
            if (mutableAvailability.value !in IN_FLIGHT_AVAILABILITIES) {
                mutableAvailability.value = WeatherAvailability.AVAILABLE
            }
            return
        }

        checkAndTriggerDueRefreshLocked()
    }

    @Synchronized
    fun setLocationEnabled(enabled: Boolean) {
        if (isClosed.get() || isLocationEnabled.getAndSet(enabled) == enabled) return
        if (!enabled) {
            stopContinuousLocationObserverLocked()
            stopTickerLocked()
            invalidateAndCancelActiveWork()
            clearCachedWeather()
            lastSuccessCoordinate = null
            lastFailureRealtimeMillis = null
            pendingMovementRefresh = false
            pendingMovementLocation = null
            pendingForceRefresh = false
            movementUpdateFailed = false
            mutableAvailability.value = WeatherAvailability.IDLE
        } else if (isForeground && hasLocationPermission) {
            startContinuousLocationObserverLocked()
            startTickerLocked()
            pendingForceRefresh = true
            checkAndTriggerDueRefreshLocked()
        }
    }

    @Synchronized
    override fun close() {
        if (!isClosed.compareAndSet(false, true)) return

        isForeground = false
        pendingMovementLocation = null
        stopContinuousLocationObserverLocked()
        stopTickerLocked()
        requestGeneration.incrementAndGet()
        cancelActiveWork()
        scope.cancel()
        mutableAvailability.value = WeatherAvailability.CLOSED
    }

    private fun startContinuousLocationObserverLocked() {
        if (continuousLocationListener != null) return
        if (!hasLocationPermission || !isLocationEnabled.get() || isClosed.get() || !isForeground) return

        val providerAvailable = runCatching {
            locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER) &&
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)

        if (!providerAvailable) return

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onContinuousLocationUpdated(location)
            }

            override fun onProviderDisabled(provider: String) {
                if (provider == LocationManager.NETWORK_PROVIDER) {
                    synchronized(resourceLock) {
                        stopContinuousLocationObserverLocked()
                    }
                }
            }

            override fun onProviderEnabled(provider: String) = Unit
        }

        val registered = runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                CONTINUOUS_LOCATION_MIN_TIME_MILLIS,
                CONTINUOUS_LOCATION_MIN_DISTANCE_METERS,
                listener,
                Looper.getMainLooper(),
            )
            true
        }.getOrDefault(false)

        if (registered) {
            continuousLocationListener = listener
        }
    }

    private fun stopContinuousLocationObserverLocked() {
        continuousLocationListener?.let { listener ->
            runCatching { locationManager.removeUpdates(listener) }
        }
        continuousLocationListener = null
    }

    private fun onContinuousLocationUpdated(location: Location) {
        synchronized(resourceLock) {
            if (!isForeground || !isLocationEnabled.get() || !hasLocationPermission || isClosed.get()) {
                return
            }

            if (!WeatherLocationPolicy.isUsable(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    locationElapsedRealtimeNanos = location.elapsedRealtimeNanos,
                    nowElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                )
            ) return
            if (pendingMovementRefresh) pendingMovementLocation = Location(location)
            val lastCoord = lastSuccessCoordinate
            if (lastCoord == null) {
                if (mutableWeather.value == null) {
                    pendingMovementRefresh = true
                    pendingMovementLocation = Location(location)
                    checkAndTriggerDueRefreshLocked()
                }
                return
            }

            val results = FloatArray(1)
            Location.distanceBetween(
                lastCoord.latitude,
                lastCoord.longitude,
                location.latitude,
                location.longitude,
                results,
            )
            val displacement = results[0]
            if (displacement >= DISPLACEMENT_THRESHOLD_METERS) {
                pendingMovementRefresh = true
                pendingMovementLocation = Location(location)
                checkAndTriggerDueRefreshLocked()
            }
        }
    }

    private fun startTickerLocked() {
        if (tickerJob != null || isClosed.get() || !isForeground) return
        tickerJob = scope.launch(CoroutineName("WeatherTicker")) {
            while (isActive) {
                delay(TICKER_INTERVAL_MILLIS)
                synchronized(resourceLock) {
                    if (isClosed.get() || !isForeground) return@launch
                    checkAndTriggerDueRefreshLocked()
                }
            }
        }
    }

    private fun stopTickerLocked() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun checkAndTriggerDueRefreshLocked() {
        if (isClosed.get() || !isForeground || !isLocationEnabled.get() || !hasLocationPermission) {
            return
        }

        if (mutableAvailability.value in IN_FLIGHT_AVAILABILITIES) {
            return
        }

        val nowRealtime = SystemClock.elapsedRealtime()
        val nowInstant = Instant.now()
        startContinuousLocationObserverLocked()
        val lastAttempt = lastAttemptRealtimeMillis
        val spacingMet = lastAttempt == null || (nowRealtime - lastAttempt) >= MIN_REQUEST_SPACING_MILLIS
        val lastFailure = lastFailureRealtimeMillis
        if (lastFailure != null && nowRealtime - lastFailure < FAILURE_RETRY_DELAY_MILLIS) return

        // 1. Movement refresh requested (displacement >= 3000m)
        if (pendingMovementRefresh) {
            val loc = pendingMovementLocation
            val lastCoord = lastSuccessCoordinate
            if (loc != null && lastCoord != null) {
                val results = FloatArray(1)
                Location.distanceBetween(
                    lastCoord.latitude,
                    lastCoord.longitude,
                    loc.latitude,
                    loc.longitude,
                    results,
                )
                if (results[0] < DISPLACEMENT_THRESHOLD_METERS) {
                    pendingMovementRefresh = false
                    pendingMovementLocation = null
                }
            }
            if (pendingMovementRefresh) {
                if (!spacingMet) return
                pendingMovementRefresh = false
                pendingMovementLocation = null
                pendingForceRefresh = false
                triggerRefreshLocked(
                    isMovement = true,
                    locationOverride = loc,
                )
                return
            }
        }

        // 2. Force refresh requested
        if (pendingForceRefresh) {
            if (!spacingMet) return
            pendingForceRefresh = false
            triggerRefreshLocked(isMovement = false)
            return
        }

        // 3. Failure retry due (5 minutes after failure)
        if (lastFailure != null) {
            val failureAge = nowRealtime - lastFailure
            if (failureAge >= FAILURE_RETRY_DELAY_MILLIS) {
                if (!spacingMet) return
                val wasMovement = movementUpdateFailed
                triggerRefreshLocked(
                    isMovement = wasMovement,
                )
            }
            return
        }

        // 4. Periodic or resume refresh due (cache stale >= 15 min or no weather)
        val lastUpdated = mutableLastUpdated.value
        val isStale = lastUpdated == null || !WeatherCachePolicy.isFresh(
            lastUpdated,
            nowInstant,
        )

        if (isStale) {
            if (!spacingMet) return
            triggerRefreshLocked(isMovement = false)
        }
    }

    private fun triggerRefreshLocked(
        isMovement: Boolean,
        locationOverride: Location? = null,
    ) {
        val nowRealtime = SystemClock.elapsedRealtime()
        lastAttemptRealtimeMillis = nowRealtime
        if (lastFailureRealtimeMillis != null) {
            lastFailureRealtimeMillis = nowRealtime
        }

        val requestId = beginRequest()
        mutableAvailability.value = WeatherAvailability.REQUESTING_LOCATION

        if (locationOverride != null &&
            WeatherLocationPolicy.isUsable(
                latitude = locationOverride.latitude,
                longitude = locationOverride.longitude,
                locationElapsedRealtimeNanos = locationOverride.elapsedRealtimeNanos,
                nowElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
            )
        ) {
            loadWeather(requestId, locationOverride, isMovement)
            return
        }

        requestCoarseLocation(
            requestId = requestId,
            isMovement = isMovement,
        )
    }

    private fun beginRequest(): Long {
        val requestId = requestGeneration.incrementAndGet()
        cancelActiveWork()
        return requestId
    }

    private fun invalidateAndCancelActiveWork() {
        requestGeneration.incrementAndGet()
        cancelActiveWork()
    }

    private fun clearCachedWeather() {
        mutableWeather.value = null
        mutableLocationName.value = null
        mutableLastUpdated.value = null
    }

    @SuppressLint("MissingPermission")
    private fun requestCoarseLocation(
        requestId: Long,
        isMovement: Boolean,
    ) {
        val providerToUse = try {
            resolveCoarseProviderToUse()
        } catch (_: SecurityException) {
            handleFailure(requestId, WeatherAvailability.LOCATION_DENIED, isMovement)
            return
        }

        if (providerToUse == null) {
            if (isTelevision && tryTvFallback(requestId, isMovement)) {
                return
            }
            if (isCurrentRequest(requestId)) {
                handleFailure(requestId, WeatherAvailability.PROVIDER_UNAVAILABLE, isMovement)
            }
            return
        }

        if (!isMovement && tryUseCachedWeatherLocation(requestId, providerToUse, isMovement)) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestCurrentLocation(requestId, providerToUse, isMovement)
        } else {
            requestSingleLocationUpdate(requestId, providerToUse, isMovement)
        }
    }

    /**
     * API 29 coarse-location privacy throttling can hold network fixes to one per ~10 minutes,
     * so a fresh request can time out even though the system already has a recent, usable fix.
     * Non-movement weather requests use that cache directly instead of waiting on the throttle.
     */
    @SuppressLint("MissingPermission")
    private fun tryUseCachedWeatherLocation(
        requestId: Long,
        provider: String,
        isMovement: Boolean,
    ): Boolean {
        val cached = try {
            locationManager.getLastKnownLocation(provider)
        } catch (_: SecurityException) {
            return false
        } catch (_: RuntimeException) {
            null
        } ?: return false

        if (!WeatherLocationPolicy.isUsableForWeather(
                latitude = cached.latitude,
                longitude = cached.longitude,
                locationElapsedRealtimeNanos = cached.elapsedRealtimeNanos,
                nowElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
            )
        ) {
            return false
        }

        if (!isCurrentRequest(requestId)) return false
        loadWeather(requestId = requestId, location = cached, isMovement = isMovement)
        return true
    }

    private fun resolveCoarseProviderToUse(): String? {
        val networkAvailable = isProviderUsable(LocationManager.NETWORK_PROVIDER)
        if (networkAvailable) return LocationManager.NETWORK_PROVIDER

        if (isTelevision && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val fusedAvailable = isProviderUsable(LocationManager.FUSED_PROVIDER)
            if (fusedAvailable) return LocationManager.FUSED_PROVIDER
        }

        return null
    }

    private fun isProviderUsable(provider: String): Boolean {
        return try {
            locationManager.allProviders.contains(provider) &&
                locationManager.isProviderEnabled(provider)
        } catch (error: SecurityException) {
            throw error
        } catch (_: RuntimeException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryTvStaticFallback(requestId: Long, isMovement: Boolean): Boolean {
        if (!isTelevision) return false
        val staticLocation = try {
            locationManager.getLastKnownLocation("static")
        } catch (_: SecurityException) {
            handleFailure(requestId, WeatherAvailability.LOCATION_DENIED, isMovement)
            return true
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: RuntimeException) {
            null
        } ?: return false

        if (!WeatherLocationPolicy.isUsableForTvStatic(
                latitude = staticLocation.latitude,
                longitude = staticLocation.longitude,
            )
        ) {
            return false
        }

        if (!isCurrentRequest(requestId)) return false
        loadWeather(requestId = requestId, location = staticLocation, isMovement = isMovement)
        return true
    }

    @Synchronized
    private fun tryTvFallback(requestId: Long, isMovement: Boolean): Boolean {
        if (!isTelevision || !isCurrentRequest(requestId)) return false
        if (!isForeground || !isLocationEnabled.get() || !hasLocationPermission) return false
        if (applicationContext.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            handleFailure(requestId, WeatherAvailability.LOCATION_DENIED, isMovement)
            return true
        }
        // Claim once so late system callbacks cannot start a second fallback.
        if (!requestGeneration.compareAndSet(requestId, requestId + 1L)) return false
        clearLocationRequest(requestId)
        val fallbackId = requestId + 1L
        if (tryTvStaticFallback(fallbackId, isMovement)) return true
        return tryTvIpFallback(fallbackId, isMovement)
    }

    private fun tryTvIpFallback(requestId: Long, isMovement: Boolean): Boolean {
        if (!isTelevision) return false
        if (!isCurrentRequest(requestId)) return false

        loadWeatherViaTvIp(requestId, isMovement)
        return true
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestCurrentLocation(
        requestId: Long,
        provider: String,
        isMovement: Boolean,
    ) {
        val cancellationSignal = CancellationSignal()
        registerLocationRequest(
            requestId = requestId,
            cancellationSignal = cancellationSignal,
            listener = null,
            isMovement = isMovement,
        )

        try {
            locationManager.getCurrentLocation(
                provider,
                cancellationSignal,
                callbackExecutor,
            ) { location ->
                handleLocationResult(requestId, location, isMovement)
            }
        } catch (_: SecurityException) {
            clearLocationRequest(requestId)
            if (isCurrentRequest(requestId)) {
                handleFailure(requestId, WeatherAvailability.LOCATION_DENIED, isMovement)
            }
        } catch (_: IllegalArgumentException) {
            clearLocationRequest(requestId)
            if (isTelevision && tryTvFallback(requestId, isMovement)) {
                return
            }
            if (isCurrentRequest(requestId)) {
                handleFailure(requestId, WeatherAvailability.PROVIDER_UNAVAILABLE, isMovement)
            }
        } catch (_: RuntimeException) {
            clearLocationRequest(requestId)
            if (isTelevision && tryTvFallback(requestId, isMovement)) {
                return
            }
            if (isCurrentRequest(requestId)) {
                handleFailure(requestId, WeatherAvailability.FAILED, isMovement)
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun requestSingleLocationUpdate(
        requestId: Long,
        provider: String,
        isMovement: Boolean,
    ) {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                handleLocationResult(requestId, location, isMovement)
            }

            override fun onProviderDisabled(disabledProvider: String) {
                if (disabledProvider == provider) {
                    handleLocationResult(requestId, null, isMovement)
                }
            }
        }

        registerLocationRequest(
            requestId = requestId,
            cancellationSignal = null,
            listener = listener,
            isMovement = isMovement,
        )

        try {
            locationManager.requestSingleUpdate(
                provider,
                listener,
                Looper.getMainLooper(),
            )
        } catch (_: SecurityException) {
            clearLocationRequest(requestId)
            if (isCurrentRequest(requestId)) {
                handleFailure(requestId, WeatherAvailability.LOCATION_DENIED, isMovement)
            }
        } catch (_: IllegalArgumentException) {
            clearLocationRequest(requestId)
            if (isTelevision && tryTvFallback(requestId, isMovement)) {
                return
            }
            if (isCurrentRequest(requestId)) {
                handleFailure(requestId, WeatherAvailability.PROVIDER_UNAVAILABLE, isMovement)
            }
        } catch (_: RuntimeException) {
            clearLocationRequest(requestId)
            if (isTelevision && tryTvFallback(requestId, isMovement)) {
                return
            }
            if (isCurrentRequest(requestId)) {
                handleFailure(requestId, WeatherAvailability.FAILED, isMovement)
            }
        }
    }

    private fun registerLocationRequest(
        requestId: Long,
        cancellationSignal: CancellationSignal?,
        listener: LocationListener?,
        isMovement: Boolean,
    ) {
        val timeoutJob = scope.launch(CoroutineName("WeatherLocationTimeout")) {
            delay(LOCATION_TIMEOUT_MILLIS)
            handleLocationTimeout(requestId, isMovement)
        }

        val accepted = synchronized(resourceLock) {
            if (!isCurrentRequest(requestId)) {
                false
            } else {
                pendingLocationRequestId = requestId
                pendingCancellationSignal = cancellationSignal
                pendingLocationListener = listener
                locationTimeoutJob = timeoutJob
                true
            }
        }

        if (!accepted) {
            timeoutJob.cancel()
            cancellationSignal?.cancel()
            listener?.let { runCatching { locationManager.removeUpdates(it) } }
        }
    }

    @Synchronized
    private fun handleLocationTimeout(requestId: Long, isMovement: Boolean) {
        if (!requestGeneration.compareAndSet(requestId, requestId + 1L)) return

        clearLocationRequest(requestId)
        if (!isClosed.get()) {
            if (isTelevision && tryTvFallback(requestId + 1L, isMovement)) {
                return
            }
            synchronized(resourceLock) {
                lastFailureRealtimeMillis = SystemClock.elapsedRealtime()
                if (isMovement) {
                    movementUpdateFailed = true
                }
                mutableAvailability.value = WeatherAvailability.PROVIDER_UNAVAILABLE
            }
        }
    }

    @Synchronized
    private fun handleLocationResult(requestId: Long, location: Location?, isMovement: Boolean) {
        if (!isCurrentRequest(requestId)) return

        clearLocationRequest(requestId)
        if (location == null || !WeatherLocationPolicy.isUsableForWeather(
                latitude = location.latitude,
                longitude = location.longitude,
                locationElapsedRealtimeNanos = location.elapsedRealtimeNanos,
                nowElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
            )
        ) {
            if (isTelevision && tryTvFallback(requestId, isMovement)) {
                return
            }
            handleFailure(requestId, WeatherAvailability.PROVIDER_UNAVAILABLE, isMovement)
            return
        }

        loadWeather(requestId = requestId, location = location, isMovement = isMovement)
    }

    private fun handleFailure(
        requestId: Long,
        availability: WeatherAvailability,
        isMovement: Boolean,
    ) {
        synchronized(resourceLock) {
            if (!isCurrentRequest(requestId)) return
            lastFailureRealtimeMillis = SystemClock.elapsedRealtime()
            if (isMovement) {
                movementUpdateFailed = true
            }
            reportFailure(requestId, availability)
        }
    }

    @Synchronized
    private fun loadWeather(
        requestId: Long,
        location: Location,
        isMovement: Boolean,
    ) {
        if (!isCurrentRequest(requestId)) return
        mutableAvailability.value = WeatherAvailability.LOADING

        val job = scope.launch(CoroutineName("WeatherRefresh")) {
            try {
                val currentWeather = fetchWeather(requestId, location.latitude, location.longitude)
                coroutineContext.ensureActive()
                if (!isCurrentRequest(requestId)) return@launch

                val nowInstant = Instant.now()
                synchronized(resourceLock) {
                    if (!isCurrentRequest(requestId)) return@launch
                    mutableWeather.value = currentWeather
                    mutableLocationName.value = null
                    mutableLastUpdated.value = nowInstant
                    lastSuccessCoordinate = WeatherCoordinate(
                        latitude = location.latitude,
                        longitude = location.longitude,
                    )
                    lastFailureRealtimeMillis = null
                    movementUpdateFailed = false
                    mutableAvailability.value = WeatherAvailability.AVAILABLE
                }

                val resolvedLocationName = resolveLocationName(location)
                coroutineContext.ensureActive()
                synchronized(resourceLock) {
                    if (isCurrentRequest(requestId)) {
                        mutableLocationName.value = resolvedLocationName
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: WeatherHttpException) {
                handleFailure(requestId, WeatherAvailability.FAILED, isMovement)
            } catch (_: WeatherDecodingException) {
                handleFailure(requestId, WeatherAvailability.FAILED, isMovement)
            } catch (_: IOException) {
                handleFailure(requestId, WeatherAvailability.OFFLINE, isMovement)
            } catch (_: SecurityException) {
                handleFailure(requestId, WeatherAvailability.FAILED, isMovement)
            } catch (_: RuntimeException) {
                handleFailure(requestId, WeatherAvailability.FAILED, isMovement)
            }
        }

        val accepted = synchronized(resourceLock) {
            if (!isCurrentRequest(requestId)) {
                false
            } else {
                refreshJob = job
                true
            }
        }
        if (!accepted) job.cancel()
    }

    @Synchronized
    private fun loadWeatherViaTvIp(requestId: Long, isMovement: Boolean) {
        if (!isCurrentRequest(requestId)) return
        mutableAvailability.value = WeatherAvailability.LOADING

        val job = scope.launch(CoroutineName("WeatherRefreshTvIp")) {
            try {
                val ipLocation = fetchTvIpLocation(requestId)
                coroutineContext.ensureActive()
                if (!isCurrentRequest(requestId)) return@launch

                if (ipLocation == null) {
                    handleFailure(requestId, WeatherAvailability.PROVIDER_UNAVAILABLE, isMovement)
                    return@launch
                }

                val currentWeather = fetchWeather(
                    requestId = requestId,
                    latitude = ipLocation.latitude,
                    longitude = ipLocation.longitude,
                )
                coroutineContext.ensureActive()
                if (!isCurrentRequest(requestId)) return@launch

                val displayName = formatTvIpLocationDisplayName(ipLocation.regionName)
                val nowInstant = Instant.now()
                synchronized(resourceLock) {
                    if (!isCurrentRequest(requestId)) return@launch
                    mutableWeather.value = currentWeather
                    mutableLocationName.value = displayName
                    mutableLastUpdated.value = nowInstant
                    lastSuccessCoordinate = WeatherCoordinate(
                        latitude = ipLocation.latitude,
                        longitude = ipLocation.longitude,
                    )
                    lastFailureRealtimeMillis = null
                    movementUpdateFailed = false
                    mutableAvailability.value = WeatherAvailability.AVAILABLE
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: WeatherHttpException) {
                handleFailure(requestId, WeatherAvailability.FAILED, isMovement)
            } catch (_: WeatherDecodingException) {
                handleFailure(requestId, WeatherAvailability.FAILED, isMovement)
            } catch (_: IOException) {
                handleFailure(requestId, WeatherAvailability.OFFLINE, isMovement)
            } catch (_: SecurityException) {
                handleFailure(requestId, WeatherAvailability.FAILED, isMovement)
            } catch (_: RuntimeException) {
                handleFailure(requestId, WeatherAvailability.FAILED, isMovement)
            }
        }

        val accepted = synchronized(resourceLock) {
            if (!isCurrentRequest(requestId)) {
                false
            } else {
                refreshJob = job
                true
            }
        }
        if (!accepted) job.cancel()
    }

    private fun formatTvIpLocationDisplayName(regionName: String?): String {
        val baseName = regionName?.trim().orEmpty()
        return if (baseName.isNotEmpty()) {
            "$baseName · IP 추정"
        } else {
            "IP 추정"
        }
    }

    private suspend fun fetchTvIpLocation(requestId: Long): TvIpLocationResult? {
        coroutineContext.ensureActive()
        val url = URL(IP_WHOIS_URL)
        val connection = url.openConnection() as HttpURLConnection
        try {
            coroutineContext.ensureActive()
            synchronized(resourceLock) {
                if (!isCurrentRequest(requestId)) throw CancellationException()
                activeConnection.set(connection)
            }

            connection.requestMethod = "GET"
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.doInput = true

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                return null
            }

            val payload = connection.inputStream.use { stream ->
                val buffer = ByteArray(MAX_IP_RESPONSE_BYTES + 1)
                var totalRead = 0
                while (totalRead < buffer.size) {
                    coroutineContext.ensureActive()
                    val read = stream.read(buffer, totalRead, buffer.size - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                if (totalRead > MAX_IP_RESPONSE_BYTES) return null
                String(buffer, 0, totalRead, Charsets.UTF_8)
            }

            coroutineContext.ensureActive()
            if (!isCurrentRequest(requestId)) throw CancellationException()

            return IpWhoisJsonDecoder.decode(payload)
        } finally {
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    private suspend fun fetchWeather(
        requestId: Long,
        latitude: Double,
        longitude: Double,
    ): CurrentWeather {
        coroutineContext.ensureActive()
        val url = OpenMeteoRequest.url(
            latitude = latitude,
            longitude = longitude,
        )
        val connection = url.openConnection() as HttpURLConnection
        try {
            coroutineContext.ensureActive()
            synchronized(resourceLock) {
                if (!isCurrentRequest(requestId)) throw CancellationException()
                activeConnection.set(connection)
            }

            connection.requestMethod = "GET"
            connection.connectTimeout = NETWORK_TIMEOUT_MILLIS
            connection.readTimeout = NETWORK_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            connection.useCaches = false
            connection.doInput = true

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) throw WeatherHttpException(statusCode)

            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
            coroutineContext.ensureActive()
            if (!isCurrentRequest(requestId)) throw CancellationException()
            return OpenMeteoJsonDecoder.decode(payload)
        } finally {
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveLocationName(location: Location): String? {
        val geocoderAvailable = try {
            Geocoder.isPresent()
        } catch (_: RuntimeException) {
            false
        }
        if (!geocoderAvailable) return null

        val address = try {
            geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: RuntimeException) {
            null
        } ?: return null

        return LocationNameFormatter.format(
            administrativeArea = address.adminArea,
            locality = address.locality,
            subAdministrativeArea = address.subAdminArea,
            subLocality = address.subLocality,
            country = address.countryName,
        )
    }

    private fun reportFailure(requestId: Long, availability: WeatherAvailability) {
        if (isCurrentRequest(requestId)) {
            mutableAvailability.value = availability
        }
    }

    private fun isCurrentRequest(requestId: Long): Boolean =
        !isClosed.get() && requestGeneration.get() == requestId

    private fun cancelActiveWork() {
        clearLocationRequest()

        val job = synchronized(resourceLock) {
            refreshJob.also { refreshJob = null }
        }
        job?.cancel()
        activeConnection.getAndSet(null)?.disconnect()
    }

    private fun clearLocationRequest(requestId: Long? = null) {
        val resources = synchronized(resourceLock) {
            if (requestId != null && pendingLocationRequestId != requestId) {
                null
            } else {
                LocationRequestResources(
                    cancellationSignal = pendingCancellationSignal,
                    listener = pendingLocationListener,
                    timeoutJob = locationTimeoutJob,
                ).also {
                    pendingLocationRequestId = null
                    pendingCancellationSignal = null
                    pendingLocationListener = null
                    locationTimeoutJob = null
                }
            }
        } ?: return

        resources.timeoutJob?.cancel()
        resources.cancellationSignal?.cancel()
        resources.listener?.let { listener ->
            runCatching { locationManager.removeUpdates(listener) }
        }
    }

    private data class LocationRequestResources(
        val cancellationSignal: CancellationSignal?,
        val listener: LocationListener?,
        val timeoutJob: Job?,
    )

    private companion object {
        val IN_FLIGHT_AVAILABILITIES = setOf(
            WeatherAvailability.REQUESTING_LOCATION,
            WeatherAvailability.LOADING,
        )
        const val LOCATION_TIMEOUT_MILLIS = 15_000L
        const val NETWORK_TIMEOUT_MILLIS = 10_000
        const val MIN_REQUEST_SPACING_MILLIS = 60_000L
        const val FAILURE_RETRY_DELAY_MILLIS = 5 * 60_000L
        const val DISPLACEMENT_THRESHOLD_METERS = 3000f
        const val TICKER_INTERVAL_MILLIS = 5_000L
        const val CONTINUOUS_LOCATION_MIN_TIME_MILLIS = 30_000L
        const val CONTINUOUS_LOCATION_MIN_DISTANCE_METERS = 100f
        const val IP_WHOIS_URL =
            "https://ipwho.is/?fields=success,latitude,longitude,city,region,country"
        const val MAX_IP_RESPONSE_BYTES = 16 * 1024
    }
}

internal object OpenMeteoRequest {
    private const val CURRENT_FIELDS =
        "temperature_2m,apparent_temperature,precipitation,weather_code,is_day"

    fun url(latitude: Double, longitude: Double): URL {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Latitude must be finite and between -90 and 90."
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Longitude must be finite and between -180 and 180."
        }

        val latitudeText = String.format(Locale.US, "%.4f", latitude)
        val longitudeText = String.format(Locale.US, "%.4f", longitude)
        return URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitudeText" +
                "&longitude=$longitudeText" +
                "&current=$CURRENT_FIELDS" +
                "&timezone=auto" +
                "&forecast_days=1",
        )
    }
}

internal object OpenMeteoJsonDecoder {
    private const val JSON_NUMBER =
        "-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?"

    fun decode(payload: String): CurrentWeather {
        val currentObject = objectValue(payload, "current")
        return CurrentWeather(
            temperatureCelsius = numberValue(currentObject, "temperature_2m"),
            apparentTemperatureCelsius = numberValue(
                currentObject,
                "apparent_temperature",
            ),
            precipitationMillimeters = numberValue(currentObject, "precipitation"),
            weatherCode = integerValue(currentObject, "weather_code"),
            isDay = when (val value = integerValue(currentObject, "is_day")) {
                0 -> false
                1 -> true
                else -> throw WeatherDecodingException("is_day must be 0 or 1, but was $value.")
            },
        )
    }

    private fun objectValue(payload: String, key: String): String {
        val keyMatch = Regex("\\\"${Regex.escape(key)}\\\"\\s*:").find(payload)
            ?: throw WeatherDecodingException("Missing JSON object: $key")
        var cursor = keyMatch.range.last + 1
        while (cursor < payload.length && payload[cursor].isWhitespace()) cursor += 1
        if (cursor >= payload.length || payload[cursor] != '{') {
            throw WeatherDecodingException("JSON value for $key is not an object.")
        }

        val objectStart = cursor
        var depth = 0
        var inString = false
        var escaped = false
        while (cursor < payload.length) {
            val character = payload[cursor]
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
            } else {
                when (character) {
                    '"' -> inString = true
                    '{' -> depth += 1
                    '}' -> {
                        depth -= 1
                        if (depth == 0) return payload.substring(objectStart, cursor + 1)
                    }
                }
            }
            cursor += 1
        }

        throw WeatherDecodingException("Unterminated JSON object: $key")
    }

    private fun numberValue(objectPayload: String, key: String): Double {
        val match = Regex(
            "\\\"${Regex.escape(key)}\\\"\\s*:\\s*($JSON_NUMBER)(?=\\s*[,}])",
        ).find(objectPayload) ?: throw WeatherDecodingException("Missing number: $key")
        val value = match.groupValues[1].toDoubleOrNull()
            ?: throw WeatherDecodingException("Invalid number: $key")
        if (!value.isFinite()) throw WeatherDecodingException("Non-finite number: $key")
        return value
    }

    private fun integerValue(objectPayload: String, key: String): Int {
        val value = numberValue(objectPayload, key)
        val integer = value.toInt()
        if (integer.toDouble() != value) {
            throw WeatherDecodingException("Expected an integer for $key, but was $value.")
        }
        return integer
    }
}

internal class WeatherDecodingException(message: String) : Exception(message)

private class WeatherHttpException(val statusCode: Int) : IOException(
    "Open-Meteo returned HTTP $statusCode.",
)

internal object WmoKoreanSummary {
    fun forCode(code: Int): String = when (code) {
        0 -> "맑음"
        1 -> "대체로 맑음"
        2 -> "구름 조금"
        3 -> "흐림"
        45, 48 -> "안개"
        51, 53, 55, 56, 57 -> "이슬비"
        61, 63, 65, 66, 67 -> "비"
        71, 73, 75, 77 -> "눈"
        80, 81, 82 -> "소나기"
        85, 86 -> "눈 소나기"
        95, 96, 99 -> "뇌우"
        else -> "날씨 정보"
    }
}

internal object WeatherCachePolicy {
    private val MAX_AGE: Duration = Duration.ofMinutes(15)

    fun isFresh(lastUpdated: Instant?, now: Instant): Boolean {
        if (lastUpdated == null) return false
        val age = Duration.between(lastUpdated, now)
        return !age.isNegative && age < MAX_AGE
    }
}

internal object WeatherLocationPolicy {
    private const val MAX_AGE_NANOS = 60L * 1_000_000_000L
    private const val MAX_WEATHER_AGE_NANOS = 15L * 60L * 1_000_000_000L

    fun isUsable(
        latitude: Double,
        longitude: Double,
        locationElapsedRealtimeNanos: Long,
        nowElapsedRealtimeNanos: Long,
    ): Boolean = isUsableWithinAge(
        latitude = latitude,
        longitude = longitude,
        locationElapsedRealtimeNanos = locationElapsedRealtimeNanos,
        nowElapsedRealtimeNanos = nowElapsedRealtimeNanos,
        maxAgeNanos = MAX_AGE_NANOS,
    )

    /**
     * Coarse network fixes on throttled devices (e.g. API 29 10-minute privacy throttling) can be
     * minutes old yet still the best available signal for weather, which tolerates a 15-minute cache.
     */
    fun isUsableForWeather(
        latitude: Double,
        longitude: Double,
        locationElapsedRealtimeNanos: Long,
        nowElapsedRealtimeNanos: Long,
    ): Boolean = isUsableWithinAge(
        latitude = latitude,
        longitude = longitude,
        locationElapsedRealtimeNanos = locationElapsedRealtimeNanos,
        nowElapsedRealtimeNanos = nowElapsedRealtimeNanos,
        maxAgeNanos = MAX_WEATHER_AGE_NANOS,
    )

    private fun isUsableWithinAge(
        latitude: Double,
        longitude: Double,
        locationElapsedRealtimeNanos: Long,
        nowElapsedRealtimeNanos: Long,
        maxAgeNanos: Long,
    ): Boolean {
        if (!latitude.isFinite() || latitude !in -90.0..90.0) return false
        if (!longitude.isFinite() || longitude !in -180.0..180.0) return false
        if (locationElapsedRealtimeNanos < 0L || nowElapsedRealtimeNanos < 0L) return false
        if (locationElapsedRealtimeNanos > nowElapsedRealtimeNanos) return false

        return nowElapsedRealtimeNanos - locationElapsedRealtimeNanos < maxAgeNanos
    }

    fun isUsableForTvStatic(
        latitude: Double,
        longitude: Double,
    ): Boolean {
        if (!latitude.isFinite() || latitude !in -90.0..90.0) return false
        if (!longitude.isFinite() || longitude !in -180.0..180.0) return false
        return true
    }

    fun isUsableForTvIp(
        latitude: Double,
        longitude: Double,
    ): Boolean {
        if (!latitude.isFinite() || latitude !in -90.0..90.0) return false
        if (!longitude.isFinite() || longitude !in -180.0..180.0) return false
        return true
    }
}

internal data class TvIpLocationResult(
    val latitude: Double,
    val longitude: Double,
    val regionName: String?,
)

internal object IpWhoisJsonDecoder {
    fun decode(payload: String): TvIpLocationResult? {
        val root = try {
            JSONObject(payload)
        } catch (_: JSONException) {
            return null
        }

        if (root.opt("success") != true) {
            return null
        }

        val latitude = (root.opt("latitude") as? Number)?.toDouble() ?: return null
        val longitude = (root.opt("longitude") as? Number)?.toDouble() ?: return null
        if (!WeatherLocationPolicy.isUsableForTvIp(latitude, longitude)) {
            return null
        }

        val city = (root.opt("city") as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val region = (root.opt("region") as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val country = (root.opt("country") as? String)?.trim()?.takeIf { it.isNotEmpty() }

        val formattedName = LocationNameFormatter.format(
            administrativeArea = region,
            locality = city,
            subAdministrativeArea = null,
            subLocality = null,
            country = country,
        )

        return TvIpLocationResult(
            latitude = latitude,
            longitude = longitude,
            regionName = formattedName,
        )
    }
}

internal object LocationNameFormatter {
    private val DIACRITIC_MARKS = Regex("\\p{M}+")

    fun format(
        administrativeArea: String?,
        locality: String?,
        subAdministrativeArea: String?,
        subLocality: String?,
        country: String?,
    ): String? {
        val seen = mutableSetOf<String>()
        val regionalComponents = listOf(
            administrativeArea,
            locality,
            subAdministrativeArea,
            subLocality,
        ).mapNotNull { value ->
            val trimmed = value?.trim().orEmpty()
            if (trimmed.isEmpty()) return@mapNotNull null

            val comparisonKey = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replace(DIACRITIC_MARKS, "")
                .lowercase(Locale.ROOT)
            trimmed.takeIf { seen.add(comparisonKey) }
        }

        if (regionalComponents.isNotEmpty()) return regionalComponents.joinToString(" ")
        return country?.trim()?.takeIf { it.isNotEmpty() }
    }
}
