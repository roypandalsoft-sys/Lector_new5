package com.roypandal.lectornotificaciones;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

public final class Prefs {
    private static final String PREF = "lector_prefs";
    private static final String APPS = "selected_apps";
    private static final String PAYMENTS = "payments";
    private static final String SPEAKER_KEEPALIVE = "speaker_keepalive";

    private Prefs(){}

    public static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static Set<String> getSelectedApps(Context c) {
        return new HashSet<>(sp(c).getStringSet(APPS, new HashSet<>()));
    }

    public static void saveSelectedApps(Context c, Set<String> set) {
        sp(c).edit().putStringSet(APPS, new HashSet<>(set)).apply();
    }

    public static float getSpeechRate(Context c) {
        return sp(c).getFloat("speech_rate", 1.0f);
    }

    public static void setSpeechRate(Context c, float value) {
        sp(c).edit().putFloat("speech_rate", value).apply();
    }

    public static int getVolume(Context c) {
        return sp(c).getInt("volume", 80);
    }

    public static void setVolume(Context c, int value) {
        sp(c).edit().putInt("volume", value).apply();
    }

    public static boolean isSpeakerKeepAlive(Context c) {
        return sp(c).getBoolean(SPEAKER_KEEPALIVE, false);
    }

    public static void setSpeakerKeepAlive(Context c, boolean enabled) {
        sp(c).edit().putBoolean(SPEAKER_KEEPALIVE, enabled).apply();
    }

    public static synchronized void addPayment(Context c, double amount) { addPayment(c, amount, ""); }

    public static synchronized void addPayment(Context c, double amount, String name) {
        try {
            JSONArray old = new JSONArray(sp(c).getString(PAYMENTS, "[]"));
            JSONArray fresh = new JSONArray();
            String today = dayKey(System.currentTimeMillis());

            for (int i=0; i<old.length(); i++) {
                JSONObject o = old.optJSONObject(i);
                if (o != null && today.equals(dayKey(o.optLong("time")))) fresh.put(o);
            }

            JSONObject item = new JSONObject();
            item.put("amount", amount);
            item.put("name", name == null ? "" : name);
            item.put("time", System.currentTimeMillis());
            fresh.put(item);
            sp(c).edit().putString(PAYMENTS, fresh.toString()).apply();
        } catch(Exception ignored) {}
    }

    public static synchronized JSONArray getTodayPayments(Context c) {
        JSONArray fresh = new JSONArray();
        try {
            JSONArray old = new JSONArray(sp(c).getString(PAYMENTS, "[]"));
            String today = dayKey(System.currentTimeMillis());
            for (int i=0; i<old.length(); i++) {
                JSONObject o = old.optJSONObject(i);
                if (o != null && today.equals(dayKey(o.optLong("time")))) fresh.put(o);
            }
            sp(c).edit().putString(PAYMENTS, fresh.toString()).apply();
        } catch(Exception ignored) {}
        return fresh;
    }

    private static String dayKey(long time) {
        return new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date(time));
    }
}
