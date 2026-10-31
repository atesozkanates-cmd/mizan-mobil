package com.mizanlar.crm.ses;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.speech.RecognizerIntent;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.ArrayList;

/**
 * Sesli komut: telefonun kendi ses tanıma penceresini açar (Google), konuşmayı yazıya çevirip döndürür.
 * Uygulama mikrofonu kendisi kullanmaz; ses kaydı uygulamaya ya da sunucuya gelmez, yalnızca metin gelir.
 */
@CapacitorPlugin(name = "MizanSes")
public class MizanSesPlugin extends Plugin {
    @PluginMethod
    public void dinle(PluginCall call) {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, call.getString("dil", "tr-TR"));
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, call.getString("baslik", "Konuşun"));
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        try {
            startActivityForResult(call, i, "sonuc");
        } catch (ActivityNotFoundException e) {
            call.reject("Bu telefonda ses tanıma bulunamadı (Google uygulaması gerekli)");
        }
    }

    @ActivityCallback
    private void sonuc(PluginCall call, ActivityResult r) {
        if (call == null) return;
        ArrayList<String> metinler = r.getData() == null ? null : r.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        JSObject o = new JSObject();
        JSArray a = new JSArray();
        if (metinler != null) for (String m : metinler) a.put(m);
        o.put("metin", metinler != null && !metinler.isEmpty() ? metinler.get(0) : "");
        o.put("secenekler", a);
        call.resolve(o);
    }
}
