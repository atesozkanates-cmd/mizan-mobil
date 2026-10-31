package com.mizanlar.crm.konum;

import com.mizanlar.crm.R;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

/**
 * Elle kurulum ekranı (MDM'siz / B yolu).
 * Yetkili kişi cihaz anahtarını CRM'den alıp girer; konum ve bildirim izinleri ile pil kısıtlaması burada verilir.
 * Yönetilen (MDM) cihazlarda bu ekrana gerek yoktur; o zaman yapılandırma MDM'den gelir.
 */
@android.annotation.TargetApi(26)
public class Kurulum extends Activity {
    private EditText sunucuAlan, anahtarAlan;
    private TextView durum;
    private String denemeSonucu = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout k = new LinearLayout(this);
        k.setOrientation(LinearLayout.VERTICAL);
        k.setPadding(p, p, p, p);

        TextView baslik = new TextView(this);
        baslik.setText("Mizan Şirket Telefonu Kurulumu");
        baslik.setTextSize(20);
        baslik.setPadding(0, 0, 0, p);
        k.addView(baslik);

        TextView bilgi = new TextView(this);
        bilgi.setText("Bu ekran yalnızca Mizan & Partners'a ait, personele zimmetli şirket telefonları içindir.\n\n"
                + "Kurulum yapılırsa Mizan CRM, uygulama kapalıyken de (arka planda) bu telefonun konumunu toplar: "
                + "yalnızca mesai saatlerinde (hafta içi 09:00–18:30, resmî tatiller hariç) yaklaşık 20 dakikada bir. "
                + "Konum bilgisi saha çalışmasının takibi için şirket sunucusuna gönderilir, yalnızca ortaklar ve yetkili kişiler görür ve 90 gün sonra silinir. "
                + "Konum alınırken bildirim çubuğunda bilgi bildirimi görünür.\n\n"
                + "Kişisel telefonlarda bu kurulumu yapmayın. Devam ederseniz konum izni istenecektir.");
        bilgi.setPadding(0, 0, 0, p);
        k.addView(bilgi);

        sunucuAlan = new EditText(this);
        sunucuAlan.setHint("Sunucu adresi");
        sunucuAlan.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        sunucuAlan.setText(Ayar.tercih(this).getString("sunucu", "https://crm.mizanlar.com"));
        k.addView(sunucuAlan);

        anahtarAlan = new EditText(this);
        anahtarAlan.setHint("Cihaz anahtarı (CRM'den)");
        anahtarAlan.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_CLASS_TEXT);
        String taslak = Ayar.tercih(this).getString("taslakAnahtar", "");
        anahtarAlan.setText(taslak.isEmpty() ? Ayar.tercih(this).getString("cihazAnahtari", "") : taslak);
        Ayar.tercih(this).edit().remove("taslakAnahtar").apply();
        k.addView(anahtarAlan);

        Button kaydet = new Button(this);
        kaydet.setText("Kaydet ve başlat");
        kaydet.setOnClickListener(v -> kaydet());
        k.addView(kaydet);

        Button kaldir = new Button(this);
        kaldir.setText("Bu telefonda konumu kapat (kurulumu kaldır)");
        kaldir.setOnClickListener(v -> {
            KonumServisi.durdur(getApplicationContext());
            anahtarAlan.setText("");
            denemeSonucu = "Konum kurulumu kaldırıldı; bu telefondan konum gönderilmez.";
            durumGoster();
        });
        k.addView(kaldir);

        durum = new TextView(this);
        durum.setPadding(0, p, 0, 0);
        k.addView(durum);

        setContentView(k);
    }

    @Override protected void onResume() { super.onResume(); durumGoster(); }

    private void kaydet() {
        if (Build.VERSION.SDK_INT < 26) { Toast.makeText(this, "Bu telefonun Android sürümü konum hizmeti için çok eski (en az Android 8)", Toast.LENGTH_LONG).show(); return; }
        String s = sunucuAlan.getText().toString().trim();
        String a = anahtarAlan.getText().toString().trim();
        a = a.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
        if (a.isEmpty()) { Toast.makeText(this, "Cihaz anahtarı gerekli", Toast.LENGTH_SHORT).show(); return; }
        if (!a.matches("[a-f0-9]{48}")) {
            durum.setText("✗ Anahtar 48 karakter olmalı (0-9 ve a-f harfleri); şu an " + a.length() + " karakter. CRM'deki anahtarı kontrol edin.");
            return;
        }
        Ayar.tercih(this).edit().putString("sunucu", s).putString("cihazAnahtari", a).apply();
        anahtarAlan.setText(a);
        // Anahtar sunucuya hemen doğrulatılır; doğruysa telefon CRM'de "Aktif" olur
        durum.setText("Sunucu deneniyor…");
        new Thread(() -> {
            String sonuc = Gonderici.dene(getApplicationContext());
            runOnUiThread(() -> { denemeSonucu = sonuc; durumGoster(); if (sonuc.startsWith("✔")) izinIste(); });
        }).start();
    }

    /** Konum (ön plan) ve bildirim izinlerini ister; sonra arka plan konumuna geçer. */
    private void izinIste() {
        ArrayList<String> iste = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            iste.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            iste.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!iste.isEmpty()) { requestPermissions(iste.toArray(new String[0]), 1); return; }
        arkaPlanKonum();
    }

    @Override public void onRequestPermissionsResult(int kod, String[] izinler, int[] sonuc) {
        if (kod == 1) arkaPlanKonum();
        else if (kod == 2) pilVeBaslat();
    }

    /** Android 10+ arka plan konumu ayrı istenir ("Her zaman izin ver"). */
    private void arkaPlanKonum() {
        if (Build.VERSION.SDK_INT >= 29
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Konumu 'Her zaman izin ver' seçin", Toast.LENGTH_LONG).show();
            requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 2);
            return;
        }
        pilVeBaslat();
    }

    /** Pil kısıtlaması muafiyeti istenir ve hizmet başlatılır. */
    private void pilVeBaslat() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (Build.VERSION.SDK_INT >= 23 && pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) { }
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            KonumServisi.baslat(this);
            Toast.makeText(this, "Kurulum tamam, konum hizmeti başladı", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Konum izni verilmeden hizmet başlamaz", Toast.LENGTH_LONG).show();
        }
        durumGoster();
    }

    private void durumGoster() {
        boolean konum = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean arka = Build.VERSION.SDK_INT < 29 || checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean bildirim = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        PowerManager pm = getSystemService(PowerManager.class);
        boolean pil = Build.VERSION.SDK_INT < 23 || (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName()));
        boolean anahtar = !Ayar.tercih(this).getString("cihazAnahtari", "").isEmpty();
        durum.setText(
                im(anahtar) + " Cihaz anahtarı\n" +
                im(konum) + " Konum izni\n" +
                im(arka) + " Her zaman konum\n" +
                im(bildirim) + " Bildirim izni\n" +
                im(pil) + " Pil kısıtlaması kapalı" +
                (denemeSonucu.isEmpty() ? "" : "\n\n" + denemeSonucu) +
                (Ayar.durum(this).isEmpty() ? "" : "\nSon durum: " + Ayar.durum(this)));
    }

    private static String im(boolean tamam) { return tamam ? "✔" : "✗"; }
}
