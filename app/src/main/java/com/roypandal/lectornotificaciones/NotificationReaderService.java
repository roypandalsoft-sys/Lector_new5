package com.roypandal.lectornotificaciones;

import android.app.Notification;
import android.content.*;
import android.media.*;
import android.os.*;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import java.util.*;

public class NotificationReaderService extends NotificationListenerService implements TextToSpeech.OnInitListener {
    public static final String ACTION_PAYMENT = "com.roypandal.lectornotificaciones.PAYMENT_ADDED";
    public static final String ACTION_SPEAKER_STATE = "com.roypandal.lectornotificaciones.SPEAKER_STATE";
    private static final long KEEP_ALIVE_MS = 2 * 60 * 1000L;
    private TextToSpeech tts;
    private boolean ready = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable keepAliveRunnable = new Runnable() {
        @Override public void run() {
            if (!Prefs.isSpeakerKeepAlive(NotificationReaderService.this)) return;
            playSoftTick();
            handler.postDelayed(this, KEEP_ALIVE_MS);
        }
    };

    private final BroadcastReceiver speakerReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { resetKeepAliveTimer(); }
    };

    @Override public void onCreate() {
        super.onCreate();
        tts = new TextToSpeech(this, this);
        IntentFilter f = new IntentFilter(ACTION_SPEAKER_STATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(speakerReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(speakerReceiver, f);
        resetKeepAliveTimer();
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        resetKeepAliveTimer();
    }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(new Locale("es", "PE"));
            ready = true;
        }
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        Set<String> selected = Prefs.getSelectedApps(this);
        if (!selected.contains(sbn.getPackageName())) return;

        Notification n = sbn.getNotification();
        Bundle e = n.extras;
        String title = safe(e.getCharSequence(Notification.EXTRA_TITLE));
        String text = safe(e.getCharSequence(Notification.EXTRA_TEXT));
        String big = safe(e.getCharSequence(Notification.EXTRA_BIG_TEXT));
        String body = join(title, !big.isEmpty() ? big : text);
        if (body.trim().isEmpty()) return;

        String pkg = sbn.getPackageName();
        boolean isYape = pkg != null && (pkg.equalsIgnoreCase("com.bcp.innovacxion.yapeapp") || pkg.toLowerCase(Locale.ROOT).contains("yape"));
        PaymentParser.Result result = isYape
                ? PaymentParser.parseYape(title, !big.isEmpty() ? big : text)
                : PaymentParser.parse(body);
        final String phrase;
        if (result.isPayment) {
            String cleanName = PaymentParser.cleanName(result.customerName);
            if (isYape && !cleanName.isEmpty())
                phrase = cleanName + " te envió un pago por " + moneySpeech(result.amount);
            else
                phrase = "CLIENTE PAGÓ " + moneySpeech(result.amount);
            Prefs.addPayment(this, result.amount, cleanName);
            sendBroadcast(new Intent(ACTION_PAYMENT).setPackage(getPackageName()));
        } else phrase = body;

        // Una notificación real también mantiene despierto el parlante: reinicia los 2 minutos.
        resetKeepAliveTimer();
        handler.postDelayed(() -> { speak(phrase); resetKeepAliveTimer(); }, 2000);
    }

    private void resetKeepAliveTimer() {
        handler.removeCallbacks(keepAliveRunnable);
        if (Prefs.isSpeakerKeepAlive(this)) handler.postDelayed(keepAliveRunnable, KEEP_ALIVE_MS);
    }

    private void playSoftTick() {
        // Pip real de 700 ms, 900 Hz, amplitud baja (~7%).
        // Se envía por STREAM_MUSIC/Bluetooth para evitar que muchos parlantes entren en autoapagado.
        new Thread(() -> {
            final int sampleRate = 16000;
            final int durationMs = 700;
            final int samples = sampleRate * durationMs / 1000;
            short[] pcm = new short[samples];
            double freq = 900.0;
            double amp = Short.MAX_VALUE * 0.07;
            for (int i = 0; i < samples; i++) {
                double envelope = Math.sin(Math.PI * i / (double)Math.max(1, samples - 1));
                pcm[i] = (short)(Math.sin(2.0 * Math.PI * freq * i / sampleRate) * amp * envelope);
            }
            AudioTrack track = null;
            try {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
                AudioFormat format = new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
                track = new AudioTrack(attrs, format, pcm.length * 2,
                        AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
                track.write(pcm, 0, pcm.length);
                track.play();
                Thread.sleep(durationMs + 80L);
            } catch (Exception ignored) {
            } finally {
                if (track != null) { try { track.stop(); } catch(Exception ignored) {} track.release(); }
            }
        }, "SpeakerKeepAlivePip").start();
    }

    public void speak(String text) {
        if (!ready || tts == null || TextUtils.isEmpty(text)) return;
        tts.setSpeechRate(Prefs.getSpeechRate(this));
        AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
        if (am != null) {
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int target = Math.round(max * (Prefs.getVolume(this) / 100f));
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lector_" + System.currentTimeMillis());
    }

    private static String moneySpeech(double amount) {
        long soles = (long)Math.floor(amount + 0.00001);
        int cents = (int)Math.round((amount - soles) * 100);
        if (cents == 100) { soles++; cents = 0; }
        StringBuilder s = new StringBuilder();
        if (soles > 0) { if (soles == 1) s.append("un sol"); else s.append(soles).append(" soles"); }
        if (cents > 0) { if (s.length() > 0) s.append(" con "); s.append(cents).append(cents == 1 ? " céntimo" : " céntimos"); }
        if (s.length() == 0) s.append("cero soles");
        return s.toString();
    }
    private static String safe(CharSequence x) { return x == null ? "" : x.toString().trim(); }
    private static String join(String a, String b) { if (a.isEmpty()) return b; if (b.isEmpty()) return a; if (b.equalsIgnoreCase(a)) return a; return a + ". " + b; }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try { unregisterReceiver(speakerReceiver); } catch(Exception ignored) {}
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
