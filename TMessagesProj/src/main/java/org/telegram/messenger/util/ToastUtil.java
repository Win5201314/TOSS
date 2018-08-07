package org.telegram.messenger.util;

import android.content.Context;
import android.widget.Toast;

public class ToastUtil {

    private static Toast toast = null;
    public static void show(Context context, int stringId, boolean delay) {
        String text = context.getResources().getString(stringId);
        int time = delay ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT;
        if(toast == null){
            toast = Toast.makeText(context, text,  time);
        }else{
            toast.setText(text);
            toast.setDuration(time);
        }
        toast.show();
    }

    public static void normalShow(Context context, String text, boolean delay) {
        int time = delay ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT;
        if(toast == null){
            toast = Toast.makeText(context, text,  time);
        }else{
            toast.setText(text);
            toast.setDuration(time);
        }
        toast.show();
    }

}

