package it.myrmm.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Dopo il riavvio o l'aggiornamento dell'app riprende i check-in. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Config.configured(context)) Sched.start(context);
    }
}
