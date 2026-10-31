package com.mizanlar.crm.konum;

import com.mizanlar.crm.R;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Konumları sunucuya gönderir; gönderilemeyenler (internet yoksa) bekletilir, en çok 150 nokta. */
@android.annotation.TargetApi(26)
final class Gonderici {
    private Gonderici() {}

    static synchronized void ekleVeGonder(Context c, JSONObject nokta) {
        SharedPreferences t = Ayar.tercih(c);
        JSONArray bekleyen;
        try { bekleyen = new JSONArray(t.getString("bekleyen", "[]")); } catch (Exception e) { bekleyen = new JSONArray(); }
        if (nokta != null) bekleyen.put(nokta);
        while (bekleyen.length() > 150) bekleyen.remove(0);
        t.edit().putString("bekleyen", bekleyen.toString()).apply();
        String anahtar = Ayar.anahtar(c);
        if (anahtar.isEmpty() || bekleyen.length() == 0) return;
        try {
            JSONObject govde = new JSONObject().put("noktalar", bekleyen);
            JSONObject yanit = istek(Ayar.sunucu(c) + "/api/konum/cihaz", "POST", anahtar, govde.toString());
            if (yanit != null && yanit.optBoolean("ok")) {
                t.edit().putString("bekleyen", "[]").apply();
                ayarKaydet(c, yanit);
                Ayar.durumYaz(c, "Son gönderim " + Ayar.saat());
            } else if (yanit != null && yanit.optInt("_kod") == 401) {
                Ayar.durumYaz(c, "Anahtar geçersiz: CRM'den yeni anahtar alıp kurulum ekranına girin");
            } else {
                Ayar.durumYaz(c, "Sunucu yanıt vermedi (" + Ayar.saat() + "); tekrar denenecek");
            }
        } catch (Exception e) {
            // internet yok: bir sonraki denemede gönderilir
            Ayar.durumYaz(c, "İnternet yok (" + Ayar.saat() + "); " + bekleyenSayisi(c) + " konum bekletiliyor");
        }
    }

    private static int bekleyenSayisi(Context c) {
        try { return new JSONArray(Ayar.tercih(c).getString("bekleyen", "[]")).length(); } catch (Exception e) { return 0; }
    }

    /** Kurulum ekranındaki deneme: boş gönderimle anahtarı sunucuya doğrulatır (telefon CRM'de "Aktif" olur). */
    static String dene(Context c) {
        String anahtar = Ayar.anahtar(c);
        if (anahtar.isEmpty()) return "✗ Cihaz anahtarı girilmedi";
        try {
            JSONObject y = istek(Ayar.sunucu(c) + "/api/konum/cihaz", "POST", anahtar, "{\"noktalar\":[]}");
            if (y != null && y.optBoolean("ok")) { ayarKaydet(c, y); Ayar.durumYaz(c, "Sunucuya bağlandı " + Ayar.saat()); return "✔ Sunucu anahtarı kabul etti"; }
            if (y != null && y.optInt("_kod") == 401) { Ayar.durumYaz(c, "Anahtar geçersiz: CRM'den yeni anahtar alıp kurulum ekranına girin"); return "✗ Anahtar geçersiz: CRM'deki anahtarla birebir aynı olmalı"; }
            return "✗ Sunucu beklenmeyen yanıt verdi";
        } catch (Exception e) {
            return "✗ Sunucuya ulaşılamadı: internet ve sunucu adresini kontrol edin";
        }
    }

    /** Mesai ayarını sunucudan alır (günde bir kez yeter). */
    static void ayarYenile(Context c) {
        String anahtar = Ayar.anahtar(c);
        if (anahtar.isEmpty()) return;
        try {
            JSONObject y = istek(Ayar.sunucu(c) + "/api/konum/cihaz", "GET", anahtar, null);
            if (y != null && y.optBoolean("ok")) ayarKaydet(c, y);
        } catch (Exception ignored) { }
    }

    private static void ayarKaydet(Context c, JSONObject y) {
        Set<String> gunler = new HashSet<>();
        JSONArray l = y.optJSONArray("isGunleri");
        if (l != null) for (int i = 0; i < l.length(); i++) gunler.add(l.optString(i));
        SharedPreferences.Editor e = Ayar.tercih(c).edit()
                .putString("mesaiBas", y.optString("mesaiBas", "09:00"))
                .putString("mesaiBit", y.optString("mesaiBit", "18:30"))
                .putInt("aralikDk", y.optInt("aralikDk", 20))
                .putLong("ayarZamani", System.currentTimeMillis());
        if (!gunler.isEmpty()) e.putStringSet("isGunleri", gunler);
        e.apply();
    }

    private static JSONObject istek(String adres, String yontem, String anahtar, String govde) throws Exception {
        HttpURLConnection b = (HttpURLConnection) new URL(adres).openConnection();
        b.setRequestMethod(yontem);
        b.setConnectTimeout(20000);
        b.setReadTimeout(30000);
        b.setRequestProperty("x-cihaz-anahtari", anahtar);
        if (govde != null) {
            b.setDoOutput(true);
            b.setRequestProperty("Content-Type", "application/json");
            try (OutputStream o = b.getOutputStream()) { o.write(govde.getBytes(StandardCharsets.UTF_8)); }
        }
        int kod = b.getResponseCode();
        try (InputStream i = kod < 400 ? b.getInputStream() : b.getErrorStream()) {
            if (i == null) return new JSONObject().put("_kod", kod);
            ByteArrayOutputStream s = new ByteArrayOutputStream();
            byte[] tampon = new byte[4096];
            int n;
            while ((n = i.read(tampon)) > 0) s.write(tampon, 0, n);
            JSONObject y;
            try { y = new JSONObject(s.toString("UTF-8")); } catch (Exception e) { y = new JSONObject(); }
            return y.put("_kod", kod);
        } finally {
            b.disconnect();
        }
    }
}
