package com.bertoferrero.fingerprintcaptureapp.lib

/**
 * Separador de columnas usado en todos los CSV que exporta la app. Se usa ";" en vez de ","
 * porque los valores decimales se escriben con coma (convención europea/Excel-ES) y no se
 * pueden usar ambos como coma a la vez sin ambigüedad.
 */
const val CSV_FIELD_SEPARATOR = ";"

/**
 * Formatea un Double con coma como separador decimal, para exportaciones CSV que usan
 * [CSV_FIELD_SEPARATOR] como separador de columnas.
 */
fun Double.toCsvDecimal(): String = toString().replace('.', ',')

/**
 * Formatea un Float con coma como separador decimal, para exportaciones CSV que usan
 * [CSV_FIELD_SEPARATOR] como separador de columnas.
 */
fun Float.toCsvDecimal(): String = toString().replace('.', ',')
