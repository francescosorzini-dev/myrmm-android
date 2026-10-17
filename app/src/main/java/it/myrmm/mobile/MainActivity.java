package it.myrmm.mobile;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Schermata unica: configurazione, stato dell'ultimo invio, amministratore e risparmio batteria. */
public class MainActivity extends Activity {
    private EditText url, token, name, every;
    private CheckBox wipe;
    private TextView status, admin, update;
    private Button updateBtn;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView label(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setPadding(0, dp(12), 0, dp(2));
        return v;
    }

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        return e;
    }

    private Button button(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(18), dp(18), dp(28));
        sv.addView(col);

        TextView title = new TextView(this);
        title.setText("myrmm");
        title.setTextSize(26);
        col.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Il telefono si segnala alla console a intervalli. Versione " + Config.APP_VERSION);
        col.addView(sub);

        col.addView(label("Indirizzo del server (https://…/index.php)"));
        url = field("https://rmm.esempio.it/index.php", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        col.addView(url);
        col.addView(label("Token del cliente"));
        token = field("st-…", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        col.addView(token);
        col.addView(label("Nome del telefono"));
        name = field("Telefono di prova", InputType.TYPE_CLASS_TEXT);
        col.addView(name);
        col.addView(label("Ogni quanti minuti (minimo " + Config.MIN_INTERVAL + ")"));
        every = field("60", InputType.TYPE_CLASS_NUMBER);
        col.addView(every);
        wipe = new CheckBox(this);
        wipe.setText("Consenti il ripristino (wipe) richiesto dalla console");
        col.addView(wipe);

        col.addView(button("Salva e avvia", new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        }));
        col.addView(button("Invia adesso", new View.OnClickListener() {
            @Override public void onClick(View v) { sendNow(); }
        }));

        col.addView(label("Blocco schermo e ripristino da remoto"));
        admin = new TextView(this);
        col.addView(admin);
        col.addView(button("Attiva amministratore del dispositivo", new View.OnClickListener() {
            @Override public void onClick(View v) { askAdmin(); }
        }));
        col.addView(button("Disattiva amministratore", new View.OnClickListener() {
            @Override public void onClick(View v) { dropAdmin(); }
        }));

        col.addView(label("Risparmio batteria"));
        col.addView(button("Escludi l'app dal risparmio batteria", new View.OnClickListener() {
            @Override public void onClick(View v) { battery(); }
        }));

        update = new TextView(this);
        update.setPadding(0, dp(12), 0, 0);
        col.addView(update);
        updateBtn = button("Scarica l'aggiornamento", new View.OnClickListener() {
            @Override public void onClick(View v) { download(); }
        });
        col.addView(updateBtn);

        col.addView(label("Ultimo invio"));
        status = new TextView(this);
        col.addView(status);
        setContentView(sv);

        load();
        link(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        link(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** Link di configurazione myrmm://setup?u=…&t=…&n=…&i=…: precompila i campi, poi si preme Salva. */
    private void link(Intent i) {
        Uri d = i == null ? null : i.getData();
        if (d == null || !"myrmm".equals(d.getScheme())) return;
        String u = d.getQueryParameter("u"), t = d.getQueryParameter("t"), n = d.getQueryParameter("n"), m = d.getQueryParameter("i");
        if (u != null) url.setText(u);
        if (t != null) token.setText(t);
        if (n != null) name.setText(n);
        if (m != null) every.setText(m);
        Toast.makeText(this, "Dati caricati dal link: controlla e premi «Salva e avvia»", Toast.LENGTH_LONG).show();
    }

    private void load() {
        url.setText(Config.url(this));
        token.setText(Config.token(this));
        name.setText(Config.name(this));
        every.setText(String.valueOf(Config.every(this)));
        wipe.setChecked(Config.allowWipe(this));
    }

    private void save() {
        String u = url.getText().toString().trim();
        String t = token.getText().toString().trim();
        if (!u.toLowerCase().startsWith("https://")) {
            Toast.makeText(this, "L'indirizzo deve iniziare con https://", Toast.LENGTH_LONG).show();
            return;
        }
        if (t.isEmpty()) {
            Toast.makeText(this, "Serve il token del cliente", Toast.LENGTH_LONG).show();
            return;
        }
        int m = 60;
        try { m = Integer.parseInt(every.getText().toString().trim()); } catch (Exception ignore) { /* predefinito */ }
        m = Math.max(Config.MIN_INTERVAL, Math.min(1440, m));
        every.setText(String.valueOf(m));
        Config.save(this, u, t, name.getText().toString().trim(), m, wipe.isChecked());
        Sched.start(this);
        Toast.makeText(this, "Salvato: il primo invio parte adesso", Toast.LENGTH_SHORT).show();
        ui.postDelayed(new Runnable() { @Override public void run() { refresh(); } }, 4000);
    }

    private void sendNow() {
        if (!Config.configured(this)) {
            Toast.makeText(this, "Salva prima le impostazioni", Toast.LENGTH_SHORT).show();
            return;
        }
        status.setText("invio in corso…");
        final android.content.Context ctx = getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                Checkin.run(ctx);
                ui.post(new Runnable() { @Override public void run() { refresh(); } });
            }
        }).start();
    }

    private void refresh() {
        String s = Config.status(this);
        status.setText(s.isEmpty() ? "nessun invio ancora" : Checkin.when(Config.statusAt(this)) + " · " + s);
        String uv = Config.updateVer(this);
        boolean has = !uv.isEmpty() && Config.newer(uv, Config.APP_VERSION) && Config.updateUrl(this).toLowerCase().startsWith("https://");
        update.setText(has ? "Aggiornamento disponibile: " + uv + " (installata " + Config.APP_VERSION + "). Scaricalo e installalo sopra questa versione." : "");
        update.setVisibility(has ? View.VISIBLE : View.GONE);
        updateBtn.setVisibility(has ? View.VISIBLE : View.GONE);
        admin.setText(Checkin.admin(this) ? "Amministratore attivo: la console può bloccare lo schermo." : "Amministratore non attivo: blocco e ripristino non disponibili.");
    }

    private void askAdmin() {
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, new ComponentName(this, AdminReceiver.class));
        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Permette alla console myrmm di bloccare lo schermo e, se lo consenti nell'app, di ripristinare il telefono.");
        startActivity(i);
    }

    private void dropAdmin() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        if (dpm != null) dpm.removeActiveAdmin(new ComponentName(this, AdminReceiver.class));
        refresh();
    }

    private void download() {
        String u = Config.updateUrl(this);
        if (!u.toLowerCase().startsWith("https://")) return;
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
    }

    private void battery() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, "Già escluso dal risparmio batteria", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }
}
