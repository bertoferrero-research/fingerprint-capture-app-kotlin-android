package com.bertoferrero.fingerprintcaptureapp.views.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Modifier

@Composable
fun <T : Number>NumberField(
    value: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable() (() -> Unit)? = null,
    enabled: Boolean = true
) {

    val text = remember { mutableStateOf(value.toString())}

    val change : (String) -> Unit = { it ->
        if (it.isEmpty()) {
            text.value = it
        }
        else {
            val newValue = when (value) {
                is Double -> it.toDoubleOrNull()
                is Float -> it.toFloatOrNull()
                is Int -> it.toIntOrNull()
                else -> null
            }
            if (newValue != null) {
                onValueChange(newValue as T)
                text.value = it
            }
        }
    }

    TextField(
        value = text.value,
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        onValueChange = change,
        label = label,
        modifier = modifier,
        enabled = enabled
    )

}