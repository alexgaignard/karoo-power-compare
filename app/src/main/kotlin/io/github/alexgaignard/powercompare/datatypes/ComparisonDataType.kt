package io.github.alexgaignard.powercompare.datatypes

import io.github.alexgaignard.powercompare.compare.Comparison
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * A numeric field showing one value picked out of the latest [Comparison].
 */
class ComparisonDataType(
    extension: String,
    typeId: String,
    private val comparison: StateFlow<Comparison>,
    private val selectValue: (Comparison) -> Double?,
) : DataTypeImpl(extension, typeId) {
    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            comparison.collect { latest ->
                emitter.onNext(toStreamState(selectValue(latest)))
            }
        }
        emitter.setCancellable {
            job.cancel()
        }
    }

    private fun toStreamState(value: Double?): StreamState {
        if (value == null) {
            return StreamState.NotAvailable
        }
        return StreamState.Streaming(
            DataPoint(
                dataTypeId,
                values = mapOf(DataType.Field.SINGLE to value),
            ),
        )
    }
}
