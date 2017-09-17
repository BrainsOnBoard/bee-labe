package uk.ac.sussex.bee_labe;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
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
    private Chronometer elapsedChronometer;
    private boolean isPaused;
    private String ownerName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ownerName = getOwnerName();
        if (ownerName == null) {
            ownerName = "(unknown)";
        }

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

        elapsedChronometer = (Chronometer)findViewById(R.id.elapsedChronometer);

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
        elapsedChronometer.setBase(SystemClock.elapsedRealtime());
        elapsedChronometer.setVisibility(View.VISIBLE);
        elapsedChronometer.start();
    }

    private void stopRecording() {
        isRecording = false;
        recButton.setText("Start Recording");
        elapsedChronometer.setVisibility(View.INVISIBLE);
        elapsedChronometer.stop();

        try {
            showDialog("Data saved", "Saved to: " + data.saveToFile(ownerName));
        } catch(IOException e) {
            showDialog(e);
        }
    }

    private String getOwnerName() {
        String name = null;
        Cursor c = getContentResolver().query(ContactsContract.Profile.CONTENT_URI, null, null, null, null);
        int index;
        if (c.moveToFirst() && (index = c.getColumnIndex("display_name")) != -1) {
            name = c.getString(index);
        }

        c.close();
        return name;
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

        final String filename = String.format("%s/%s's data (%s).zip", getExternalFilesDir(null),
                ownerName, new SimpleDateFormat("yyyy.MM.dd").format(new Date()));
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
            showDialog(e);
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

    private void showDialog(Exception e) {
        showDialog("Error", "Caught exception: " + e.toString());
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
                    yaw = (float)Math.toDegrees(orient[0]);
                    if (yaw < 0) {
                        yaw += 360;
                    }
                    pitch = (float)Math.toDegrees(-orient[1]);
                    roll = (float)Math.toDegrees(orient[2]);

                    yawView.setText(String.format("Yaw: %.2f°", yaw));
                    pitchView.setText(String.format("Pitch: %.2f°", pitch));
                    rollView.setText(String.format("Roll: %.2f°", roll));
                }

                if (isRecording) {
                    data.log(orient);
                }
            }
        }
    }
}
