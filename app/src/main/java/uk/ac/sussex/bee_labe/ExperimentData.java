package uk.ac.sussex.bee_labe;

import android.content.Context;
import android.hardware.Sensor;
import android.os.Build;
import android.util.JsonWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by ad374 on 15/09/17.
 */

public class ExperimentData {
    private ArrayList<DataPoint> dataList = new ArrayList(1000);
    private ArrayList<RawData> rawDataList = new ArrayList(1000);
    private long startTime;
    private Date startDate;
    private MainActivity main;

    public ExperimentData(MainActivity main) {
        this.main = main;
    }

    public void start() {
        startDate = new Date();
        startTime = System.nanoTime();
    }

    public void log(Attitude attitude) {
        dataList.add(new DataPoint(System.nanoTime(), attitude));
    }

    public void logRaw(int sensorType, float[] values) {
        rawDataList.add(new RawData(System.nanoTime(), sensorType, values));
    }

    public String saveToFile(String ownerName) throws IOException {
        // date and time at which this "trial" ended
        Date endDate = new Date();

        // work out phone owner's initials (we could also probably get this directly somehow)
        int space = ownerName.indexOf(' ');
        String initials;
        if (space != -1 && space+1 < ownerName.length()) {
            initials = new String(new char[] {ownerName.charAt(0), ownerName.charAt(space+1)});
        } else {
            initials = ownerName;
        }

        // filename is composed of the end date, time and owner's initials
        final String filename = String.format("data_%s_%s.json", new SimpleDateFormat("yyyyMMdd_HHmmss").format(startDate),
                initials);

        // save file to external storage
        File dataFile = new File(main.getExternalFilesDir(null), filename);
        FileOutputStream stream = new FileOutputStream(dataFile);

        // save data as JSON
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(stream, "UTF-8"));
        writer.setIndent("  ");
        writer.beginObject();

        // save start and end time as dates
        writer.name("startTime").value(new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(startDate));
        writer.name("endTime").value(new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(endDate));

        // save phone owner's name
        writer.name("experimenter").value(ownerName);

        // this probably won't get the right model, but at least the manufacturer should be right
        writer.name("phone_model").value(Build.MANUFACTURER + " " + Build.MODEL);

        // save calibration data to file
        writer.name("calibration");
        writer.beginObject();
        writer.name("pitch").value(main.cal.pitch);
        writer.name("roll").value(main.cal.roll);
        writer.endObject();

        // begin writing data array
        writer.name("data");
        writer.beginArray();
        for(int i = 0; i < dataList.size(); i++) {
            DataPoint datum = dataList.get(i);

            // write timestamp and attitude for each data point, as JSON object
            writer.beginObject();
            writer.name("time").value((datum.time - startTime) / 1000000);
            writer.name("yaw").value(datum.attitude.yaw);
            writer.name("pitch").value(datum.attitude.pitch);
            writer.name("roll").value(datum.attitude.roll);
            writer.endObject();
        }
        // end of data array
        writer.endArray();

        // save raw data if we have it
        if (rawDataList.size() > 0) {
            writer.name("raw_data");
            writer.beginArray();
            for (int i = 0; i < rawDataList.size(); i++) {
                RawData data = rawDataList.get(i);

                writer.beginObject();
                writer.name("time").value((data.time - startTime) / 1000000);
                writer.name("type");
                switch (data.sensorType) {
                    case Sensor.TYPE_ACCELEROMETER:
                        writer.value("acc");
                        break;
                    case Sensor.TYPE_MAGNETIC_FIELD:
                        writer.value("mag");
                        break;
                    case Sensor.TYPE_GYROSCOPE:
                        writer.value("gyro");
                        break;
                    default:
                        writer.value("unknown");
                }

                writer.name("values");
                writer.beginArray();
                for(float f : data.values) {
                    writer.value(f);
                }
                writer.endArray();
                writer.endObject();
            }
            writer.endArray();
        }

        // end of file
        writer.endObject();
        writer.close();

        // delete data from memory so we can start over
        dataList.clear();
        rawDataList.clear();

        // return the file path for display
        return dataFile.getAbsolutePath();
    }

    class DataPoint {
        long time;
        Attitude attitude;

        public DataPoint(long time, Attitude attitude) {
            this.time = time;
            this.attitude = attitude;
        }
    }

    class RawData {
        long time;
        int sensorType;
        float[] values;

        public RawData(long time, int sensorType, float[] values) {
            this.time = time;
            this.sensorType = sensorType;
            this.values = values;
        }
    }
}
