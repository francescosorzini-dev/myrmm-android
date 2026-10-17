package it.myrmm.mobile;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Locale;
import java.util.TimeZone;

/** Dati di inventario del telefono. Ogni voce è indipendente: se una non è leggibile viene saltata. */
final class Details {
    private Details() {}

    /** Valori che cambiano spesso: vanno a ogni check-in (poche decine di byte). */
    static void live(Context c, JSONObject o) {
        try { o.put("uptime_h", Math.round(SystemClock.elapsedRealtime() / 360000.0) / 10.0); } catch (Exception ignore) { }
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            if (mi.totalMem > 0) {
                o.put("ram_gb", Math.round(mi.totalMem / 1073741824.0 * 10.0) / 10.0);
                o.put("ram_pct", Math.round((mi.totalMem - mi.availMem) * 100.0 / mi.totalMem));
            }
        } catch (Exception ignore) { }
        try {
            Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                int t = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                if (t > 0) o.put("batt_temp", t / 10.0);
                o.put("batt_health", health(b.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)));
            }
        } catch (Exception ignore) { }
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
            if (nc == null) o.put("net", "nessuna");
            else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) o.put("net", "Wi-Fi");
            else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) o.put("net", "dati mobili");
            else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) o.put("net", "Ethernet");
            else if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) o.put("net", "VPN");
            else o.put("net", "altra");
        } catch (Exception ignore) { }
    }

    /** Dati stabili e elenco app: ogni qualche ora. */
    static JSONObject full(Context c) {
        JSONObject d = new JSONObject();
        try {
            d.put("sdk", Build.VERSION.SDK_INT);
            d.put("build", Build.DISPLAY);
            d.put("fingerprint", Build.FINGERPRINT);
            d.put("brand", Build.BRAND);
            d.put("device", Build.DEVICE);
            d.put("board", Build.BOARD);
            d.put("hardware", Build.HARDWARE);
            d.put("abi", Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "");
            d.put("cores", Runtime.getRuntime().availableProcessors());
            d.put("kernel", System.getProperty("os.version", ""));
            d.put("locale", Locale.getDefault().toLanguageTag());
            d.put("tz", TimeZone.getDefault().getID());
        } catch (Exception ignore) { }
        try {
            DisplayMetrics m = c.getResources().getDisplayMetrics();
            d.put("screen", m.widthPixels + "x" + m.heightPixels + " @" + m.densityDpi + "dpi");
        } catch (Exception ignore) { }
        try {
            TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
            String op = tm == null ? "" : tm.getNetworkOperatorName();
            if (op != null && !op.isEmpty()) d.put("carrier", op);
        } catch (Exception ignore) { }
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
            int s = dpm.getStorageEncryptionStatus();
            d.put("encrypted", s == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE
                    || s == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER
                    || s == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY ? 1 : 0);
        } catch (Exception ignore) { }
        try {
            KeyguardManager km = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
            d.put("screen_lock", km.isDeviceSecure() ? 1 : 0);
        } catch (Exception ignore) { }
        try { d.put("adb", Settings.Global.getInt(c.getContentResolver(), Settings.Global.ADB_ENABLED, 0)); } catch (Exception ignore) { }
        try { d.put("dev_options", Settings.Global.getInt(c.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)); } catch (Exception ignore) { }
        try {
            String[] su = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/system/app/Superuser.apk"};
            boolean root = false;
            for (String p : su) if (new File(p).exists()) { root = true; break; }
            d.put("root_hint", root ? 1 : 0);
        } catch (Exception ignore) { }
        try {
            PackageManager pm = c.getPackageManager();
            JSONArray apps = new JSONArray();
            int sys = 0, total = 0;
            for (PackageInfo pi : pm.getInstalledPackages(0)) {
                if (pi.applicationInfo == null) continue;
                total++;
                if ((pi.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) { sys++; continue; }
                if (apps.length() >= 400) continue;
                JSONObject a = new JSONObject();
                String label = String.valueOf(pm.getApplicationLabel(pi.applicationInfo));
                a.put("name", label);
                a.put("version", pi.versionName == null ? "" : pi.versionName);
                a.put("pkg", pi.packageName);
                a.put("installed", new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new java.util.Date(pi.firstInstallTime)));
                apps.put(a);
            }
            d.put("apps", apps);
            d.put("apps_system", sys);
            d.put("apps_total", total);
        } catch (Exception ignore) { }
        return d;
    }

    private static String health(int h) {
        switch (h) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "buona";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "surriscaldata";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "esaurita";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "sovratensione";
            case BatteryManager.BATTERY_HEALTH_COLD: return "troppo fredda";
            default: return "sconosciuta";
        }
    }
}
