package com.bertoferrero.fingerprintcaptureapp.components.permissions

import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.bluetooth.BLUETOOTH_SCAN
import dev.icerock.moko.permissions.location.COARSE_LOCATION

class CoarseLocationPermissionsViewModel(controller: PermissionsController) : MainPermissionsViewModel(
    controller
) {

    override fun getPermissionType(): Permission{
        return Permission.COARSE_LOCATION
    }

}