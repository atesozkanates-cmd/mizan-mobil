package com.mizanlar.crm.konum;

import android.content.Intent;
import android.os.Build;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * CRM sayfasından çağrılır (yalnızca telefon uygulamasında):
 *  - kurulumAc({anahtar?}): şirket telefonu konum kurulum ekranını açar; CRM'de yeni üretilen anahtar verilirse kutuya yazılmış gelir
 *  - durum(): bu telefonda konum kurulu mu, mesai mi, son durum
 */
@CapacitorPlugin(name = "MizanKonum")
public class MizanKonumPlugin extends Plugin {
    @PluginMethod
    public void kurulumAc(PluginCall call) {
        if (Build.VERSION.SDK_INT < 26) { call.reject("Android 8 ve üzeri gerekli"); return; }
        String a = call.getString("anahtar", "");
        if (a != null && !a.trim().isEmpty()) Ayar.tercih(getContext()).edit().putString("taslakAnahtar", a.trim()).apply();
        Intent i = new Intent(getContext(), Kurulum.class);
        getActivity().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void durum(PluginCall call) {
        JSObject r = new JSObject();
        boolean kurulu = !Ayar.anahtar(getContext()).isEmpty();
        r.put("kurulu", kurulu);
        r.put("mesaide", Build.VERSION.SDK_INT >= 26 && Ayar.mesaide(getContext()));
        r.put("sonDurum", Ayar.durum(getContext()));
        call.resolve(r);
    }
}
