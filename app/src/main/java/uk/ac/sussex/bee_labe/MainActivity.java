package uk.ac.sussex.bee_labe;

import android.content.Context;
import android.hardware.*;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

public class MainActivity extends AppCompatActivity implements SensorEventListener, View.OnClickListener {
    private static final int SENSOR_DELAY = 1000000; // µs

    private SensorManager mSensorManager;
    private Sensor mAccSensor, mMagSensor;
    private float[] mGravity, mGeomagnetic;
    private float pitch, roll, yaw;
    private boolean isRecording = false;
    private Button recButton;
    private TextView pitchView, rollView, yawView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recButton = (Button)findViewById(R.id.recButton);
        recButton.setOnClickListener(this);

        pitchView = (TextView)findViewById(R.id.pitchView);
        rollView = (TextView)findViewById(R.id.rollView);
        yawView = (TextView)findViewById(R.id.yawView);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccSensor, SENSOR_DELAY, SENSOR_DELAY);
        mSensorManager.registerListener(this, mMagSensor, SENSOR_DELAY, SENSOR_DELAY);
    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onClick(View view) {
        if(isRecording) {
            recButton.setText("Start Recording");
            isRecording = false;
        } else {
            recButton.setText("Stop Recording");
            isRecording = true;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mGravity = event.values;
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = event.values;
        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
            if (success) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                yaw = orientation[0]; // orientation contains: azimuth, pitch and roll
                pitch = -orientation[1];
                roll = orientation[2];

                pitchView.setText(String.format("Pitch: %.2f°", Math.toDegrees(pitch)));
                rollView.setText(String.format("Roll: %.2f°", Math.toDegrees(roll)));
                yawView.setText(String.format("Yaw: %.2f°", Math.toDegrees(yaw)));
            }
        }
    }
}
