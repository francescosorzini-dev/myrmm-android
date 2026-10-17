package it.myrmm.mobile;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Un check-in: invia inventario e stato, esegue i comandi della console e ne riporta l'esito. */
final class Checkin {
    private Checkin() {}

    /** @return testo dell'esito (vuoto = ok). Mai eccezioni verso il chiamante. */
    static synchronized String run(Context c) {
        String msg;
        try {
            if (!Config.configured(c)) { msg = "non configurato"; Config.setStatus(c, msg); return msg; }
            String base = Config.url(c);
            if (!base.toLowerCase(Locale.ROOT).startsWith("https://")) {
                msg = "serve un indirizzo https://";
                Config.setStatus(c, msg);
                return msg;
            }
            JSONObject body = info(c);
            JSONObject resp = post(base, body);
            JSONArray cmds = resp.optJSONArray("commands");
            int n = 0;
            if (cmds != null && cmds.length() > 0) {
                JSONArray done = new JSONArray();
                for (int i = 0; i < cmds.length(); i++) {
                    JSONObject cmd = cmds.getJSONObject(i);
                    JSONObject r = new JSONObject();
                    r.put("id", cmd.optInt("id"));
                    String err = exec(c, cmd.optString("type"));
                    r.put("ok", err.isEmpty());
                    r.put("msg", err);
                    done.put(r);
                    n++;
                }
                JSONObject again = info(c);
                again.put("done", done);
                try { post(base, again); } catch (Exception ignore) { /* il prossimo check-in lo ripeterà */ }
            }
            msg = "ok" + (n > 0 ? " · " + n + (n == 1 ? " comando eseguito" : " comandi eseguiti") : "");
            Config.setStatus(c, msg);
            return "";
        } catch (Exception e) {
            msg = "errore: " + e.getClass().getSimpleName() + (e.getMessage() != null ? " " + e.getMessage() : "");
            if (msg.length() > 160) msg = msg.substring(0, 160);
            Config.setStatus(c, msg);
            return msg;
        }
    }

    static JSONObject info(Context c) throws Exception {
        JSONObject o = new JSONObject();
        o.put("token", Config.token(c));
        o.put("id", Config.id(c));
        String name = Config.name(c);
        o.put("name", name.isEmpty() ? Build.MODEL : name);
        o.put("os", "android");
        o.put("os_version", Build.VERSION.RELEASE);
        o.put("manufacturer", Build.MANUFACTURER);
        o.put("model", Build.MODEL);
        o.put("sec_patch", Build.VERSION.SECURITY_PATCH);
        o.put("client", "apk");
        o.put("app_version", Config.APP_VERSION);
        o.put("interval_min", Config.every(c));
        o.put("admin", admin(c));
        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b != null) {
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) o.put("battery", Math.round(level * 100f / scale));
            int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            o.put("charging", (st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL) ? 1 : 0);
        }
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            double gb = 1024.0 * 1024.0 * 1024.0;
            o.put("total_gb", Math.round(fs.getTotalBytes() / gb * 10.0) / 10.0);
            o.put("free_gb", Math.round(fs.getAvailableBytes() / gb * 10.0) / 10.0);
        } catch (Exception ignore) { /* spazio non disponibile */ }
        String ip = localIp(c);
        if (!ip.isEmpty()) o.put("ip", ip);
        return o;
    }

    static boolean admin(Context c) {
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(new ComponentName(c, AdminReceiver.class));
    }

    static String localIp(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm == null ? null : cm.getActiveNetwork();
            LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
            if (lp == null) return "";
            for (LinkAddress la : lp.getLinkAddresses()) {
                InetAddress a = la.getAddress();
                if (!a.isLoopbackAddress() && !a.isLinkLocalAddress() && a.getAddress().length == 4) return a.getHostAddress();
            }
        } catch (Exception ignore) { /* nessuna rete */ }
        return "";
    }

    /** @return stringa vuota se riuscito, altrimenti il motivo. */
    static String exec(Context c, String type) {
        if ("ping".equals(type) || "checkin".equals(type)) return "";
        DevicePolicyManager dpm = (DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName cn = new ComponentName(c, AdminReceiver.class);
        if (dpm == null || !dpm.isAdminActive(cn)) return "amministratore del dispositivo non attivo";
        try {
            if ("lock".equals(type)) { dpm.lockNow(); return ""; }
            if ("wipe".equals(type)) {
                if (!Config.allowWipe(c)) return "ripristino remoto non consentito sul telefono";
                dpm.wipeData(0);
                return "";
            }
        } catch (Exception e) {
            return e.getClass().getSimpleName() + (e.getMessage() != null ? " " + e.getMessage() : "");
        }
        return "comando sconosciuto";
    }

    static JSONObject post(String base, JSONObject body) throws Exception {
        String u = base + (base.contains("?") ? "&" : "?") + "action=mobile_checkin";
        HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
        try {
            h.setConnectTimeout(15000);
            h.setReadTimeout(20000);
            h.setRequestMethod("POST");
            h.setDoOutput(true);
            h.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream os = h.getOutputStream();
            os.write(data);
            os.close();
            int code = h.getResponseCode();
            InputStream in = code >= 400 ? h.getErrorStream() : h.getInputStream();
            StringBuilder sb = new StringBuilder();
            if (in != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null && sb.length() < 200000) sb.append(line);
                br.close();
            }
            if (code >= 400) {
                String why = sb.toString();
                try { why = new JSONObject(why).optString("error", why); } catch (Exception ignore) { /* non JSON */ }
                throw new Exception("HTTP " + code + " " + (why.length() > 80 ? why.substring(0, 80) : why));
            }
            return new JSONObject(sb.toString());
        } finally {
            h.disconnect();
        }
    }

    static String when(long ms) {
        if (ms <= 0) return "mai";
        return new SimpleDateFormat("dd/MM HH:mm:ss", Locale.ITALY).format(new Date(ms));
    }
}
