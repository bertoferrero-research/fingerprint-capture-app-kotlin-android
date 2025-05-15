package com.bertoferrero.fingerprintcaptureapp.lib.positioning

import kotlin.math.sqrt

/**
 * Represents a 3D position with optional distance information.
 *
 * @property x The X-coordinate of the position. Defaults to 0.0.
 * @property y The Y-coordinate of the position. Defaults to 0.0.
 * @property z The Z-coordinate of the position. Defaults to 0.0.
 */
open class Position(
    var x: Double = 0.0,
    var y: Double = 0.0,
    var z: Double = 0.0
) {
    companion object{
        /**
         * Calculates the Euclidean distance between two positions in 3D space.
         *
         * @param a The first position.
         * @param b The second position.
         * @return The Euclidean distance between the two positions.
         */
        fun euclideanDistance(a: Position, b: Position): Double {
            val dx = a.x - b.x
            val dy = a.y - b.y
            val dz = a.z - b.z
            return sqrt(dx * dx + dy * dy + dz * dz)
        }
    }
}

/**
 * Represents a 3D position associated with a marker, including its ID and distance.
 *
 * @property markerId The ID of the marker associated with this position. Defaults to 0.
 * @property distance The optional distance from the marker to the position. Defaults to null.
 * @property x The X-coordinate of the position. Defaults to 0.0.
 * @property y The Y-coordinate of the position. Defaults to 0.0.
 * @property z The Z-coordinate of the position. Defaults to 0.0.
 */
class PositionFromMarker(
    var markerId: Int = 0,
    var distance: Double? = null,
    x: Double = 0.0,
    y: Double = 0.0,
    z: Double = 0.0
) : Position(x, y, z) {
    override fun toString(): String {
        return "PositionFromMarker(markerId=$markerId, x=$x, y=$y, z=$z, distance=$distance)"
    }
}