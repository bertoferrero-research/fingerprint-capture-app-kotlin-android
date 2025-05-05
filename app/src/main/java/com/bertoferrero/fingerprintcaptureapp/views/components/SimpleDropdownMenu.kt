package com.bertoferrero.fingerprintcaptureapp.views.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SimpleDropdownMenu(
    label: String,
    options: Array<String>,
    values: Array<T>,
    onOptionSelected: (T) -> Unit,
    selectedValue: T? = null,
    modifier: Modifier = Modifier
) {
    val selectedIndex = remember { mutableIntStateOf(-1) }
    if (selectedIndex.intValue == -1) {
        if(selectedValue != null && values.contains(selectedValue)) {
            selectedIndex.intValue = values.indexOf(selectedValue)
        }
        else{
            selectedIndex.intValue = 0
        }
    }

    val (expanded, setExpanded) = remember { mutableStateOf(false) }


    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { setExpanded(!expanded) },
        modifier = modifier
    ) {
        TextField(
            value = options[selectedIndex.intValue],
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            modifier = modifier.fillMaxWidth(),
            onDismissRequest = { setExpanded(false) }
        ) {
            options.forEachIndexed  { optionIndex, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(values[optionIndex])
                        selectedIndex.intValue = optionIndex
                        setExpanded(false)
                    }
                )
            }
        }
    }
}



