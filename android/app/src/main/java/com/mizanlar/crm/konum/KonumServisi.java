package com.mizanlar.crm.konum;

import com.mizanlar.crm.R;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

/**
 * Sürekli çalışan ön plan hizmeti. Android, arka planda konum alan programın bildirim çubuğunda görünmesini zorunlu tutar.
 * Konum her aralıkta Zamanlayici ile alınır; mesai dışında hiç konum alınmaz.
 */
@android.annotation.TargetApi(26)
public class KonumServisi extends Service {
    static final String KANAL = "konum";
    private BroadcastReceiver ayarDinleyici;

    /** Yalnızca cihaz anahtarı girilmiş (CRM'de tanımlı şirket telefonu) telefonlarda çalışır; kişisel telefonlarda hiçbir şey yapmaz. */
    public static void baslat(Context c) {
        if (Build.VERSION.SDK_INT < 26 || Ayar.anahtar(c).isEmpty()) return;
        try {
            Intent i = new Intent(c, KonumServisi.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception ignored) { }
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(KANAL, "Şirket konum hizmeti", NotificationManager.IMPORTANCE_LOW));
        ayarDinleyici = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { new Thread(() -> Gonderici.ayarYenile(c)).start(); }
        };
        registerReceiver(ayarDinleyici, new IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED));
    }

    static Notification bildirim(Context c) {
        String d = Ayar.durum(c);
        String metin = Ayar.anahtar(c).isEmpty() ? "Kurulum bekleniyor"
                : d.startsWith("Anahtar") || d.startsWith("Konum alınamadı") || d.startsWith("İnternet") ? d
                : Ayar.mesaide(c) ? "Mesai saatinde " + Ayar.aralikDk(c) + " dakikada bir konum alınıyor" + (d.isEmpty() ? "" : " · " + d)
                : "Mesai dışı: konum alınmıyor";
        return new Notification.Builder(c, KANAL)
                .setSmallIcon(R.drawable.konum_simge)
                .setContentTitle("Mizan CRM · şirket telefonu konumu")
                .setContentText(metin)
                .setOngoing(true)
                .build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, bildirim(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(1, bildirim(this));
        sonrakiniKur(this, 5_000);
        new Thread(() -> Gonderici.ayarYenile(getApplicationContext())).start();
        return START_STICKY;
    }

    /** Bir sonraki konum zamanı: cihaz uykudayken de çalışır (birkaç dakika gecikebilir). */
    static void sonrakiniKur(Context c, long gecikmeMs) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, new Intent(c, Zamanlayici.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + gecikmeMs, pi);
    }

    /** Kurulumu kaldırır: anahtar silinir, hizmet ve zamanlayıcı durur. */
    static void durdur(Context c) {
        Ayar.tercih(c).edit().remove("cihazAnahtari").remove("bekleyen").putString("sonDurum", "").apply();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        am.cancel(PendingIntent.getBroadcast(c, 1, new Intent(c, Zamanlayici.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        c.stopService(new Intent(c, KonumServisi.class));
    }

    @Override public void onDestroy() {
        try { unregisterReceiver(ayarDinleyici); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
