package com.bertoferrero.fingerprintcaptureapp.views.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bertoferrero.fingerprintcaptureapp.components.permissions.MainPermissionsViewModel
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.compose.BindEffect
import dev.icerock.moko.permissions.compose.PermissionsControllerFactory
import dev.icerock.moko.permissions.compose.rememberPermissionsControllerFactory

/**
 * Resolve a permission
 * @param permission Permission to resolve
 * @return True if the permission is granted, false otherwise
 */
@Composable
fun resolvePermission(permission: Permission): Boolean {
    val factory: PermissionsControllerFactory = rememberPermissionsControllerFactory()
    val controller: PermissionsController =
        remember(factory) { factory.createPermissionsController() }
    BindEffect(controller)

    val permissionsViewModel = viewModel(key = "permission_$permission") {
        MainPermissionsViewModel(controller, permission)
    }

    when (permissionsViewModel.state) {
        PermissionState.Granted -> {
            return true
        }
        /*PermissionState.Denied -> {
            cameraPermissionGranted = false
        }
        PermissionState.DeniedAlways -> {
            cameraPermissionGranted = false
        }*/
        else -> {
            permissionsViewModel.provideOrRequestPermission()
        }
    }

    return false
}

/**
 * Resolve a list of permissions
 * @param permissions List of permissions to resolve
 * @return True if all permissions are granted, false otherwise
 */
@Composable
fun resolvePermissions(permissions: List<Permission>): Boolean {
    var resolution = true
    for (permission in permissions) {
        resolution = resolution && resolvePermission(permission)
    }
    return resolution
}