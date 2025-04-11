package com.bertoferrero.fingerprintcaptureapp.models

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages the shared preferences for the application.
 * This class is employed for avoiding sharing the context between the different classes for loading the preferences.
 */
object SharedPreferencesManager {
    private lateinit var cameraCalibration: SharedPreferences
    private lateinit var settingsParameters: SharedPreferences

    fun init(context: Context) {
        this.cameraCalibration = context.getSharedPreferences(
            "CalibrationData",
            Context.MODE_PRIVATE
        )
        this.settingsParameters = context.getSharedPreferences(
            "ViewParameters",
            Context.MODE_PRIVATE
        )
    }

    // General
    fun getCameraCalibrationSharedPreferences(): SharedPreferences {
        return cameraCalibration
    }
    fun getSettingsParametersSharedPreferences(): SharedPreferences {
        return settingsParameters
    }
}