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

    var charucoVerticalSquares: Int
        get() = sharedPreferences.getInt(
            "charucoVerticalSquares",
            7
        )
        set(value) {
            sharedPreferences.edit().putInt("charucoVerticalSquares", value).apply()
        }


    var charucoHorizontalSquares: Int
        get() = sharedPreferences.getInt(
            "charucoHorizontalSquares",
            5
        )
        set(value) {
            sharedPreferences.edit().putInt("charucoHorizontalSquares", value).apply()
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