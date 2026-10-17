package it.myrmm.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/** Impostazioni del telefono: indirizzo del server, token del cliente, nome, intervallo. */
final class Config {
    static final String APP_VERSION = "1.2.1";
    static final int MIN_INTERVAL = 15; // minuti: il minimo di JobScheduler per i lavori periodici

    private Config() {}

    static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("myrmm", Context.MODE_PRIVATE);
    }

    static String url(Context c) { return p(c).getString("url", ""); }
    static String token(Context c) { return p(c).getString("token", ""); }
    static String name(Context c) { return p(c).getString("name", ""); }
    static int every(Context c) { return Math.max(MIN_INTERVAL, Math.min(1440, p(c).getInt("every", 60))); }
    static boolean allowWipe(Context c) { return p(c).getBoolean("wipe", false); }
    static boolean configured(Context c) { return !url(c).isEmpty() && !token(c).isEmpty(); }

    /** Identificativo fisso del telefono (8-64 caratteri), creato al primo uso. */
    static String id(Context c) {
        SharedPreferences sp = p(c);
        String id = sp.getString("id", "");
        if (id.isEmpty()) {
            id = "apk-" + stableSuffix(c);
            sp.edit().putString("id", id).apply();
        }
        return id;
    }

    /**
     * Derivato dall'Android ID: dopo una disinstallazione e reinstallazione il telefono ritrova lo stesso device nella console;
     * dopo un ripristino di fabbrica l'Android ID cambia e il telefono risulta nuovo. Senza Android ID (o con il valore noto
     * difettoso) si usa un codice casuale.
     */
    private static String stableSuffix(Context c) {
        try {
            String aid = android.provider.Settings.Secure.getString(c.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
            if (aid != null && aid.length() >= 8 && !"9774d56d682e549c".equals(aid)) {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] h = md.digest(("myrmm|" + c.getPackageName() + "|" + aid).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < 16; i++) b.append(String.format(java.util.Locale.ROOT, "%02x", h[i]));
                return b.toString();
            }
        } catch (Exception ignore) { /* si ripiega sul casuale */ }
        return UUID.randomUUID().toString().replace("-", "");
    }

    static void save(Context c, String url, String token, String name, int every, boolean wipe) {
        p(c).edit().putString("url", url).putString("token", token).putString("name", name)
                .putInt("every", every).putBoolean("wipe", wipe).apply();
    }

    /** Versione dell'app pubblicata dal server (se più recente di questa compare l'avviso di aggiornamento). */
    static void setUpdate(Context c, String ver, String url) {
        p(c).edit().putString("apk_ver", ver).putString("apk_url", url).apply();
    }

    static String updateVer(Context c) { return p(c).getString("apk_ver", ""); }
    static String updateUrl(Context c) { return p(c).getString("apk_url", ""); }

    /** true se a è una versione più recente di b (confronto numerico per parti: 1.10.0 > 1.9.0). */
    static boolean newer(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static int num(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    static void setStatus(Context c, String s) {
        p(c).edit().putString("status", s).putLong("status_at", System.currentTimeMillis()).apply();
    }

    static long detailsAt(Context c) { return p(c).getLong("details_at", 0); }
    static void setDetailsAt(Context c, long t) { p(c).edit().putLong("details_at", t).apply(); }
    static String status(Context c) { return p(c).getString("status", ""); }
    static long statusAt(Context c) { return p(c).getLong("status_at", 0); }
}
