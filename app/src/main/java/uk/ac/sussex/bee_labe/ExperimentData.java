package uk.ac.sussex.bee_labe;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by ad374 on 15/09/17.
 */

public class ExperimentData {
    private ArrayList<DataPoint> dataList = new ArrayList(120);
    private long startTime, endTime;
    private String filename;
    private Context ctx;

    public ExperimentData(Context ctx) {
        this.ctx = ctx;
    }

    public void startLogging() {
        startTime = System.currentTimeMillis();
    }

    public void stopLogging() {
        endTime = System.currentTimeMillis();
    }

    public void log(float[] orient) {
        dataList.add(new DataPoint(System.currentTimeMillis() - startTime, orient));
    }

    public void clear() {
        dataList.clear();
    }

    public String saveToFile() throws IOException {
        final String filename = "data_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(startTime)) + ".txt";
        File dataFile = new File(ctx.getExternalFilesDir(null), filename);
        FileOutputStream stream = new FileOutputStream(dataFile);
        stream.write(toString().getBytes());
        stream.close();

        return dataFile.getAbsolutePath();
    }

    @Override
    public String toString() {
        String str = "Started at " + new Date(startTime) + "\n";
        for (int i = 0; i < dataList.size(); i++) {
            str += "  " + dataList.get(i) + "\n";
        }
        str += "Finished at " + new Date(endTime) + "\n";
        return str;
    }

    class DataPoint {
        private long time;
        private float yaw, pitch, roll;

        public DataPoint(long time, float[] orient) {
            this.time = time;
            yaw = orient[0];
            pitch = -orient[1];
            roll = orient[2];
        }

        @Override
        public String toString() {
            return String.format("%d: %.2f, %.2f, %.2f", this.time, this.yaw, this.pitch, this.roll);
        }
    }
}
