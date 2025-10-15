package com.bertoferrero.fingerprintcaptureapp.lib.positioning

import kotlin.math.sqrt

/**
 * Filters a list of positions using the RANSAC algorithm to remove outliers.
 *
 * @param positions The list of positions to filter.
 * @param threshold The maximum distance between points to consider them as inliers. Defaults to 0.2.
 * @return A filtered list of positions containing only the inliers. Returns an empty list if no sufficient inliers are found.
 */
fun ransacFilterPositions(positions: List<PositionFromMarker>, threshold: Double = 0.2): List<PositionFromMarker> {
    if(threshold <= 0){
        return positions
    }

    // If the list has 1 or fewer positions, return an empty list. We require at least 2 points to confirm the position
    if (positions.size <= 1) {
        return listOf()
        //return positions // If the list has 1 or fewer positions, return it as is (no filtering needed)
    }

    // Calculate the minimum number of inliers required for a valid result
    val minInliers = if (positions.size <= 4) 2 else (positions.size * 0.5).toInt()

    var bestInliers = listOf<PositionFromMarker>()

    // Iterate through each position as a potential sample
    for (sample in positions) {
        // Filter positions that are within the threshold distance from the sample
        val inliers = positions.filter {
            val dist = Position.euclideanDistance(it, sample)
            dist < threshold
        }
        // Update the best inliers if the current set has more inliers
        if (inliers.size > bestInliers.size) {
            bestInliers = inliers
        }
    }

    // If the best inliers set does not meet the minimum required, return an empty list
    if (bestInliers.size < minInliers) {
        return listOf()
    }

    // Return the filtered list of inliers
    return bestInliers
}

