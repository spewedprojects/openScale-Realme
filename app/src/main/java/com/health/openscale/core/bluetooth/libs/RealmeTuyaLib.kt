/*
 * openScale
 * Copyright (C) 2025 olie.xdev <olie.xdeveloper@googlemail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.health.openscale.core.bluetooth.libs

import com.health.openscale.core.utils.LogManager
import com.tuya.smart.tyncnnlibrary.NCNNApi

data class RealmeTuyaResults(
    val fat: Float = 0.0f,
    val water: Float = 0.0f,
    val muscle: Float = 0.0f,
    val protein: Float = 0.0f,
    val bone: Float = 0.0f,
    val bmr: Float = 0.0f,
    val bodyAge: Float = 0.0f,
    val visceralFat: Float = 0.0f,
    val bodyScore: Float = 0.0f
)

class RealmeTuyaLib(
    private val sex: Int,      // 1 = Male, 2 = Female
    private val height: Float, // in cm
    private val age: Int       // in years
) {
    companion object {
        private const val TAG = "RealmeTuyaLib"
    }

    fun calculate(weight: Float, impedance: Float): RealmeTuyaResults {
        if (weight <= 0.0f || impedance <= 0.0f) {
            return RealmeTuyaResults()
        }

        // 1. Calculate BMI
        val heightM = height / 100.0f
        val bmi = weight / (heightM * heightM)
        val bmiStr = String.format("%.1f", bmi)

        // 2. Prepare NCNN Input: [sex, height, weight, bmi, impedance, age]
        val inputs = floatArrayOf(
            sex.toFloat(),
            height,
            weight,
            bmi,
            impedance,
            age.toFloat()
        )

        // 3. Call JNI natively
        LogManager.d(TAG, "Calling NCNNApi.forward with inputs: [sex=$sex, height=$height, weight=$weight, bmi=$bmi, impedance=$impedance, age=$age]")
        val outputs = try {
            NCNNApi.init()
            val res = NCNNApi.forward(inputs)
            NCNNApi.destroy()
            LogManager.d(TAG, "NCNNApi.forward returned outputs: ${res.contentToString()}")
            res
        } catch (e: Throwable) {
            LogManager.e(TAG, "Failed to execute NCNNApi: ${e.message}", e)
            null
        }

        if (outputs == null || outputs.size < 7) {
            LogManager.e(TAG, "NCNNApi output is empty or invalid size.")
            return RealmeTuyaResults()
        }

        val fatPct = outputs[0]
        val waterPct = outputs[1]
        val muscleMass = outputs[2]
        val proteinPct = outputs[3]
        val boneMass = outputs[4]
        val bmrValue = outputs[5]
        val bodyAgeValue = outputs[6]

        // 4. Calculate Visceral Fat using Tuya Formula
        var visceralFatVal = 0.0f
        if (age >= 18 && impedance <= 5000.0f) {
            visceralFatVal = if (sex == 1) { // Male
                (0.55f * bmi) + (0.007f * impedance) + (0.15f * age) - 15.0f
            } else { // Female
                (0.43f * bmi) + (0.0055f * impedance) + (0.05f * age) - 11.0f
            }
            if (visceralFatVal < 1.0f) visceralFatVal = 1.0f
            if (visceralFatVal > 30.0f) visceralFatVal = 30.0f
        }

        // 5. Calculate Body Score using Tuya Formula
        var bodyScoreVal = 0.0f
        try {
            val fatTerm = Math.abs(fatPct - 22.0f) * 2.0f
            val bmiTerm = Math.abs(bmi - 10.0f) * 0.5f
            bodyScoreVal = 100.0f - fatTerm - bmiTerm
            if (bodyScoreVal < 50.0f) bodyScoreVal = 50.0f
        } catch (e: Exception) {
            LogManager.e(TAG, "Error calculating body score: ${e.message}")
        }

        return RealmeTuyaResults(
            fat = fatPct,
            water = waterPct,
            muscle = muscleMass,
            protein = proteinPct,
            bone = boneMass,
            bmr = bmrValue,
            bodyAge = bodyAgeValue,
            visceralFat = visceralFatVal,
            bodyScore = bodyScoreVal
        )
    }
}
