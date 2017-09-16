package uk.ac.sussex.bee_labe;

import android.content.Context;
import android.util.JsonWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by ad374 on 15/09/17.
 */

public class ExperimentData {
    private ArrayList<DataPoint> dataList = new ArrayList(5 * 60);
    private long startTime;
    private Date startDate;
    private Context ctx;

    public ExperimentData(Context ctx) {
        this.ctx = ctx;
    }

    public void startLogging() {
        startDate = new Date();
        startTime = System.nanoTime();
    }

    public void log(float[] orient) {
        dataList.add(new DataPoint(System.nanoTime(), orient));
    }

    public String saveToFile() throws IOException {
        Date endDate = new Date();

        final String filename = "data_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(startDate) + ".json";
        File dataFile = new File(ctx.getExternalFilesDir(null), filename);
        FileOutputStream stream = new FileOutputStream(dataFile);
        JsonWriter writer = new JsonWriter(new OutputStreamWriter(stream, "UTF-8"));

        writer.beginObject();
        writer.name("startTime").value(new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(startDate));
        writer.name("endTime").value(new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(endDate));

        writer.name("data");
        writer.beginArray();
        for(int i = 0; i < dataList.size(); i++) {
            DataPoint datum = dataList.get(i);
            writer.beginObject();
            writer.name("time").value((datum.time - startTime) / 1000000);
            writer.name("yaw").value(datum.yaw);
            writer.name("pitch").value(datum.pitch);
            writer.name("roll").value(datum.roll);
            writer.endObject();
        }
        writer.endArray();
        writer.endObject();
        writer.close();

        dataList.clear();

        return dataFile.getAbsolutePath();
    }

    class DataPoint {
        private long time;
        private float yaw, pitch, roll;

        public DataPoint(long time, float[] orient) {
            this.time = time;
            yaw = orient[0];
            if (yaw < 0) {
                yaw += 2 * Math.PI;
            }
            pitch = -orient[1];
            roll = orient[2];
        }

        @Override
        public String toString() {
            return String.format("%d: %.2f, %.2f, %.2f", this.time, this.yaw, this.pitch, this.roll);
        }
    }
}
