package com.mizanlar.crm;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import com.getcapacitor.BridgeActivity;
import com.mizanlar.crm.konum.MizanKonumPlugin;
import com.mizanlar.crm.konum.Zamanlayici;
import com.mizanlar.crm.ses.MizanSesPlugin;

public class MainActivity extends BridgeActivity {
    // Uygulama açıkken dakikada bir bakılır; konum ancak aralık (20 dk) dolunca ve mesaideyse alınır.
    private final Handler zaman = new Handler(Looper.getMainLooper());
    private final Runnable konumBak = new Runnable() {
        @Override public void run() {
            Zamanlayici.belkiAl(MainActivity.this);
            zaman.postDelayed(this, 60_000);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Uygulamaya özel eklentiler: şirket telefonu konum kurulumu ve sesli komut
        registerPlugin(MizanKonumPlugin.class);
        registerPlugin(MizanSesPlugin.class);
        super.onCreate(savedInstanceState);
    }

    // Konum yalnızca uygulama ekrandayken (kullanılırken) ve yalnızca kurulum yapılmış şirket telefonunda alınır
    @Override public void onResume() { super.onResume(); zaman.removeCallbacks(konumBak); zaman.post(konumBak); }
    @Override public void onPause() { zaman.removeCallbacks(konumBak); super.onPause(); }
}
