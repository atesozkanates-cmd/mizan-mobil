package com.mizanlar.crm.konum;

import com.mizanlar.crm.R;

import android.content.Context;
import android.content.RestrictionsManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Yapılandırma: sunucu ve cihaz anahtarı.
 * Önce MDM'in gönderdiği yönetilen yapılandırmaya bakılır; boşsa Kurulum ekranıyla
 * elle girilen (SharedPreferences) değer kullanılır. Böylece hem MDM'li (A) hem elle (B) kurulum çalışır.
 */
@android.annotation.TargetApi(26)
final class Ayar {
    static final TimeZone TR = TimeZone.getTimeZone("Europe/Istanbul");

    static SharedPreferences tercih(Context c) { return c.getSharedPreferences("ayar", Context.MODE_PRIVATE); }

    static String sunucu(Context c) {
        Bundle b = ((RestrictionsManager) c.getSystemService(Context.RESTRICTIONS_SERVICE)).getApplicationRestrictions();
        String s = b.getString("sunucu", "");
        if (s == null || s.isEmpty()) s = tercih(c).getString("sunucu", "https://crm.mizanlar.com");
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    static String anahtar(Context c) {
        Bundle b = ((RestrictionsManager) c.getSystemService(Context.RESTRICTIONS_SERVICE)).getApplicationRestrictions();
        String a = b.getString("cihazAnahtari", "");
        if (a == null || a.isEmpty()) a = tercih(c).getString("cihazAnahtari", "");
        // Elle yazılan anahtardaki boşluklar ve büyük harfler yok sayılır (sunucu da aynısını yapar)
        return a == null ? "" : a.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /** Son gönderimin sonucu (bildirimde ve kurulum ekranında gösterilir). */
    static void durumYaz(Context c, String metin) { tercih(c).edit().putString("sonDurum", metin).apply(); }
    static String durum(Context c) { return tercih(c).getString("sonDurum", ""); }
    static String saat() {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("HH:mm", Locale.ROOT);
        f.setTimeZone(TR);
        return f.format(new java.util.Date());
    }

    static int aralikDk(Context c) { return Math.max(5, tercih(c).getInt("aralikDk", 20)); }

    private static int dakika(String hhmm, int varsayilan) {
        try { return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5)); } catch (Exception e) { return varsayilan; }
    }

    /** Şu an mesai mi: sunucunun bildirdiği iş günlerinden biri ve 09:00–18:30 arası. İş günü listesi yoksa hafta içi sayılır. */
    static boolean mesaide(Context c) {
        SharedPreferences t = tercih(c);
        Calendar k = Calendar.getInstance(TR, Locale.ROOT);
        String gun = String.format(Locale.ROOT, "%04d-%02d-%02d", k.get(Calendar.YEAR), k.get(Calendar.MONTH) + 1, k.get(Calendar.DAY_OF_MONTH));
        Set<String> gunler = t.getStringSet("isGunleri", new HashSet<>());
        boolean isGunu = gunler.isEmpty() || gunler.size() < 3
                ? k.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY && k.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY
                : gunler.contains(gun);
        if (!isGunu) return false;
        int simdi = k.get(Calendar.HOUR_OF_DAY) * 60 + k.get(Calendar.MINUTE);
        return simdi >= dakika(t.getString("mesaiBas", "09:00"), 540) && simdi <= dakika(t.getString("mesaiBit", "18:30"), 1110);
    }
}
