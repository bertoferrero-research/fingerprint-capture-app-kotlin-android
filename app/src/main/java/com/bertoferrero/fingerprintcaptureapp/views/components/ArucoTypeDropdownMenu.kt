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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue

enum class ArucoDictionaryType(val value: Int) {
    DICT_4X4_50(org.opencv.objdetect.Objdetect.DICT_4X4_50),
    DICT_4X4_100(org.opencv.objdetect.Objdetect.DICT_4X4_100),
    DICT_4X4_250(org.opencv.objdetect.Objdetect.DICT_4X4_250),
    DICT_4X4_1000(org.opencv.objdetect.Objdetect.DICT_4X4_1000),
    DICT_5X5_50(org.opencv.objdetect.Objdetect.DICT_5X5_50),
    DICT_5X5_100(org.opencv.objdetect.Objdetect.DICT_5X5_100),
    DICT_5X5_250(org.opencv.objdetect.Objdetect.DICT_5X5_250),
    DICT_5X5_1000(org.opencv.objdetect.Objdetect.DICT_5X5_1000),
    DICT_6X6_50(org.opencv.objdetect.Objdetect.DICT_6X6_50),
    DICT_6X6_100(org.opencv.objdetect.Objdetect.DICT_6X6_100),
    DICT_6X6_250(org.opencv.objdetect.Objdetect.DICT_6X6_250),
    DICT_6X6_1000(org.opencv.objdetect.Objdetect.DICT_6X6_1000),
    DICT_7X7_50(org.opencv.objdetect.Objdetect.DICT_7X7_50),
    DICT_7X7_100(org.opencv.objdetect.Objdetect.DICT_7X7_100),
    DICT_7X7_250(org.opencv.objdetect.Objdetect.DICT_7X7_250),
    DICT_7X7_1000(org.opencv.objdetect.Objdetect.DICT_7X7_1000),
    DICT_ARUCO_ORIGINAL(org.opencv.objdetect.Objdetect.DICT_ARUCO_ORIGINAL);

    companion object {
        private val map = entries.associateBy(ArucoDictionaryType::value)
        fun fromInt(type: Int) = map[type]
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArucoTypeDropdownMenu(
    selectedArucoType: ArucoDictionaryType,
    onArucoTypeSelected: (ArucoDictionaryType) -> Unit
) {
    val arucoTypes = ArucoDictionaryType.entries.toTypedArray()
    val (expanded, setExpanded) = remember { mutableStateOf(false) }
    val (selectedText, setSelectedText) = remember { mutableStateOf(selectedArucoType.name) }


    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { setExpanded(!expanded) }
    ) {
        TextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text("Aruco Dictionary Type") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { setExpanded(false) }
        ) {
            arucoTypes.forEach { arucoType ->
                DropdownMenuItem(
                    text = { Text(arucoType.name) },
                    onClick = {
                        onArucoTypeSelected(arucoType)
                        setSelectedText(arucoType.name)
                        setExpanded(false)
                    }
                )
            }
        }
    }
}



