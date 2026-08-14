package com.classeschedule.widget;

import android.appwidget.AppWidgetManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import java.io.InputStream;
import java.util.Calendar;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

public class ScheduleWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_UPDATE =
            "com.classeschedule.widget.UPDATE";

    @Override
    public void onUpdate(
            Context context,
            AppWidgetManager manager,
            int[] appWidgetIds) {

        for (int id : appWidgetIds) {
            update(context, manager, id);
        }
    }

    @Override
    public void onReceive(
            Context context,
            Intent intent) {

        super.onReceive(context, intent);

        String action = intent.getAction();

        if (ACTION_UPDATE.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)) {

            refreshAll(context);
        }
    }

    public static void refreshAll(Context context) {

        Context c = context.getApplicationContext();

        AppWidgetManager manager =
                AppWidgetManager.getInstance(c);

        ComponentName component =
                new ComponentName(
                        c,
                        ScheduleWidgetProvider.class
                );

        int[] ids =
                manager.getAppWidgetIds(component);

        for (int id : ids) {
            update(c, manager, id);
        }
    }

    private static int minutes(String value) {

        try {

            String s =
                    value.trim().toUpperCase(Locale.US);

            boolean pm = s.contains("PM");
            boolean am = s.contains("AM");

            s = s
                    .replace("AM", "")
                    .replace("PM", "")
                    .trim();

            String[] parts = s.split(":");

            int hour =
                    Integer.parseInt(
                            parts[0].trim()
                    );

            int minute =
                    Integer.parseInt(
                            parts[1]
                                    .trim()
                                    .split("\\s+")[0]
                    );

            if (pm && hour < 12) {
                hour += 12;
            }

            if (am && hour == 12) {
                hour = 0;
            }

            return hour * 60 + minute;

        } catch (Exception e) {

            return 9999;
        }
    }

    private static JSONObject next(Context context) {

        try {

            JSONArray classes =
                    new JSONArray(
                            context
                                    .getSharedPreferences(
                                            "schedule",
                                            0
                                    )
                                    .getString(
                                            "classes",
                                            "[]"
                                    )
                    );

            Calendar now =
                    Calendar.getInstance();

            int dayOfWeek =
                    now.get(Calendar.DAY_OF_WEEK);

            int today =
                    dayOfWeek == Calendar.SUNDAY
                            ? 6
                            : dayOfWeek - Calendar.MONDAY;

            int nowMinutes =
                    now.get(Calendar.HOUR_OF_DAY) * 60
                            + now.get(Calendar.MINUTE);

            JSONObject best = null;

            int bestScore =
                    Integer.MAX_VALUE;

            for (int i = 0; i < classes.length(); i++) {

                JSONObject item =
                        classes.getJSONObject(i);

                int day =
                        item.optInt(
                                "day",
                                -1
                        );

                int start =
                        minutes(
                                item.optString(
                                        "start",
                                        ""
                                )
                        );

                if (day < 0
                        || day > 6
                        || start == 9999) {

                    continue;
                }

                int daysAhead =
                        (day - today + 7) % 7;

                int score =
                        daysAhead * 1440
                                + start
                                - nowMinutes;

                if (score <= 0) {
                    score += 7 * 1440;
                }

                if (score < bestScore) {

                    bestScore = score;
                    best = item;
                }
            }

            return best;

        } catch (Exception e) {

            return null;
        }
    }

    private static Bitmap makeRoundedBitmap(
            Bitmap source,
            float radius) {

        if (source == null) {
            return null;
        }

        Bitmap output =
                Bitmap.createBitmap(
                        source.getWidth(),
                        source.getHeight(),
                        Bitmap.Config.ARGB_8888
                );

        Canvas canvas =
                new Canvas(output);

        Paint paint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        RectF rect =
                new RectF(
                        0,
                        0,
                        source.getWidth(),
                        source.getHeight()
                );

        canvas.drawRoundRect(
                rect,
                radius,
                radius,
                paint
        );

        paint.setXfermode(
                new android.graphics.PorterDuffXfermode(
                        android.graphics.PorterDuff.Mode.SRC_IN
                )
        );

        canvas.drawBitmap(
                source,
                0,
                0,
                paint
        );

        paint.setXfermode(null);

        return output;
    }

    private static Bitmap loadBackgroundImage(
            Context context,
            String uriString) {

        if (uriString == null
                || uriString.trim().isEmpty()) {

            return null;
        }

        InputStream input = null;

        try {

            Uri uri;

            if (uriString.startsWith("/")) {

                uri =
                        Uri.parse(
                                "file://" + uriString
                        );

            } else {

                uri =
                        Uri.parse(uriString);
            }

            input =
                    context
                            .getContentResolver()
                            .openInputStream(uri);

            if (input == null) {
                return null;
            }

            Bitmap original =
                    BitmapFactory.decodeStream(input);

            if (original == null) {
                return null;
            }

            int targetSize = 800;

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
                                        (int)
                                                (original.getWidth()
                                                        * scale)
                                ),
                                Math.max(
                                        1,
                                        (int)
                                                (original.getHeight()
                                                        * scale)
                                ),
                                true
                        );

            } else {

                scaled = original;
            }

            if (scaled != original) {
                original.recycle();
            }

            return scaled;

        } catch (Exception e) {

            return null;

        } finally {

            if (input != null) {

                try {
                    input.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    static void update(
            Context context,
            AppWidgetManager manager,
            int id) {

        RemoteViews views =
                new RemoteViews(
                        context.getPackageName(),
                        R.layout.widget
                );

        JSONObject item =
                next(context);

        if (item == null) {

            views.setTextViewText(
                    R.id.widget_label,
                    "NEXT CLASS"
            );

            views.setTextViewText(
                    R.id.widget_subject,
                    "No classes scheduled"
            );

            views.setTextViewText(
                    R.id.widget_time,
                    "Open app to add your schedule"
            );

            views.setTextViewText(
                    R.id.widget_room,
                    ""
            );

            views.setTextViewText(
                    R.id.widget_teacher,
                    ""
            );

        } else {

            views.setTextViewText(
                    R.id.widget_label,
                    "NEXT CLASS"
            );

            views.setTextViewText(
                    R.id.widget_subject,
                    item.optString(
                            "subject",
                            "Class"
                    )
            );

            views.setTextViewText(
                    R.id.widget_time,
                    item.optString(
                            "start",
                            ""
                    )
                            + " - "
                            + item.optString(
                                    "end",
                                    ""
                            )
            );

            views.setTextViewText(
                    R.id.widget_room,
                    item.optString(
                            "room",
                            ""
                    )
            );

            views.setTextViewText(
                    R.id.widget_teacher,
                    item.optString(
                            "teacher",
                            ""
                    )
            );
        }

        android.content.SharedPreferences preferences =
                context.getSharedPreferences(
                        "widget_settings",
                        0
                );

        int backgroundColor =
                preferences.getInt(
                        "bgColor",
                        Color.rgb(11, 11, 11)
                );

        int textColor =
                preferences.getInt(
                        "textColor",
                        Color.WHITE
                );

        int radius =
                preferences.getInt(
                        "radius",
                        18
                );

        String imageUri =
                preferences.getString(
                        "bgUri",
                        ""
                );

        views.setTextColor(
                R.id.widget_label,
                textColor
        );

        views.setTextColor(
                R.id.widget_subject,
                textColor
        );

        views.setTextColor(
                R.id.widget_time,
                textColor
        );

        views.setTextColor(
                R.id.widget_room,
                textColor
        );

        views.setTextColor(
                R.id.widget_teacher,
                textColor
        );

        int backgroundResource;

        if (radius <= 0) {

            backgroundResource =
                    R.drawable.widget_bg_square;

        } else if (radius <= 12) {

            backgroundResource =
                    R.drawable.widget_bg_slight;

        } else if (radius <= 24) {

            backgroundResource =
                    R.drawable.widget_bg_round;

        } else {

            backgroundResource =
                    R.drawable.widget_bg_veryround;
        }

        views.setInt(
                R.id.widget_root,
                "setBackgroundResource",
                backgroundResource
        );

        /*
         * Background image
         */

        Bitmap image =
                loadBackgroundImage(
                        context,
                        imageUri
                );

        if (image != null) {

            float density =
                    context
                            .getResources()
                            .getDisplayMetrics()
                            .density;

            float imageRadius =
                    radius * density;

            Bitmap rounded =
                    makeRoundedBitmap(
                            image,
                            imageRadius
                    );

            if (rounded != null) {

                views.setImageViewBitmap(
                        R.id.widget_bg_image,
                        rounded
                );

                views.setViewVisibility(
                        R.id.widget_bg_image,
                        View.VISIBLE
                );

            } else {

                views.setViewVisibility(
                        R.id.widget_bg_image,
                        View.GONE
                );
            }

            if (rounded != image
                    && rounded != null) {

                image.recycle();
            }

        } else {

            views.setViewVisibility(
                    R.id.widget_bg_image,
                    View.GONE
            );
        }

        /*
         * Widget click
         */

        Intent launchIntent =
                new Intent(
                        context,
                        MainActivity.class
                );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context,
                        0,
                        launchIntent,
                        PendingIntent.FLAG_IMMUTABLE
                                | PendingIntent.FLAG_UPDATE_CURRENT
                );

        views.setOnClickPendingIntent(
                R.id.widget_root,
                pendingIntent
        );

        manager.updateAppWidget(
                id,
                views
        );
    }
}
