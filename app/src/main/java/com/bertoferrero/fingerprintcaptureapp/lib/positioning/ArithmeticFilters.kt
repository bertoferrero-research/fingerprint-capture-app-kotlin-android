package com.bertoferrero.fingerprintcaptureapp.lib.positioning


/**
 * Calculate the average (arithmetic or weighted) of the positions based on their distances.
 * @param positions List of positions, each position is a list of [x, y, z, distance]
 * @param weightedAverage Indicates whether the average must be calculated weighted or arithmetic
 * @return Average position as a list of [x, y, z] or null if no valid positions are provided.
 */
fun calculateAveragePosition(
    positions: List<PositionFromMarker>,
    weightedAverage: Boolean = true,
): Position? {

    if (positions.isEmpty()) return null

    var x = 0.0
    var y = 0.0
    var z = 0.0
    var totalWeight = 0.0

    for (position in positions) {
        // position[0] = x, [1] = y, [2] = z, [3] = distancia

        val distance = if (weightedAverage) position.distance ?: continue else 1.0

        if (distance <= 0.0) continue // evita división por 0 o distancias inválidas

        val weight = 1.0 / (distance * distance)

        x += position.x * weight
        y += position.y * weight
        z += position.z * weight
        totalWeight += weight
    }

    // Si no se acumuló peso (ninguna posición válida), evita NaN
    if (totalWeight == 0.0) return Position()

    return Position(
        x = x / totalWeight,
        y = y / totalWeight,
        z = z / totalWeight
    )
}

/**
 * Calculates the weighted or simple median of positions in the X, Y, and Z axes.
 *
 * @param positions List of positions, where each position is a list of [x, y, z, distance].
 * @param useWeighted Indicates whether to use the distance as a weight for calculating the weighted median.
 * @return A list with the medians of the axes [medianX, medianY, medianZ], or [0.0, 0.0, 0.0] if no valid data is provided.
 */
fun calculateMedianPosition(
    positions: List<PositionFromMarker>,
    useWeighted: Boolean = true,
): Position? {

    if (positions.isEmpty()) return null

    // Lists to store pairs of values and weights for each axis
    val xList = mutableListOf<Pair<Double, Double>>() // Pair(value, weight)
    val yList = mutableListOf<Pair<Double, Double>>()
    val zList = mutableListOf<Pair<Double, Double>>()

    for (position in positions) {
        // Calculate the weight based on the distance, or use uniform weight if not weighted
        val distance = if (useWeighted) position.distance ?: continue else 1.0
        if (distance <= 0.0) continue // Avoid invalid distances or division by 0

        val weight = 1.0 / (distance * distance)
        xList.add(position.x to weight)
        yList.add(position.y to weight)
        zList.add(position.z to weight)
    }

    // If no valid data, return a default position
    if (xList.isEmpty()) return Position()

    // Calculate the weighted median for each axis
    return Position(
        weightedMedianPosition(xList),
        weightedMedianPosition(yList),
        weightedMedianPosition(zList)
    )
}

/**
 * Calculates the weighted median of a list of pairs (value, weight).
 *
 * @param data A list of pairs where the first element is the value and the second is the weight.
 * @return The value corresponding to the weighted median.
 *
 * The method works as follows:
 * 1. Sorts the list of pairs by the value (first element of the pair).
 * 2. Computes the total weight by summing up all the weights (second element of the pair).
 * 3. Iterates through the sorted list, accumulating the weights.
 * 4. Returns the value when the cumulative weight reaches or exceeds half of the total weight.
 * 5. If no value is found during the iteration (unlikely), it returns the last value in the sorted list as a fallback.
 */
fun weightedMedianPosition(data: List<Pair<Double, Double>>): Double {
    val sorted = data.sortedBy { it.first }
    val totalWeight = sorted.sumOf { it.second }
    var cumulativeWeight = 0.0

    for ((value, weight) in sorted) {
        cumulativeWeight += weight
        if (cumulativeWeight >= totalWeight / 2) {
            return value
        }
    }
    return sorted.last().first // fallback, shouldn't happen
}