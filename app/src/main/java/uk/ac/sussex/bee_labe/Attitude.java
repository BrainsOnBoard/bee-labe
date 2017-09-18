package uk.ac.sussex.bee_labe;

/**
 * Created by alex on 18/09/17.
 */

public class Attitude {
    public float yaw, pitch, roll;

    public Attitude(float yaw, float pitch, float roll) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
    }

    @Override
    public String toString() {
        return String.format("Yaw: %.2f°\nPitch: %.2f°\nRoll: %.2f°",
                Math.toDegrees(yaw), Math.toDegrees(pitch), Math.toDegrees(roll));
    }
}
