package io.github.alexgaignard.powercompare

import io.github.alexgaignard.powercompare.ble.BleManager
import io.github.alexgaignard.powercompare.ble.PowerMeterB
import io.github.alexgaignard.powercompare.compare.Comparison
import io.github.alexgaignard.powercompare.compare.LatestValue
import io.github.alexgaignard.powercompare.compare.PowerComparator
import io.github.alexgaignard.powercompare.datatypes.ComparisonDataType
import io.github.alexgaignard.powercompare.datatypes.DataTypeIds
import io.github.alexgaignard.powercompare.datatypes.PowerBDataType
import io.github.alexgaignard.powercompare.fit.FitFields
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import io.hammerhead.karooext.models.FieldValue
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.ReleaseBluetooth
import io.hammerhead.karooext.models.RequestBluetooth
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.WriteToRecordMesg
import io.hammerhead.karooext.models.WriteToSessionMesg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.ExperimentalUuidApi

/**
 * Compares the Karoo's paired power meter (A) with a second BLE power meter (B) read by this extension.
 */
@OptIn(ExperimentalUuidApi::class)
class PowerCompareExtension : KarooExtension("powercompare", BuildConfig.VERSION_NAME) {
    private val karooSystem by lazy { KarooSystemService(this) }
    private val bleManager by lazy { BleManager(applicationContext) }

    private val latestPowerA = LatestValue()
    private val latestPowerB = LatestValue()
    private val comparator = PowerComparator()
    private val comparison = MutableStateFlow(Comparison())

    @Volatile
    private var rideState: RideState = RideState.Idle

    private val meters = ConcurrentHashMap<String, PowerMeterB>()
    private var serviceJob: Job? = null

    override val types by lazy {
        val latest = comparison.asStateFlow()
        listOf(
            PowerBDataType(extension),
            ComparisonDataType(extension, DataTypeIds.POWER_DIFF, latest) { it.diffWatts },
            ComparisonDataType(extension, DataTypeIds.POWER_DIFF_PERCENT, latest) { it.diffPercent },
            ComparisonDataType(extension, DataTypeIds.RIDE_DIFF_PERCENT, latest) { it.rideDiffPercent },
        )
    }

    override fun onCreate() {
        super.onCreate()
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            karooSystem.connect { connected ->
                if (connected) {
                    karooSystem.dispatch(RequestBluetooth(extension))
                }
            }
            launch {
                karooSystem.streamDataFlow(DataType.Type.POWER).collect { state ->
                    latestPowerA.set((state as? StreamState.Streaming)?.dataPoint?.singleValue)
                }
            }
            launch {
                karooSystem.consumerFlow<RideState>().collect { rideState = it }
            }
            launch {
                compareEverySecond()
            }
        }
    }

    override fun onDestroy() {
        serviceJob?.cancel()
        serviceJob = null
        karooSystem.dispatch(ReleaseBluetooth(extension))
        karooSystem.disconnect()
        super.onDestroy()
    }

    override fun startScan(emitter: Emitter<Device>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            bleManager.scan(listOf(BleManager.CYCLING_POWER_SERVICE_UUID)).collect { (address, name) ->
                Timber.i("Found power meter $address: $name")
                val meter = meters.getOrPut(address) { createMeter(address, name) }
                emitter.onNext(meter.device)
            }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    override fun connectDevice(
        uid: String,
        emitter: Emitter<DeviceEvent>,
    ) {
        val address = uid.removePrefix("${PowerMeterB.PREFIX}-")
        meters.getOrPut(address) { createMeter(address, null) }.connect(emitter)
    }

    override fun startFit(emitter: Emitter<FitEffect>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            comparison.collect { latest ->
                when (rideState) {
                    is RideState.Recording -> latest.powerB?.let {
                        emitter.onNext(WriteToRecordMesg(FieldValue(FitFields.powerB, it)))
                    }
                    // The session message is written once at the end of the ride; the Karoo keeps the last value sent.
                    is RideState.Paused -> {
                        val values = sessionValues(latest)
                        if (values.isNotEmpty()) {
                            emitter.onNext(WriteToSessionMesg(values))
                        }
                    }
                    is RideState.Idle -> {}
                }
            }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    private suspend fun compareEverySecond() {
        // Runs until the service job is cancelled, which `delay` responds to.
        while (true) {
            delay(1_000)
            val state = rideState
            if (state is RideState.Idle) {
                comparator.resetRide()
            }
            comparison.value = comparator.addSample(
                powerA = latestPowerA.get(),
                powerB = latestPowerB.get(),
                recording = state is RideState.Recording,
            )
        }
    }

    private fun sessionValues(latest: Comparison): List<FieldValue> {
        val values = mutableListOf<FieldValue>()
        latest.rideDiffPercent?.let { values.add(FieldValue(FitFields.rideDiffPercent, it)) }
        latest.rideAveragePowerB?.let { values.add(FieldValue(FitFields.rideAveragePowerB, it)) }
        return values
    }

    private fun createMeter(
        address: String,
        name: String?,
    ): PowerMeterB {
        return PowerMeterB(bleManager, latestPowerB, extension, address, name)
    }
}
