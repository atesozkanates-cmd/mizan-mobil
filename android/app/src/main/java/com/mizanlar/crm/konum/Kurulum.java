package com.mizanlar.crm.konum;

import com.mizanlar.crm.R;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Şirket telefonu konum kurulumu (CRM'deki Şirket Telefonları sayfasından açılır).
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
                + "Kurulum yapılırsa Mizan CRM, yalnızca uygulama açıkken ve kullanılırken bu telefonun konumunu alır: "
                + "mesai saatlerinde (hafta içi 09:00–18:30, resmî tatiller hariç) en çok yaklaşık 20 dakikada bir. "
                + "Uygulama kapalıyken veya arka plandayken konum alınmaz. "
                + "Konum bilgisi saha çalışmasının takibi için şirket sunucusuna gönderilir, yalnızca ortaklar ve yetkili kişiler görür ve 90 gün sonra silinir.\n\n"
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
            Ayar.tercih(getApplicationContext()).edit().remove("cihazAnahtari").remove("bekleyen").putString("sonDurum", "").apply();
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

    /** Yalnızca uygulama kullanılırken konum izni istenir (arka plan konumu istenmez). */
    private void izinIste() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            return;
        }
        bitti();
    }

    @Override public void onRequestPermissionsResult(int kod, String[] izinler, int[] sonuc) { if (kod == 1) bitti(); }

    private void bitti() {
        boolean izin = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (izin) {
            Ayar.tercih(this).edit().putLong("sonKonum", 0).apply();
            Zamanlayici.belkiAl(this);
            Toast.makeText(this, "Kurulum tamam: uygulama kullanılırken mesai saatinde konum alınacak", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Konum izni verilmeden konum alınamaz", Toast.LENGTH_LONG).show();
        }
        durumGoster();
    }

    private void durumGoster() {
        boolean konum = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean anahtar = !Ayar.tercih(this).getString("cihazAnahtari", "").isEmpty();
        durum.setText(
                im(anahtar) + " Cihaz anahtarı\n" +
                im(konum) + " Konum izni (uygulama kullanılırken)" +
                (denemeSonucu.isEmpty() ? "" : "\n\n" + denemeSonucu) +
                (Ayar.durum(this).isEmpty() ? "" : "\nSon durum: " + Ayar.durum(this)));
    }

    private static String im(boolean tamam) { return tamam ? "✔" : "✗"; }
}
