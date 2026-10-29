package io.github.alexgaignard.powercompare.ble

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.client.android.native
import no.nordicsemi.kotlin.ble.client.distinctByPeripheral
import no.nordicsemi.kotlin.ble.core.ConnectionState
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Thin wrapper over Nordic's BLE client, adapted from Hammerhead's karoo-ext sample (Apache 2.0).
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)
class BleManager(context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val centralManager by lazy {
        CentralManager.Factory.native(context, scope)
    }

    private val connectionOptions by lazy {
        CentralManager.ConnectionOptions.Direct(
            timeout = 120.seconds,
            retry = 10,
            retryDelay = 12.seconds,
        )
    }

    /** Emits the address and name of each peripheral advertising one of [services], once per peripheral. */
    fun scan(services: List<Uuid>): Flow<Pair<String, String?>> {
        return centralManager
            .scan {
                Any {
                    services.map { ServiceUuid(it) }
                }
            }
            .distinctByPeripheral()
            .map { Pair(it.peripheral.address, it.peripheral.name) }
            .catch { Timber.w(it, "Error in BLE scan") }
    }

    /**
     * Connects and keeps reconnecting to [address].
     * Emits the peripheral on every connection and null on every disconnection.
     */
    fun connect(address: String): Flow<Peripheral?> {
        return flow {
            val peripheral = centralManager
                .scan { Address(address) }
                .filter { it.isConnectable }
                .map { it.peripheral }
                .first()
            try {
                repeat(10) { attempt ->
                    if (attempt > 0) {
                        delay(3.seconds)
                    }
                    Timber.i("Connecting to $peripheral (attempt ${attempt + 1})")
                    centralManager.connect(peripheral, connectionOptions)
                    emit(peripheral)
                    peripheral.state
                        .filter { it is ConnectionState.Disconnected }
                        .first()
                    Timber.i("Disconnected from $peripheral")
                    emit(null)
                }
            } finally {
                peripheral.disconnect()
            }
        }
    }

    fun <T> observeCharacteristic(
        peripheral: Peripheral,
        service: Uuid,
        characteristic: Uuid,
        parser: (ByteArray) -> T,
    ): Flow<T> {
        return peripheral.services(listOf(service))
            .flatMapLatest { services ->
                val found = services
                    .firstOrNull()
                    ?.characteristics
                    ?.firstOrNull { it.uuid == characteristic }
                if (found == null) {
                    Timber.w("Characteristic $service:$characteristic not found")
                    emptyFlow()
                } else {
                    found.subscribe().map(parser)
                }
            }
            .catch { Timber.w("Characteristic $service:$characteristic error: ${it.message}") }
    }

    suspend fun <T> readCharacteristic(
        peripheral: Peripheral,
        service: Uuid,
        characteristic: Uuid,
        parser: (ByteArray) -> T,
    ): T? {
        val foundService = peripheral.services(listOf(service))
            .mapNotNull { it.firstOrNull() }
            .firstOrNull()
            ?: return null
        val found = foundService.characteristics.firstOrNull { it.uuid == characteristic }
            ?: return null
        return parser(found.read())
    }

    companion object {
        val CYCLING_POWER_SERVICE_UUID = Uuid.parse("00001818-0000-1000-8000-00805f9b34fb")
        val CYCLING_POWER_MEASUREMENT_UUID = Uuid.parse("00002A63-0000-1000-8000-00805f9b34fb")

        val BATTERY_SERVICE_UUID = Uuid.parse("0000180F-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_UUID = Uuid.parse("00002A19-0000-1000-8000-00805f9b34fb")

        val DEVICE_INFO_SERVICE_UUID = Uuid.parse("0000180A-0000-1000-8000-00805f9b34fb")
        val MANUFACTURER_NAME_UUID = Uuid.parse("00002A29-0000-1000-8000-00805f9b34fb")
        val MODEL_NUMBER_UUID = Uuid.parse("00002A24-0000-1000-8000-00805f9b34fb")
        val SERIAL_NUMBER_UUID = Uuid.parse("00002A25-0000-1000-8000-00805f9b34fb")
    }
}
