package com.ubtrobot.cerebra.other;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ubtrobot.cerebra.CerebraService;
import com.ubtrobot.ulog.Logger;
import com.ubtrobot.ulog.ULog;

/**
 * A Receiver that receives @{WakeupEvnet}
 */
public class WakeupEventReceiver extends BroadcastReceiver {
    private static final Logger LOGGER = ULog.getLogger("WakeupEventReceiver");

    @Override
    public void onReceive(Context context, Intent intent) {
        LOGGER.i("wakeup event received: ");

        context.startService(new Intent(context, CerebraService.class)
                .setAction(intent.getAction()));
    }
}
