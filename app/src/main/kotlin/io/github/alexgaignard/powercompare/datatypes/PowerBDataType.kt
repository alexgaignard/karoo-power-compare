package io.github.alexgaignard.powercompare.datatypes

import android.content.Context
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.UpdateNumericConfig
import io.hammerhead.karooext.models.ViewConfig

/**
 * Meter B's instant power. Its values come from the [io.github.alexgaignard.powercompare.ble.PowerMeterB]
 * sensor through the Karoo, so this type only sets the formatting.
 */
class PowerBDataType(extension: String) : DataTypeImpl(extension, DataTypeIds.POWER_B) {
    override fun startView(
        context: Context,
        config: ViewConfig,
        emitter: ViewEmitter,
    ) {
        emitter.onNext(UpdateNumericConfig(formatDataTypeId = DataType.Type.POWER))
    }
}
