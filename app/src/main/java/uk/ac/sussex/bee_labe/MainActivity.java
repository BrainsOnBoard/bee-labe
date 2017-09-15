package uk.ac.sussex.bee_labe;

import android.content.Context;
import android.content.DialogInterface;
import android.hardware.*;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements SensorEventListener, View.OnClickListener {
    private static final int SENSOR_DELAY = 1000000; // µs

    private SensorManager mSensorManager;
    private Sensor mAccSensor, mMagSensor;
    private float[] mGravity, mGeomagnetic;
    private float yaw, pitch, roll;
    private boolean isRecording = false;
    private Button recButton;
    private TextView pitchView, rollView, yawView;
    private ArrayList<Attitude> data = new ArrayList(100);
    private boolean isPaused;

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
        isPaused = false;
        mSensorManager.registerListener(this, mAccSensor, SENSOR_DELAY, SENSOR_DELAY);
        mSensorManager.registerListener(this, mMagSensor, SENSOR_DELAY, SENSOR_DELAY);
    }

    protected void onPause() {
        super.onPause();
        isPaused = true;

        if (!isRecording) {
            mSensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onClick(View view) {
        if(isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        isRecording = true;
        recButton.setText("Stop Recording");
    }

    private void stopRecording() {
        isRecording = false;
        recButton.setText("Start Recording");

        String dString = "";
        for (int i = 0; i < data.size(); i++) {
            dString += data.get(i) + "\n";
        }
        data.clear();

        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Data");
        dialog.setMessage(dString);
        dialog.setPositiveButton(" OK ", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();

            }
        });
        dialog.show();
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

                if (!isPaused) {
                    yaw = orientation[0];
                    pitch = -orientation[1];
                    roll = orientation[2];

                    pitchView.setText(String.format("Pitch: %.2f°", Math.toDegrees(pitch)));
                    rollView.setText(String.format("Roll: %.2f°", Math.toDegrees(roll)));
                    yawView.setText(String.format("Yaw: %.2f°", Math.toDegrees(yaw)));
                }

                if (isRecording) {
                    data.add(new Attitude(orientation));
                }
            }
        }
    }

    class Attitude {
        public float yaw, pitch, roll;

        public Attitude(float[] orient) {
            yaw = orient[0];
            pitch = -orient[1];
            roll = orient[2];
        }

        @Override
        public String toString() {
            return String.format("%.2f, %.2f, %.2f", this.yaw, this.pitch, this.roll);
        }
    }
}
