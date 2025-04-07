package com.bertoferrero.fingerprintcaptureapp.components.permissions

import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.bluetooth.BLUETOOTH_SCAN

class BluetoothScanPermissionsViewModel(controller: PermissionsController) : MainPermissionsViewModel(
    controller
) {

    override fun getPermissionType(): Permission{
        return Permission.BLUETOOTH_SCAN
    }

}