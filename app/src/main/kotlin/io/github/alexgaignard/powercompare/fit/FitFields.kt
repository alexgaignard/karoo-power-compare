package io.github.alexgaignard.powercompare.fit

import io.hammerhead.karooext.models.DeveloperField

/** Developer fields this extension adds to the ride's FIT file. */
object FitFields {
    // FIT base type ids from the FIT SDK profile.
    private const val UINT16: Short = 132
    private const val FLOAT32: Short = 136

    val powerB = DeveloperField(
        fieldDefinitionNumber = 0,
        fitBaseTypeId = UINT16,
        fieldName = "power_b",
        units = "watts",
    )

    val rideDiffPercent = DeveloperField(
        fieldDefinitionNumber = 1,
        fitBaseTypeId = FLOAT32,
        fieldName = "power_b_vs_a",
        units = "%",
    )

    val rideAveragePowerB = DeveloperField(
        fieldDefinitionNumber = 2,
        fitBaseTypeId = UINT16,
        fieldName = "avg_power_b",
        units = "watts",
    )
}
