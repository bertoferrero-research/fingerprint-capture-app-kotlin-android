package com.bertoferrero.fingerprintcaptureapp.models

import androidx.core.content.edit

/**
 * Manages the view parameters
 */
class SettingsParametersManager() {

    private val sharedPreferences = SharedPreferencesManager.getSettingsParametersSharedPreferences()

    // General

    var arucoDictionaryType: Int
        get() = sharedPreferences.getInt(
            "arucoDictionaryType",
            org.opencv.objdetect.Objdetect.DICT_6X6_250
        )
        set(value) {
            sharedPreferences.edit() { putInt("arucoDictionaryType", value) }
        }

    // Detecting

    var markerSize: Float
        get() = sharedPreferences.getFloat(
            "markerSize",
            0.173f
        )
        set(value) {
            sharedPreferences.edit() { putFloat("markerSize", value) }
        }


    // Calibrating

    var calibrationSamples: Int
        get() = sharedPreferences.getInt(
            "calibrationSamples",
            15
        )
        set(value) {
            sharedPreferences.edit() { putInt("calibrationSamples", value) }
        }

    var charucoXSquares: Int
        get() = sharedPreferences.getInt(
            "charucoXSquares",
            7
        )
        set(value) {
            sharedPreferences.edit() { putInt("charucoXSquares", value) }
        }


    var charucoYSquares: Int
        get() = sharedPreferences.getInt(
            "charucoYSquares",
            5
        )
        set(value) {
            sharedPreferences.edit() { putInt("charucoYSquares", value) }
        }

    var charucoSquareLength: Float
        get() = sharedPreferences.getFloat(
            "charucoSquareLength",
            0.035f
        )
        set(value) {
            sharedPreferences.edit() { putFloat("charucoSquareLength", value) }
        }

    var charucoMarkerLength: Float
        get() = sharedPreferences.getFloat(
            "charucoMarkerLength",
            0.018f
        )
        set(value) {
            sharedPreferences.edit() { putFloat("charucoMarkerLength", value) }
        }

}