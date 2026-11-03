package com.mizanlar.crm.konum;

import com.mizanlar.crm.R;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.CancellationSignal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/**
 * Uygulama kullanılırken (ekran açıkken) konum alır: yalnızca bu telefonda şirket konum kurulumu yapılmışsa,
 * mesai saatindeyse, konum izni verilmişse ve son konumdan bu yana aralık (varsayılan 20 dk) geçtiyse.
 * Uygulama kapalıyken / arka plandayken hiç konum alınmaz.
 */
@android.annotation.TargetApi(26)
public final class Zamanlayici {
    private Zamanlayici() {}
    private static volatile boolean calisiyor = false;

    public static void belkiAl(Context context) {
        final Context c = context.getApplicationContext();
        if (calisiyor || Build.VERSION.SDK_INT < 26 || Ayar.anahtar(c).isEmpty()) return;
        if (c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED
                && c.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        calisiyor = true;
        new Thread(() -> {
            try {
                long simdi = System.currentTimeMillis();
                if (simdi - Ayar.tercih(c).getLong("ayarZamani", 0) > 12 * 3600_000L) Gonderici.ayarYenile(c);
                long son = Ayar.tercih(c).getLong("sonKonum", 0);
                long aralik = Ayar.aralikDk(c) * 60_000L;
                if (Ayar.mesaide(c) && simdi - son >= aralik - 90_000) {
                    Location l = konumAl(c);
                    if (l != null) {
                        Ayar.tercih(c).edit().putLong("sonKonum", simdi).apply();
                        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
                        f.setTimeZone(TimeZone.getTimeZone("UTC"));
                        JSONObject n = new JSONObject()
                                .put("zaman", f.format(new Date(l.getTime() > 0 ? Math.min(l.getTime(), simdi) : simdi)))
                                .put("enlem", l.getLatitude()).put("boylam", l.getLongitude())
                                .put("dogruluk", l.hasAccuracy() ? l.getAccuracy() : JSONObject.NULL)
                                .put("pil", c.getSystemService(BatteryManager.class).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
                        Gonderici.ekleVeGonder(c, n);
                    } else {
                        Ayar.durumYaz(c, neden(c));
                        Gonderici.ekleVeGonder(c, null);
                    }
                } else {
                    // internet yokken bekletilenler varsa göndermeyi dener
                    Gonderici.ekleVeGonder(c, null);
                }
            } catch (Exception ignored) {
            } finally {
                calisiyor = false;
            }
        }).start();
    }

    /**
     * Anlık konum. Önce Google birleşik konum servisi (GPS + Wi-Fi + baz istasyonu; iç mekânda da çalışır),
     * olmazsa telefonun kendi GPS / ağ konumu, o da olmazsa son 15 dakikadaki bilinen konum.
     * Toplam bekleme en çok ~60 sn (yayın alıcısının süre sınırını aşmamak için).
     */
    static Location konumAl(Context c) {
        Location l = birlesikKonum(c);
        if (l == null) l = telefonKonumu(c, Build.VERSION.SDK_INT >= 30 ? 20 : 0);
        return l;
    }

    /** Google Play Hizmetleri ile konum; hizmet yoksa ya da alınamazsa null. */
    static Location birlesikKonum(Context c) {
        try {
            if (com.google.android.gms.common.GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(c) != com.google.android.gms.common.ConnectionResult.SUCCESS) return null;
            com.google.android.gms.location.FusedLocationProviderClient f = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(c);
            // 1) Yüksek doğruluk (GPS dahil), en çok 25 sn; 2 dakikadan yeni hazır konum varsa o kullanılır
            Location l = guncel(f, com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 25_000);
            // 2) Dengeli (Wi-Fi / baz istasyonu), en çok 12 sn
            if (l == null) l = guncel(f, com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY, 12_000);
            // 3) Son bilinen konum (15 dakikadan yeniyse)
            if (l == null) {
                Location s = com.google.android.gms.tasks.Tasks.await(f.getLastLocation(), 3, TimeUnit.SECONDS);
                if (s != null && System.currentTimeMillis() - s.getTime() < 15 * 60_000L) l = s;
            }
            return l;
        } catch (Throwable e) {
            return null;
        }
    }

    private static Location guncel(com.google.android.gms.location.FusedLocationProviderClient f, int oncelik, long sureMs) {
        try {
            com.google.android.gms.location.CurrentLocationRequest r = new com.google.android.gms.location.CurrentLocationRequest.Builder()
                    .setPriority(oncelik).setDurationMillis(sureMs).setMaxUpdateAgeMillis(2 * 60_000L).build();
            return com.google.android.gms.tasks.Tasks.await(f.getCurrentLocation(r, null), sureMs + 3_000, TimeUnit.MILLISECONDS);
        } catch (Throwable e) {
            return null;
        }
    }

    /** Telefonun kendi konum servisi (GPS, yoksa ağ); bekleme sn cinsinden. Alınamazsa son bilinen konum (15 dk'dan yeniyse). */
    static Location telefonKonumu(Context c, int bekleSn) {
        LocationManager lm = c.getSystemService(LocationManager.class);
        final Location[] sonuc = new Location[1];
        try {
            if (Build.VERSION.SDK_INT >= 30 && bekleSn > 0) {
                for (String p : new String[] { LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER }) {
                    if (sonuc[0] != null || !lm.isProviderEnabled(p)) continue;
                    CountDownLatch bekle = new CountDownLatch(1);
                    lm.getCurrentLocation(p, new CancellationSignal(), Executors.newSingleThreadExecutor(), x -> { sonuc[0] = x; bekle.countDown(); });
                    bekle.await(p.equals(LocationManager.NETWORK_PROVIDER) ? 10 : bekleSn, TimeUnit.SECONDS);
                }
            }
            if (sonuc[0] == null) {
                for (String p : new String[] { LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER }) {
                    Location x = lm.getLastKnownLocation(p);
                    if (x != null && System.currentTimeMillis() - x.getTime() < 15 * 60_000L && (sonuc[0] == null || x.getTime() > sonuc[0].getTime())) sonuc[0] = x;
                }
            }
        } catch (SecurityException | InterruptedException ignored) { }
        return sonuc[0];
    }

    /** Konum alınamadığında bildirimde ve kurulum ekranında görünecek açıklama. */
    static String neden(Context c) {
        LocationManager lm = c.getSystemService(LocationManager.class);
        boolean acik = Build.VERSION.SDK_INT >= 28 ? lm.isLocationEnabled() : (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        if (!acik) return "Konum alınamadı (" + Ayar.saat() + "): telefonun konumu kapalı. Üst menüden Konum'u açın.";
        if (c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            return "Konum alınamadı (" + Ayar.saat() + "): konum izni verilmemiş.";
        return "Konum alınamadı (" + Ayar.saat() + "): sinyal bulunamadı; bir sonraki denemede yeniden alınacak.";
    }
}
