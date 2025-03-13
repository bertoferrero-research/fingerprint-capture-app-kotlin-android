package com.bertoferrero.fingerprintcaptureapp.models

import android.content.Context

/**
 * Manages the view parameters
 */
class ViewParametersManager(private val context: Context) {

    private val sharedPreferences =
        context.getSharedPreferences("ViewParameters", Context.MODE_PRIVATE)

    // General

    var arucoDictionaryType: Int
        get() = sharedPreferences.getInt(
            "arucoDictionaryType",
            org.opencv.objdetect.Objdetect.DICT_6X6_250
        )
        set(value) {
            sharedPreferences.edit().putInt("arucoDictionaryType", value).apply()
        }

    // Detecting

    var markerSize: Float
        get() = sharedPreferences.getFloat(
            "markerSize",
            0.173f
        )
        set(value) {
            sharedPreferences.edit().putFloat("markerSize", value).apply()
        }


    // Calibrating

    var calibrationSamples: Int
        get() = sharedPreferences.getInt(
            "calibrationSamples",
            15
        )
        set(value) {
            sharedPreferences.edit().putInt("calibrationSamples", value).apply()
        }

    var charucoXSquares: Int
        get() = sharedPreferences.getInt(
            "charucoXSquares",
            7
        )
        set(value) {
            sharedPreferences.edit().putInt("charucoXSquares", value).apply()
        }


    var charucoYSquares: Int
        get() = sharedPreferences.getInt(
            "charucoYSquares",
            5
        )
        set(value) {
            sharedPreferences.edit().putInt("charucoYSquares", value).apply()
        }

    var charucoSquareLength: Float
        get() = sharedPreferences.getFloat(
            "charucoSquareLength",
            0.035f
        )
        set(value) {
            sharedPreferences.edit().putFloat("charucoSquareLength", value).apply()
        }

    var charucoMarkerLength: Float
        get() = sharedPreferences.getFloat(
            "charucoMarkerLength",
            0.018f
        )
        set(value) {
            sharedPreferences.edit().putFloat("charucoMarkerLength", value).apply()
        }

}