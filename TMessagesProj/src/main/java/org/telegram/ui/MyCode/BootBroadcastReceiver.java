package org.telegram.ui.MyCode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.telegram.ui.LaunchActivity;

public class BootBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")){
            Intent intent1 = new Intent(context, LaunchActivity.class);
            context.startActivity(intent1);
            Log.d("TAG", "开机自启动电报!");
        }
    }
}
