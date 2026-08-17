package com.classeschedule.widget;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.widget.RemoteViews;

import java.io.InputStream;
import java.util.*;
import java.text.SimpleDateFormat;

import org.json.JSONArray;
import org.json.JSONObject;

public class ScheduleWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_UPDATE =
            "com.classeschedule.widget.UPDATE";

    private static final long REFRESH_INTERVAL =
            60 * 1000L;

    @Override
    public void onUpdate(
            Context c,
            AppWidgetManager m,
            int[] ids) {

        for (int id : ids) {
            update(c, m, id);
        }

        scheduleRefresh(c);
    }

    @Override
    public void onEnabled(Context c) {
        super.onEnabled(c);
        scheduleRefresh(c);
    }

    @Override
    public void onDisabled(Context c) {
        super.onDisabled(c);
        cancelRefresh(c);
    }

    @Override
    public void onReceive(
            Context c,
            Intent i) {

        super.onReceive(c, i);

        String a = i.getAction();

        if (ACTION_UPDATE.equals(a)
                || Intent.ACTION_TIME_CHANGED.equals(a)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(a)
                || Intent.ACTION_DATE_CHANGED.equals(a)) {

            refreshAll(c);
            scheduleRefresh(c);
        }
    }

    private static void scheduleRefresh(Context c) {

        AlarmManager alarm =
                (AlarmManager) c.getSystemService(
                        Context.ALARM_SERVICE
                );

        if (alarm == null) {
            return;
        }

        Intent intent =
                new Intent(
                        c,
                        ScheduleWidgetProvider.class
                );

        intent.setAction(ACTION_UPDATE);

        PendingIntent pending =
                PendingIntent.getBroadcast(
                        c,
                        1001,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        long now =
                System.currentTimeMillis();

        long first =
                ((now / REFRESH_INTERVAL) + 1)
                        * REFRESH_INTERVAL;

        alarm.setRepeating(
                AlarmManager.RTC_WAKEUP,
                first,
                REFRESH_INTERVAL,
                pending
        );
    }

    private static void cancelRefresh(Context c) {

        AlarmManager alarm =
                (AlarmManager) c.getSystemService(
                        Context.ALARM_SERVICE
                );

        if (alarm == null) {
            return;
        }

        Intent intent =
                new Intent(
                        c,
                        ScheduleWidgetProvider.class
                );

        intent.setAction(ACTION_UPDATE);

        PendingIntent pending =
                PendingIntent.getBroadcast(
                        c,
                        1001,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        alarm.cancel(pending);
    }

    public static void refreshAll(Context c) {

        AppWidgetManager m =
                AppWidgetManager.getInstance(
                        c.getApplicationContext()
                );

        ComponentName cn =
                new ComponentName(
                        c,
                        ScheduleWidgetProvider.class
                );

        for (int id : m.getAppWidgetIds(cn)) {
            update(c, m, id);
        }
    }

    private static int minutes(String s) {

        try {

            s = s.trim().toUpperCase(Locale.US);

            boolean pm = s.contains("PM");
            boolean am = s.contains("AM");

            s = s.replace("AM", "")
                    .replace("PM", "")
                    .trim();

            String[] p = s.split(":");

            int h =
                    Integer.parseInt(
                            p[0].trim()
                    );

            int min =
                    Integer.parseInt(
                            p[1].trim()
                                    .split("\\s+")[0]
                    );

            if (pm && h < 12) {
                h += 12;
            }

            if (am && h == 12) {
                h = 0;
            }

            return h * 60 + min;

        } catch (Exception e) {

            return 9999;
        }
    }

    private static int todayIndex(Calendar now) {

        int dow =
                now.get(Calendar.DAY_OF_WEEK);

        return dow == Calendar.SUNDAY
                ? 6
                : dow - Calendar.MONDAY;
    }

    private static JSONObject currentClass(Context c) {

        try {

            JSONArray a =
                    new JSONArray(
                            c.getSharedPreferences(
                                    "schedule",
                                    0
                            ).getString(
                                    "classes",
                                    "[]"
                            )
                    );

            Calendar now =
                    Calendar.getInstance();

            int today =
                    todayIndex(now);

            int nowMin =
                    now.get(Calendar.HOUR_OF_DAY) * 60
                            + now.get(Calendar.MINUTE);

            for (int i = 0; i < a.length(); i++) {

                JSONObject o =
                        a.getJSONObject(i);

                int d =
                        o.optInt(
                                "day",
                                -1
                        );

                if (d != today) {
                    continue;
                }

                int start =
                        minutes(
                                o.optString(
                                        "start",
                                        ""
                                )
                        );

                int end =
                        minutes(
                                o.optString(
                                        "end",
                                        ""
                                )
                        );

                if (start == 9999 || end == 9999) {
                    continue;
                }

                if (nowMin >= start && nowMin < end) {
                    return o;
                }
            }

        } catch (Exception e) {
            // Ignore invalid schedule data.
        }

        return null;
    }

    private static JSONObject nextClass(
            Context c,
            JSONObject current) {

        try {

            JSONArray a =
                    new JSONArray(
                            c.getSharedPreferences(
                                    "schedule",
                                    0
                            ).getString(
                                    "classes",
                                    "[]"
                            )
                    );

            Calendar now =
                    Calendar.getInstance();

            int today =
                    todayIndex(now);

            int nowMin =
                    now.get(Calendar.HOUR_OF_DAY) * 60
                            + now.get(Calendar.MINUTE);

            JSONObject best = null;

            int bestScore =
                    Integer.MAX_VALUE;

            for (int i = 0; i < a.length(); i++) {

                JSONObject o =
                        a.getJSONObject(i);

                if (current != null
                        && o.toString().equals(
                                current.toString()
                        )) {
                    continue;
                }

                int d =
                        o.optInt(
                                "day",
                                -1
                        );

                int start =
                        minutes(
                                o.optString(
                                        "start",
                                        ""
                                )
                        );

                if (d < 0 || d > 6 || start == 9999) {
                    continue;
                }

                int daysAhead =
                        (d - today + 7) % 7;

                int score =
                        daysAhead * 1440
                                + start
                                - nowMin;

                if (score <= 0) {
                    score += 7 * 1440;
                }

                if (score < bestScore) {

                    bestScore = score;
                    best = o;
                }
            }

            return best;

        } catch (Exception e) {

            return null;
        }
    }

    private static String currentCountdown(
            JSONObject o,
            Calendar now) {

        try {

            int end =
                    minutes(
                            o.optString(
                                    "end",
                                    ""
                            )
                    );

            int nowMin =
                    now.get(Calendar.HOUR_OF_DAY) * 60
                            + now.get(Calendar.MINUTE);

            int remaining =
                    end - nowMin;

            if (end == 9999 || remaining <= 0) {
                return "";
            }

            if (remaining == 1) {
                return "Ends in 1 min";
            }

            return "Ends in "
                    + remaining
                    + " min";

        } catch (Exception e) {

            return "";
        }
    }

    private static String nextCountdown(
            JSONObject o,
            Calendar now) {

        try {

            int d =
                    o.optInt(
                            "day",
                            -1
                    );

            int start =
                    minutes(
                            o.optString(
                                    "start",
                                    ""
                            )
                    );

            if (d < 0 || d > 6 || start == 9999) {
                return "";
            }

            int today =
                    todayIndex(now);

            int nowMin =
                    now.get(Calendar.HOUR_OF_DAY) * 60
                            + now.get(Calendar.MINUTE);

            int daysAhead =
                    (d - today + 7) % 7;

            int difference =
                    daysAhead * 1440
                            + start
                            - nowMin;

            if (difference <= 0) {
                difference += 7 * 1440;
            }

            int days =
                    difference / 1440;

            int hours =
                    (difference % 1440) / 60;

            int mins =
                    difference % 60;

            if (days > 0) {

                if (days == 1) {
                    return "Starts tomorrow";
                }

                return "Starts in "
                        + days
                        + " days";
            }

            if (hours > 0) {

                if (mins > 0) {

                    return "Starts in "
                            + hours
                            + "h "
                            + mins
                            + "m";
                }

                return "Starts in "
                        + hours
                        + "h";
            }

            if (mins <= 1) {
                return "Starts soon";
            }

            return "Starts in "
                    + mins
                    + " min";

        } catch (Exception e) {

            return "";
        }
    }

    private static void setClassViews(
            RemoteViews v,
            int labelId,
            int subjectId,
            int timeId,
            int roomId,
            int teacherId,
            JSONObject o,
            String label,
            String countdown) {

        if (o == null) {

            v.setViewVisibility(
                    labelId,
                    android.view.View.GONE
            );

            v.setViewVisibility(
                    subjectId,
                    android.view.View.GONE
            );

            v.setViewVisibility(
                    timeId,
                    android.view.View.GONE
            );

            v.setViewVisibility(
                    roomId,
                    android.view.View.GONE
            );

            v.setViewVisibility(
                    teacherId,
                    android.view.View.GONE
            );

            return;
        }

        v.setViewVisibility(
                labelId,
                android.view.View.VISIBLE
        );

        v.setViewVisibility(
                subjectId,
                android.view.View.VISIBLE
        );

        v.setViewVisibility(
                timeId,
                android.view.View.VISIBLE
        );

        v.setViewVisibility(
                roomId,
                android.view.View.VISIBLE
        );

        v.setViewVisibility(
                teacherId,
                android.view.View.VISIBLE
        );

        v.setTextViewText(
                labelId,
                label
        );

        v.setTextViewText(
                subjectId,
                o.optString(
                        "subject",
                        "Class"
                )
        );

        String time =
                o.optString(
                        "start",
                        ""
                )
                        + " - "
                        + o.optString(
                                "end",
                                ""
                        );

        if (countdown != null
                && !countdown.isEmpty()) {

            time =
                    time
                            + " • "
                            + countdown;
        }

        v.setTextViewText(
                timeId,
                time
        );

        v.setTextViewText(
                roomId,
                o.optString(
                        "room",
                        ""
                )
        );

        v.setTextViewText(
                teacherId,
                o.optString(
                        "teacher",
                        ""
                )
        );
    }

    static void update(
            Context c,
            AppWidgetManager m,
            int id) {

        RemoteViews v =
                new RemoteViews(
                        c.getPackageName(),
                        R.layout.widget
                );

        Calendar now =
                Calendar.getInstance();

        /*
         * DATE
         */
        SimpleDateFormat dateFormat =
                new SimpleDateFormat(
                        "EEEE • MMM d",
                        Locale.getDefault()
                );

        String currentDate =
                dateFormat.format(
                        now.getTime()
                );

        v.setTextViewText(
                R.id.widget_date,
                currentDate.toUpperCase(
                        Locale.getDefault()
                )
        );

        /*
         * CURRENT AND NEXT CLASS
         */
        JSONObject current =
                currentClass(c);

        JSONObject next =
                nextClass(
                        c,
                        current
                );

        String currentCountdown =
                current != null
                        ? currentCountdown(
                                current,
                                now
                        )
                        : "";

        String nextCountdown =
                next != null
                        ? nextCountdown(
                                next,
                                now
                        )
                        : "";

        setClassViews(
                v,
                R.id.widget_label,
                R.id.widget_subject,
                R.id.widget_time,
                R.id.widget_room,
                R.id.widget_teacher,
                current,
                "CURRENT CLASS",
                currentCountdown
        );

        setClassViews(
                v,
                R.id.widget_next_label,
                R.id.widget_next_subject,
                R.id.widget_next_time,
                R.id.widget_next_room,
                R.id.widget_next_teacher,
                next,
                "NEXT CLASS",
                nextCountdown
        );

        if (current == null && next == null) {

            v.setViewVisibility(
                    R.id.widget_label,
                    android.view.View.VISIBLE
            );

            v.setViewVisibility(
                    R.id.widget_subject,
                    android.view.View.VISIBLE
            );

            v.setViewVisibility(
                    R.id.widget_time,
                    android.view.View.VISIBLE
            );

            v.setTextViewText(
                    R.id.widget_label,
                    "NEXT CLASS"
            );

            v.setTextViewText(
                    R.id.widget_subject,
                    "No classes scheduled"
            );

            v.setTextViewText(
                    R.id.widget_time,
                    "Open app to add your schedule"
            );
        }

        /*
         * WIDGET SETTINGS
         */
        SharedPreferences p =
                c.getSharedPreferences(
                        "widget_settings",
                        0
                );

        int bg =
                p.getInt(
                        "bgColor",
                        Color.rgb(
                                11,
                                11,
                                11
                        )
                );

        int text =
                p.getInt(
                        "textColor",
                        Color.WHITE
                );

        int radius =
                p.getInt(
                        "radius",
                        18
                );

        String uri =
                p.getString(
                        "bgUri",
                        ""
                );

        /*
         * TEXT COLORS
         */
        v.setTextColor(
                R.id.widget_date,
                text
        );

        v.setTextColor(
                R.id.widget_label,
                text
        );

        v.setTextColor(
                R.id.widget_subject,
                text
        );

        v.setTextColor(
                R.id.widget_time,
                text
        );

        v.setTextColor(
                R.id.widget_room,
                text
        );

        v.setTextColor(
                R.id.widget_teacher,
                text
        );

        v.setTextColor(
                R.id.widget_next_label,
                text
        );

        v.setTextColor(
                R.id.widget_next_subject,
                text
        );

        v.setTextColor(
                R.id.widget_next_time,
                text
        );

        v.setTextColor(
                R.id.widget_next_room,
                text
        );

        v.setTextColor(
                R.id.widget_next_teacher,
                text
        );

        /*
         * CORNER STYLE
         */
        int bgRes;

        if (radius <= 0) {

            bgRes =
                    R.drawable.widget_bg_square;

        } else if (radius <= 12) {

            bgRes =
                    R.drawable.widget_bg_slight;

        } else if (radius <= 24) {

            bgRes =
                    R.drawable.widget_bg_round;

        } else {

            bgRes =
                    R.drawable.widget_bg_veryround;
        }

        v.setInt(
                R.id.widget_root,
                "setBackgroundResource",
                bgRes
        );

        v.setInt(
                R.id.widget_root,
                "setBackgroundColor",
                bg
        );

        /*
         * BACKGROUND IMAGE
         */
        if (uri == null || uri.isEmpty()) {

            v.setViewVisibility(
                    R.id.widget_bg_image,
                    android.view.View.GONE
            );

        } else {

            try {

                Uri imageUri;

                if (uri.startsWith("/")) {

                    imageUri =
                            Uri.parse(
                                    "file://" + uri
                            );

                } else {

                    imageUri =
                            Uri.parse(uri);
                }

                InputStream in =
                        c.getContentResolver()
                                .openInputStream(
                                        imageUri
                                );

                if (in == null) {

                    v.setViewVisibility(
                            R.id.widget_bg_image,
                            android.view.View.GONE
                    );

                } else {

                    Bitmap original =
                            BitmapFactory.decodeStream(in);

                    in.close();

                    if (original != null) {

                        int targetSize = 500;

                        float scale =
                                Math.min(
                                        (float) targetSize
                                                / original.getWidth(),
                                        (float) targetSize
                                                / original.getHeight()
                                );

                        Bitmap scaled;

                        if (scale < 1f) {

                            scaled =
                                    Bitmap.createScaledBitmap(
                                            original,
                                            Math.max(
                                                    1,
                                                    (int) (
                                                            original.getWidth()
                                                                    * scale
                                                    )
                                            ),
                                            Math.max(
                                                    1,
                                                    (int) (
                                                            original.getHeight()
                                                                    * scale
                                                    )
                                            ),
                                            true
                                    );

                        } else {

                            scaled = original;
                        }

                        v.setImageViewBitmap(
                                R.id.widget_bg_image,
                                scaled
                        );

                        v.setViewVisibility(
                                R.id.widget_bg_image,
                                android.view.View.VISIBLE
                        );

                        if (scaled != original) {
                            original.recycle();
                        }

                    } else {

                        v.setViewVisibility(
                                R.id.widget_bg_image,
                                android.view.View.GONE
                        );
                    }
                }

            } catch (Exception e) {

                v.setViewVisibility(
                        R.id.widget_bg_image,
                        android.view.View.GONE
                );
            }
        }

        /*
         * OPEN APP WHEN WIDGET IS TAPPED
         */
        Intent x =
                new Intent(
                        c,
                        MainActivity.class
                );

        PendingIntent pnd =
                PendingIntent.getActivity(
                        c,
                        0,
                        x,
                        PendingIntent.FLAG_IMMUTABLE
                                | PendingIntent.FLAG_UPDATE_CURRENT
                );

        v.setOnClickPendingIntent(
                R.id.widget_root,
                pnd
        );

        /*
         * UPDATE WIDGET
         */
        m.updateAppWidget(
                id,
                v
        );
    }
}
