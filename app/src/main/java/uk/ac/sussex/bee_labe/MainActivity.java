package uk.ac.sussex.bee_labe;

import android.content.Context;
import android.content.DialogInterface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements SensorEventListener, View.OnClickListener {
    private static final int SENSOR_DELAY = 1000000; // µs

    private SensorManager mSensorManager;
    private Sensor mAccSensor, mMagSensor;
    private float[] mGravity, mGeomagnetic;
    private float yaw, pitch, roll;
    private boolean isRecording = false;
    private Button recButton;
    private TextView pitchView, rollView, yawView;
    private ExperimentData data = new ExperimentData();
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
        data.startLogging();
        recButton.setText("Stop Recording");
    }

    private void stopRecording() {
        isRecording = false;
        data.stopLogging();
        recButton.setText("Start Recording");

        String msg = data.toString();
        File dataFile = new File(getExternalFilesDir(null), "beedata.txt");
        try {
            FileOutputStream stream = new FileOutputStream(dataFile);
            stream.write(msg.getBytes());
            stream.close();
        } catch (IOException e) {
            msg = e.toString();
        }
        msg = dataFile.getAbsolutePath() + "\n" + msg;

        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Data");
        dialog.setMessage(msg);
        dialog.setPositiveButton(" OK ", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();

            }
        });
        data.clear();
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
                float orient[] = new float[3];
                SensorManager.getOrientation(R, orient);

                if (!isPaused) {
                    yaw = orient[0];
                    pitch = -orient[1];
                    roll = orient[2];

                    pitchView.setText(String.format("Pitch: %.2f°", Math.toDegrees(pitch)));
                    rollView.setText(String.format("Roll: %.2f°", Math.toDegrees(roll)));
                    yawView.setText(String.format("Yaw: %.2f°", Math.toDegrees(yaw)));
                }

                if (isRecording) {
                    data.log(orient);
                }
            }
        }
    }
}
