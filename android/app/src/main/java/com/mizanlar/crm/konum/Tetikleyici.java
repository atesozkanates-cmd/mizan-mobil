package com.mizanlar.crm.konum;

import com.mizanlar.crm.R;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Telefon açılınca ve uygulama güncellenince, yalnızca bu telefonda konum kurulumu yapılmışsa hizmeti başlatır. */
@android.annotation.TargetApi(26)
public class Tetikleyici extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) { KonumServisi.baslat(c); }
}
