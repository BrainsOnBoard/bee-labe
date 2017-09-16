package uk.ac.sussex.bee_labe;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private static final int SENSOR_DELAY = 1000000; // µs
    private static final int ZIP_BUFFER = 2048; // bytes

    private SensorManager mSensorManager;
    private Sensor mAccSensor, mMagSensor;
    private float[] mGravity, mGeomagnetic;
    private float yaw, pitch, roll;
    private boolean isRecording = false;
    private Button recButton;
    private TextView pitchView, rollView, yawView;
    private ExperimentData data = new ExperimentData(this);
    private boolean isPaused;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recButton = (Button)findViewById(R.id.recButton);
        recButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(isRecording) {
                    stopRecording();
                } else {
                    startRecording();
                }
            }
        });

        Button shareButton = (Button)findViewById(R.id.shareButton);
        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                shareData();
            }
        });

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

    private void startRecording() {
        isRecording = true;
        data.startLogging();
        recButton.setText("Stop Recording");
    }

    private void stopRecording() {
        isRecording = false;
        data.stopLogging();
        recButton.setText("Start Recording");

        try {
            showDialog("Data saved", "Saved to: " + data.saveToFile());
        } catch(IOException e) {
            showDialog("Error", e.toString());
        }
    }

    private void shareData() {
        // get JSON files from application directory
        File[] files = getExternalFilesDir(null).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.endsWith(".json");
            }
        });

        if (files.length == 0) {
            showDialog("Error", "No data files found");
            return;
        }

        final String filename = getExternalFilesDir(null) + "/data_" + new SimpleDateFormat("yyyyMMdd").format(new Date()) + ".zip";
        byte[] data = new byte[ZIP_BUFFER];
        try {
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(filename));
            for (File file : files) {
                BufferedInputStream in = new BufferedInputStream(new FileInputStream(file), ZIP_BUFFER);
                out.putNextEntry(new ZipEntry(file.getName()));
                int count;
                while ((count = in.read(data, 0, ZIP_BUFFER)) != -1) {
                    out.write(data, 0, count);
                }
                in.close();
            }
            out.close();
        } catch (IOException e) {
            showDialog("Error", e.toString());
            return;
        }

        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(filename)));
        shareIntent.setType("application/zip");
        startActivity(Intent.createChooser(shareIntent, "Share data files"));
    }

    private void showDialog(String title, String msg) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(title);
        dialog.setMessage(msg.toString());
        dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
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
