package com.mizanlar.crm;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.mizanlar.crm.konum.MizanKonumPlugin;
import com.mizanlar.crm.ses.MizanSesPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Uygulamaya özel eklentiler: şirket telefonu konum kurulumu ve sesli komut
        registerPlugin(MizanKonumPlugin.class);
        registerPlugin(MizanSesPlugin.class);
        super.onCreate(savedInstanceState);
        // Bu telefonda konum kurulumu yapılmışsa hizmet çalışıyor olsun (kişisel telefonlarda hiçbir şey yapmaz)
        com.mizanlar.crm.konum.KonumServisi.baslat(this);
    }
}
