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
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private static final int SENSOR_DELAY = 50000; // µs
    private static final int ZIP_BUFFER = 2048; // bytes
    private static final boolean LOG_RAW = true;

    private SensorManager mSensorManager;
    private Sensor mAccSensor, mMagSensor, mGyroSensor;
    private float[] mGravity, mGeomagnetic;
    private Button recButton, calButton;
    private TextView infoTextView;
    private ExperimentData data = new ExperimentData(this);
    private Chronometer elapsedChronometer;
    private boolean isPaused, isRecording = false;
    private String ownerName;
    private Handler mHandlerRec;
    private Toolbar appToolbar;
    public CalibrationHandler cal = new CalibrationHandler(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // show on top of lock screen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        // set layout
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

        calButton = (Button)findViewById(R.id.calButton);
        calButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startCalibration();
            }
        });

        Button shareButton = (Button)findViewById(R.id.shareButton);
        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                shareData();
            }
        });

        infoTextView = (TextView)findViewById(R.id.infoTextView);

        elapsedChronometer = (Chronometer)findViewById(R.id.elapsedChronometer);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        mHandlerRec = new Handler() {
            @Override
            public void handleMessage(Message inputMessage) {
                try {
                    showDialog("Data saved", "Saved to: " + data.saveToFile(ownerName));
                } catch(IOException e) {
                    showDialog(e);
                }

                recButton.setText("Start Recording");
                recButton.setEnabled(true);
                calButton.setEnabled(true);
            }
        };

        appToolbar = (Toolbar)findViewById(R.id.appToolbar);
        setSupportActionBar(appToolbar);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete_files:
                deleteFiles();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void deleteFiles() {
        final File dir = getExternalFilesDir(null);
        final String[] children = dir.list();
        if (children.length == 0) {
            showDialog("Error", "No files found to delete");
            return;
        }

        String msg = "Are you sure you want to delete the following files?\n";
        for (String s : children) {
            msg += "\n- " + s;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(msg).setNegativeButton("No", null)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                for (String s : children) {
                    File f = new File(dir, s);
                    if (f.isFile()) {
                        f.delete();
                    }
                }
            }
        }).show();
    }

    protected void onResume() {
        super.onResume();
        isPaused = false;
        mSensorManager.registerListener(this, mAccSensor, SENSOR_DELAY, SENSOR_DELAY);
        mSensorManager.registerListener(this, mMagSensor, SENSOR_DELAY, SENSOR_DELAY);
        mSensorManager.registerListener(this, mGyroSensor, SENSOR_DELAY, SENSOR_DELAY);
    }

    protected void onPause() {
        super.onPause();
        isPaused = true;

        // disable sensors if neither calibrating nor recording
        if (!isRecording && !cal.isCalibrating) {
            mSensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        if (LOG_RAW && isRecording) {
            data.logRaw(type, event.values);
        }

        switch (type) {
            case Sensor.TYPE_ACCELEROMETER:
                mGravity = event.values;
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                mGeomagnetic = event.values;
                break;
            default:
                return;
        }
        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
            if (success) {
                float orient[] = new float[3];
                SensorManager.getOrientation(R, orient);
                Attitude att = cal.getAttitude(orient);

                if (!isPaused) {
                    infoTextView.setText(att.toString());
                }
                if (isRecording) {
                    data.log(att);
                }
            }
        }
    }

    private void startCalibration() {
        if (!cal.isCalibrating) {
            recButton.setEnabled(false);
            cal.start();
            calButton.setText("Calibrating");
        }
    }

    public void stopCalibration() {
        calButton.setText("Calibrated");
        recButton.setEnabled(true);
    }

    private void startRecording() {
        calButton.setEnabled(false);
        isRecording = true;
        data.start();
        recButton.setText("Stop Recording");
        elapsedChronometer.setBase(SystemClock.elapsedRealtime());
        elapsedChronometer.setVisibility(View.VISIBLE);
        elapsedChronometer.start();
    }

    private void stopRecording() {
        isRecording = false;
        recButton.setEnabled(false);
        recButton.setText("Saving data...");
        elapsedChronometer.setVisibility(View.INVISIBLE);
        elapsedChronometer.stop();

        mHandlerRec.sendEmptyMessage(0);
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

    public void showDialog(String title, String msg) {
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

    public void showDialog(Exception e) {
        showDialog("Error", "Caught exception: " + e.toString());
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
}