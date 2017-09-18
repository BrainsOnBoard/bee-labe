package uk.ac.sussex.bee_labe;

import java.util.ArrayList;

/**
 * Created by alex on 18/09/17.
 */

public class CalibrationHandler {
    private static final long CALIBRATION_DURATION = 3000; // ms

    private ArrayList<Attitude> calData = new ArrayList();
    private long startTime;
    private MainActivity main;
    public float pitch, roll;
    public boolean isCalibrating = false;

    public CalibrationHandler(MainActivity main) {
        this.main = main;
        pitch = 0;
        roll = 0;
    }

    public void start() {
        startTime = System.currentTimeMillis();
        isCalibrating = true;
        pitch = 0;
        roll = 0;
    }

    public Attitude getAttitude(float[] orient) {
        float yaw = orient[0];
        if (yaw < 0) {
            yaw += 2 * Math.PI;
        }
        float pitch = -orient[1] - this.pitch;
        float roll = orient[2] - this.roll;

        Attitude att = new Attitude(yaw, pitch, roll);

        if (isCalibrating) {
            if ((System.currentTimeMillis() - startTime) >= CALIBRATION_DURATION) {
                calculateOffsets();
                isCalibrating = false;

                main.stopCalibration();

                att.pitch -= pitch;
                att.roll -= roll;
            } else {
                calData.add(att);
            }
        }

        return att;
    }

    private void calculateOffsets() {
        pitch = roll = 0;

        for (int i = 0; i < calData.size(); i++) {
            Attitude dat = calData.get(i);

            pitch += dat.pitch;
            roll += dat.roll;
        }
        pitch /= calData.size();
        roll /= calData.size();

        calData.clear();
    }
}