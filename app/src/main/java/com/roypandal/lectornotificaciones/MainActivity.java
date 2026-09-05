package com.roypandal.lectornotificaciones;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout paymentsBox;
    private TextView totalText, selectedText, statusText, speakerStatusText;
    private Button speakerButton;
    private TextToSpeech testTts;
    private BroadcastReceiver paymentReceiver;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        testTts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) testTts.setLanguage(new Locale("es","PE"));
        });
        requestNotificationPermissionIfNeeded();
        refreshAll();

        paymentReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { refreshPayments(); }
        };
    }

    @Override protected void onResume() {
        super.onResume();
        refreshAll();
        IntentFilter f = new IntentFilter(NotificationReaderService.ACTION_PAYMENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(paymentReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(paymentReceiver, f);
    }

    @Override protected void onPause() {
        try { unregisterReceiver(paymentReceiver); } catch(Exception ignored) {}
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (testTts != null) { testTts.stop(); testTts.shutdown(); }
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(root);

        TextView title = text("LECTOR DE NOTIFICACIONES", 24, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(10), 0, dp(18));
        root.addView(title, matchWrap());

        Button apps = button("Aplicaciones");
        apps.setOnClickListener(v -> chooseApps());
        root.addView(apps, matchWrap());

        selectedText = text("Aplicaciones seleccionadas: ninguna", 14, false);
        selectedText.setPadding(dp(4), dp(8), dp(4), dp(12));
        root.addView(selectedText, matchWrap());

        statusText = text("", 14, true);
        statusText.setPadding(dp(4), dp(4), dp(4), dp(10));
        root.addView(statusText, matchWrap());

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);

        Button voice = button("Probar voz");
        voice.setOnClickListener(v -> speakTest("Prueba de voz. Lector de notificaciones funcionando."));
        buttons.addView(voice, matchWrap());

        Button notif = button("Notificación de prueba");
        notif.setOnClickListener(v -> showTestNotification());
        buttons.addView(notif, matchWrap());

        Button check = button("Comprobar funcionamiento");
        check.setOnClickListener(v -> checkFunction());
        buttons.addView(check, matchWrap());
        root.addView(buttons, matchWrap());

        speakerButton = button("");
        speakerButton.setOnClickListener(v -> {
            boolean enabled = !Prefs.isSpeakerKeepAlive(MainActivity.this);
            Prefs.setSpeakerKeepAlive(MainActivity.this, enabled);
            updateSpeakerUi();
            sendBroadcast(new Intent(NotificationReaderService.ACTION_SPEAKER_STATE).setPackage(getPackageName()));
            Toast.makeText(MainActivity.this, enabled
                    ? "Parlante activo: pip de 700 ms cada 2 minutos"
                    : "Mantenimiento del parlante desactivado", Toast.LENGTH_SHORT).show();
        });
        root.addView(speakerButton, matchWrap());

        speakerStatusText = text("", 14, true);
        speakerStatusText.setGravity(Gravity.CENTER);
        speakerStatusText.setPadding(dp(4), dp(4), dp(4), dp(8));
        root.addView(speakerStatusText, matchWrap());
        updateSpeakerUi();

        root.addView(section("Velocidad de voz"));
        SeekBar speed = new SeekBar(this);
        speed.setMax(160);
        speed.setProgress(Math.round((Prefs.getSpeechRate(this)-0.5f)*100));
        speed.setOnSeekBarChangeListener(new SimpleSeek() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                Prefs.setSpeechRate(MainActivity.this, 0.5f + p/100f);
            }
        });
        root.addView(speed, matchWrap());

        root.addView(section("Volumen de voz"));
        SeekBar volume = new SeekBar(this);
        volume.setMax(100);
        volume.setProgress(Prefs.getVolume(this));
        volume.setOnSeekBarChangeListener(new SimpleSeek() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                Prefs.setVolume(MainActivity.this, p);
            }
        });
        root.addView(volume, matchWrap());

        TextView payTitle = section("PAGOS DE HOY");
        payTitle.setTextSize(20);
        root.addView(payTitle);

        paymentsBox = new LinearLayout(this);
        paymentsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(paymentsBox, matchWrap());

        totalText = text("TOTAL: S/ 0.00", 20, true);
        totalText.setPadding(dp(4), dp(14), dp(4), dp(20));
        root.addView(totalText, matchWrap());

        TextView footer = text("Diseñado Por Roy Pandal", 15, true);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(20), 0, dp(8));
        root.addView(footer, matchWrap());

        setContentView(scroll);
    }

    private void updateSpeakerUi() {
        if (speakerButton == null || speakerStatusText == null) return;
        boolean enabled = Prefs.isSpeakerKeepAlive(this);
        if (enabled) {
            speakerButton.setText("🔊  PARLANTE ACTIVO");
            speakerButton.setBackgroundColor(android.graphics.Color.rgb(46, 160, 67));
            speakerButton.setTextColor(android.graphics.Color.WHITE);
            speakerStatusText.setText("🔊 Manteniendo conexión • pip de 700 ms cada 2 min");
            speakerStatusText.setTextColor(android.graphics.Color.rgb(32, 120, 52));
        } else {
            speakerButton.setText("🔈  ACTIVAR PARLANTE");
            speakerButton.setBackgroundColor(android.graphics.Color.rgb(210, 210, 210));
            speakerButton.setTextColor(android.graphics.Color.BLACK);
            speakerStatusText.setText("Parlante en espera");
            speakerStatusText.setTextColor(android.graphics.Color.DKGRAY);
        }
    }

    private void refreshAll() {
        refreshSelected();
        refreshPayments();
        updateSpeakerUi();
        statusText.setText(isNotificationAccessEnabled()
                ? "✓ Acceso a notificaciones activado"
                : "✗ Falta activar acceso a notificaciones");
    }

    private void chooseApps() {
        PackageManager pm = getPackageManager();

        // Mostrar TODAS las aplicaciones instaladas, incluidas las de sistema
        // y las que no tienen icono en el lanzador.
        List<ApplicationInfo> list;
        if (Build.VERSION.SDK_INT >= 33) {
            list = pm.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS));
        } else {
            list = pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS);
        }

        TreeMap<String,String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ApplicationInfo ai : list) {
            if (ai == null) continue;
            String pkg = ai.packageName;
            if (pkg == null || pkg.equals(getPackageName())) continue;
            CharSequence loadedLabel = pm.getApplicationLabel(ai);
            String label = loadedLabel == null || loadedLabel.length() == 0 ? pkg : loadedLabel.toString();
            sorted.put(label + "  [" + pkg + "]", pkg);
        }

        String[] names = sorted.keySet().toArray(new String[0]);
        String[] pkgs = sorted.values().toArray(new String[0]);
        Set<String> selected = Prefs.getSelectedApps(this);
        boolean[] checked = new boolean[names.length];
        for (int i=0;i<pkgs.length;i++) checked[i] = selected.contains(pkgs[i]);

        new AlertDialog.Builder(this)
                .setTitle("Escoger aplicaciones a leer")
                .setMultiChoiceItems(names, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("GRABAR", (d,w) -> {
                    Set<String> out = new HashSet<>();
                    for (int i=0;i<pkgs.length;i++) if (checked[i]) out.add(pkgs[i]);
                    Prefs.saveSelectedApps(this, out);
                    refreshSelected();
                    Toast.makeText(this, "Selección grabada", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void refreshSelected() {
        Set<String> selected = Prefs.getSelectedApps(this);
        if (selected.isEmpty()) {
            selectedText.setText("Aplicaciones seleccionadas: ninguna");
            return;
        }
        PackageManager pm = getPackageManager();
        ArrayList<String> labels = new ArrayList<>();
        for (String pkg : selected) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                labels.add(String.valueOf(pm.getApplicationLabel(ai)));
            } catch(Exception e) { labels.add(pkg); }
        }
        Collections.sort(labels, String.CASE_INSENSITIVE_ORDER);
        selectedText.setText("Aplicaciones seleccionadas: " + TextUtils.join(", ", labels));
    }

    private void refreshPayments() {
        paymentsBox.removeAllViews();
        JSONArray arr = Prefs.getTodayPayments(this);
        double total = 0;
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

        if (arr.length() > 0) {
            LinearLayout header = new LinearLayout(this);
            header.setOrientation(LinearLayout.HORIZONTAL);
            TextView hn = text("NOMBRE", 13, true);
            TextView hm = text("MONTO", 13, true); hm.setGravity(Gravity.END);
            TextView hh = text("HORA", 13, true); hh.setGravity(Gravity.END);
            header.addView(hn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.9f));
            header.addView(hm, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f));
            header.addView(hh, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.75f));
            header.setPadding(dp(4), dp(6), dp(4), dp(6));
            paymentsBox.addView(header, matchWrap());
        }

        for (int i=0; i<arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            double amount = o.optDouble("amount");
            long time = o.optLong("time");
            String name = PaymentParser.cleanName(o.optString("name", ""));
            if (name.isEmpty()) name = "CLIENTE";
            total += amount;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(8), dp(4), dp(8));

            TextView nameView = text(name, 15, false);
            nameView.setMaxLines(2);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            TextView amountView = text(String.format(Locale.US, "S/ %.2f", amount), 15, true);
            amountView.setGravity(Gravity.END);
            TextView timeView = text(timeFmt.format(new Date(time)), 14, false);
            timeView.setGravity(Gravity.END);

            row.addView(nameView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.9f));
            row.addView(amountView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f));
            row.addView(timeView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.75f));
            paymentsBox.addView(row, matchWrap());
        }

        if (arr.length() == 0) {
            TextView empty = text("Aún no hay pagos registrados hoy.", 15, false);
            empty.setPadding(dp(4), dp(8), dp(4), dp(8));
            paymentsBox.addView(empty, matchWrap());
        }
        totalText.setText(String.format(Locale.US, "TOTAL DEL DÍA: S/ %.2f", total));
        totalText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    }

    private void speakTest(String s) {
        if (testTts == null) return;
        testTts.setSpeechRate(Prefs.getSpeechRate(this));
        AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
        if (am != null) {
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC,
                    Math.round(max * Prefs.getVolume(this)/100f), 0);
        }
        testTts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "test");
    }

    private void showTestNotification() {
        final String channelId = "test_channel";
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(channelId,
                    "Pruebas", NotificationManager.IMPORTANCE_HIGH));
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Notificación de prueba")
                .setContentText("Esta es una notificación de prueba.")
                .setAutoCancel(true);
        nm.notify(1001, b.build());

        // La notificación de prueba pertenece a esta misma app y no forma parte
        // de la lista de aplicaciones seleccionables. Por eso la leemos
        // directamente, respetando el mismo retraso de 2 segundos.
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> speakTest("Esta es una notificación de prueba."), 2000);

        Toast.makeText(this, "Notificación de prueba enviada", Toast.LENGTH_SHORT).show();
    }

    private void checkFunction() {
        if (!isNotificationAccessEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("Falta un permiso")
                    .setMessage("Activa “Lector de Notificaciones” en Acceso a notificaciones.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("ABRIR AJUSTES", (d,w) ->
                            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")))
                    .show();
            return;
        }
        if (Prefs.getSelectedApps(this).isEmpty()) {
            Toast.makeText(this, "El servicio está activo, pero debes escoger al menos una aplicación.", Toast.LENGTH_LONG).show();
            return;
        }
        speakTest("Comprobación correcta. El lector está funcionando.");
        Toast.makeText(this, "✓ Permiso activo y aplicaciones grabadas.", Toast.LENGTH_LONG).show();
    }

    private boolean isNotificationAccessEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(getPackageName());
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 70);
        }
    }

    private TextView section(String s) {
        TextView t = text(s, 17, true);
        t.setPadding(dp(4), dp(18), dp(4), dp(6));
        return t;
    }

    private TextView text(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setMinHeight(dp(52));
        return b;
    }

    private ViewGroup.LayoutParams matchWrap() {
        return new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int x) {
        return Math.round(x * getResources().getDisplayMetrics().density);
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        public void onStartTrackingTouch(SeekBar s) {}
        public void onStopTrackingTouch(SeekBar s) {}
    }
}
