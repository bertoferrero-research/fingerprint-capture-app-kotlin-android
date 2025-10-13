package com.bertoferrero.fingerprintcaptureapp.models

/**
 * Class that defines a marker with an identifier, position, and rotation.
 *
 * @property id Marker identifier.
 * @property position Marker position.
 * @property size Marker size.
 * @property rotation Marker rotation.
 * @property maxDistanceAllowed Maximum allowed distance for the marker (in millimeters).
 */
class MarkerDefinition(
    public var id: Int,
    public var position: MarkerPosition,
    public var size: Float,
    public var rotation: MarkerRotation = MarkerRotation(0f, 0f, 0f),
    public var maxDistanceAllowed: Float? = null
) {

    /**
     * Legacy constructor that allows initializing the marker with x, y, z coordinates.
     *
     * @param id Marker identifier.
     * @param x x-coordinate of the position.
     * @param y y-coordinate of the position.
     * @param z z-coordinate of the position.
     */
    constructor(
        id: Int,
        x: Float,
        y: Float,
        z: Float,
        size: Float
    ) : this(id, MarkerPosition(x, y, z), size)

    /**
     * Legacy property x that gets or sets the x-coordinate of the position.
     */
    var x: Float
        get() = position.x
        set(value) {
            position.x = value
        }

    /**
     * Legacy property y that gets or sets the y-coordinate of the position.
     */
    var y: Float
        get() = position.y
        set(value) {
            position.y = value
        }

    /**
     * Legacy property z that gets or sets the z-coordinate of the position.
     */
    var z: Float
        get() = position.z
        set(value) {
            position.z = value
        }

}

/**
 * Class that defines the position of a marker in a three-dimensional space.
 *
 * @property x x-coordinate of the position.
 * @property y y-coordinate of the position.
 * @property z z-coordinate of the position.
 */
class MarkerPosition(
    public var x: Float,
    public var y: Float,
    public var z: Float
)

/**
 * Class that defines the rotation in Degrees of a marker in a three-dimensional space.
 *
 * @property roll Rotation in Degrees around the x-axis.
 * @property pitch Rotation in Degrees around the y-axis.
 * @property yaw Rotation in Degrees around the z-axis.
 */
class MarkerRotation(
    public var roll: Float, //x
    public var pitch: Float, //y
    public var yaw: Float   //z
){
    /**
     * Property x that gets or sets the rotation around the x-axis.
     */
    var x:Float
        get() = roll
        set(value) {
            roll = value
        }

    /**
     * Property y that gets or sets the rotation around the y-axis.
     */
    var y:Float
        get() = pitch
        set(value) {
            pitch = value
        }

    /**
     * Property z that gets or sets the rotation around the z-axis.
     */
    var z:Float
        get() = yaw
        set(value) {
            yaw = value
        }
}