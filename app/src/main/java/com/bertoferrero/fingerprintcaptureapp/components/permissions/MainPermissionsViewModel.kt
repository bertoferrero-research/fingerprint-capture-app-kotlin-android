package com.bertoferrero.fingerprintcaptureapp.components.permissions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.icerock.moko.permissions.DeniedAlwaysException
import dev.icerock.moko.permissions.DeniedException
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionState
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.RequestCanceledException
import kotlinx.coroutines.launch

open class MainPermissionsViewModel(
    protected val controller: PermissionsController,
    protected val permission: Permission
): ViewModel() {

    var state by mutableStateOf(PermissionState.NotDetermined)
        protected set

    init {
        viewModelScope.launch{
            state = controller.getPermissionState(permission)
        }
    }

    fun provideOrRequestPermission(){
        viewModelScope.launch {
            try {
                controller.providePermission(permission)
                state = PermissionState.Granted
            } catch(e: DeniedAlwaysException){
                state = PermissionState.DeniedAlways
            } catch(e: DeniedException){
                state = PermissionState.Denied
            } catch(e:RequestCanceledException){
                e.printStackTrace()
            }
        }
    }

}