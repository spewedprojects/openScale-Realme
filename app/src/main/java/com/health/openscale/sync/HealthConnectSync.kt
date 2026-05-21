package com.health.openscale.sync

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import com.health.openscale.core.data.MeasurementTypeKey
import com.health.openscale.core.model.MeasurementWithValues
import java.time.Instant
import java.time.ZoneId
import com.health.openscale.core.utils.LogManager

class HealthConnectSync(private val healthConnectClient: HealthConnectClient) {

    suspend fun syncAll(measurements: List<MeasurementWithValues>): Result<Unit> {
        val records = mutableListOf<Record>()

        measurements.forEach { measurement ->
            val weightRecord = buildWeightRecord(measurement)
            if (weightRecord != null) records.add(weightRecord)

            val waterRecord = buildWaterRecord(measurement)
            if (waterRecord != null) records.add(waterRecord)

            val fatRecord = buildFatRecord(measurement)
            if (fatRecord != null) records.add(fatRecord)
        }

        return try {
            if (records.isNotEmpty()) {
                healthConnectClient.insertRecords(records)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            LogManager.e("HealthConnectSync", "Failed to sync to Health Connect. Error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun buildMetadata(measurement: MeasurementWithValues, type: String): Metadata {
        return Metadata.manualEntry(
            clientRecordId = measurement.measurement.id.toString() + "_" + type,
            clientRecordVersion = Instant.now().toEpochMilli()
        )
    }

    private fun getFloatValue(measurement: MeasurementWithValues, key: MeasurementTypeKey): Float? {
        return measurement.values.find { it.type.key == key }?.value?.floatValue
    }

    private fun buildWeightRecord(measurement: MeasurementWithValues): WeightRecord? {
        val weight = getFloatValue(measurement, MeasurementTypeKey.WEIGHT) ?: return null
        
        val measurementInstant = Instant.ofEpochMilli(measurement.measurement.timestamp)
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(measurementInstant)

        return WeightRecord(
            time = measurementInstant,
            zoneOffset = zoneOffset,
            weight = Mass.kilograms(weight.toDouble()),
            metadata = buildMetadata(measurement, "weight")
        )
    }

    private fun buildWaterRecord(measurement: MeasurementWithValues): BodyWaterMassRecord? {
        val weight = getFloatValue(measurement, MeasurementTypeKey.WEIGHT) ?: return null
        val water = getFloatValue(measurement, MeasurementTypeKey.WATER) ?: return null

        val measurementInstant = Instant.ofEpochMilli(measurement.measurement.timestamp)
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(measurementInstant)

        return BodyWaterMassRecord(
            time = measurementInstant,
            zoneOffset = zoneOffset,
            mass = Mass.kilograms(weight.toDouble() * water.toDouble() / 100.0),
            metadata = buildMetadata(measurement, "water")
        )
    }

    private fun buildFatRecord(measurement: MeasurementWithValues): BodyFatRecord? {
        val fat = getFloatValue(measurement, MeasurementTypeKey.BODY_FAT) ?: return null

        val measurementInstant = Instant.ofEpochMilli(measurement.measurement.timestamp)
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(measurementInstant)

        return BodyFatRecord(
            time = measurementInstant,
            zoneOffset = zoneOffset,
            percentage = Percentage(fat.toDouble()),
            metadata = buildMetadata(measurement, "fat")
        )
    }
}
