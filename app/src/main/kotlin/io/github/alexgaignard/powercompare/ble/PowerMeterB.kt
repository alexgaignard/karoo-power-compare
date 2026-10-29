package io.github.alexgaignard.powercompare.ble

import android.annotation.SuppressLint
import io.github.alexgaignard.powercompare.compare.LatestValue
import io.github.alexgaignard.powercompare.datatypes.DataTypeIds
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.BatteryStatus
import io.hammerhead.karooext.models.ConnectionStatus
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import io.hammerhead.karooext.models.ManufacturerInfo
import io.hammerhead.karooext.models.OnBatteryStatus
import io.hammerhead.karooext.models.OnConnectionStatus
import io.hammerhead.karooext.models.OnDataPoint
import io.hammerhead.karooext.models.OnManufacturerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import kotlin.uuid.ExperimentalUuidApi

/**
 * The second power meter, read over BLE by this extension.
 *
 * It reports into the extension's own "Power B" data type rather than the standard power source,
 * so the Karoo keeps using meter A as its power meter.
 */
class PowerMeterB(
    private val bleManager: BleManager,
    private val latestPowerB: LatestValue,
    extension: String,
    private val address: String,
    name: String?,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val device = Device(
        extension,
        "$PREFIX-$address",
        listOf(DataType.dataTypeId(extension, DataTypeIds.POWER_B)),
        "Power B ${name ?: address}",
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun connect(emitter: Emitter<DeviceEvent>) {
        val job = scope.launch {
            bleManager.connect(address)
                .flatMapLatest { peripheral ->
                    if (peripheral == null) {
                        flowOf(OnConnectionStatus(ConnectionStatus.SEARCHING))
                    } else {
                        merge(
                            flowOf(OnConnectionStatus(ConnectionStatus.CONNECTED)),
                            powerEvents(peripheral),
                            batteryEvents(peripheral),
                            manufacturerInfo(peripheral),
                        )
                    }
                }
                .collect { emitter.onNext(it) }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun powerEvents(peripheral: Peripheral): Flow<DeviceEvent> {
        return bleManager
            .observeCharacteristic(
                peripheral,
                BleManager.CYCLING_POWER_SERVICE_UUID,
                BleManager.CYCLING_POWER_MEASUREMENT_UUID,
                CyclingPowerParser::instantaneousPower,
            )
            .mapNotNull { it?.toDouble() }
            .map { watts ->
                latestPowerB.set(watts)
                OnDataPoint(
                    DataPoint(
                        device.dataTypes.first(),
                        values = mapOf(DataType.Field.SINGLE to watts),
                        sourceId = device.uid,
                    ),
                )
            }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun batteryEvents(peripheral: Peripheral): Flow<DeviceEvent> {
        return bleManager
            .observeCharacteristic(
                peripheral,
                BleManager.BATTERY_SERVICE_UUID,
                BleManager.BATTERY_LEVEL_UUID,
            ) { data -> if (data.isNotEmpty()) data[0].toInt() and 0xFF else -1 }
            .map { percent -> OnBatteryStatus(BatteryStatus.fromPercentage(percent)) }
    }

    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalUuidApi::class)
    private fun manufacturerInfo(peripheral: Peripheral): Flow<DeviceEvent> {
        return flow {
            suspend fun readText(characteristic: kotlin.uuid.Uuid): String? {
                return bleManager.readCharacteristic(
                    peripheral,
                    BleManager.DEVICE_INFO_SERVICE_UUID,
                    characteristic,
                ) { it.decodeToString() }
            }
            val info = ManufacturerInfo(
                readText(BleManager.MANUFACTURER_NAME_UUID),
                readText(BleManager.SERIAL_NUMBER_UUID),
                readText(BleManager.MODEL_NUMBER_UUID),
            )
            emit(OnManufacturerInfo(info))
        }
    }

    companion object {
        const val PREFIX = "power-b"
    }
}
