package org.telegram.ui.MyCode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;

public class Util {

    public static void log(String content) {
        Util.wrtieFile("/sdcard/log.txt", Util.getTime() + " " + content + "\r\n", true, "utf-8");
    }

    public static void wrtieFile(String filenPath, String content, boolean append, String encode) {
        File file = new File(filenPath);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try {
            FileOutputStream fos = new FileOutputStream(file, append);
            fos.write(content.getBytes(encode));
            fos.close();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public static final String DEFAULTTIMEFORMAT = "yyyy-MM-dd HH:mm:ss";
    public static String getTime() {
        SimpleDateFormat formatter = new SimpleDateFormat(DEFAULTTIMEFORMAT);
        return formatter.format(new java.util.Date());
    }

}
