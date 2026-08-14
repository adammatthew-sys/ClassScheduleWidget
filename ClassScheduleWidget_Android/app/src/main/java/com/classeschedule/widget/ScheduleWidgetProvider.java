package com.classeschedule.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.Calendar;
import java.util.Locale;

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

        AppWidgetManager manager =
                AppWidgetManager.getInstance(
                        context.getApplicationContext()
                );

        ComponentName componentName =
                new ComponentName(
                        context,
                        ScheduleWidgetProvider.class
                );

        int[] ids =
                manager.getAppWidgetIds(componentName);

        for (int id : ids) {
            update(context, manager, id);
        }
    }

    private static int minutes(String value) {

        try {

            value = value
                    .trim()
                    .toUpperCase(Locale.US);

            boolean pm = value.contains("PM");
            boolean am = value.contains("AM");

            value = value
                    .replace("AM", "")
                    .replace("PM", "")
                    .trim();

            String[] parts = value.split(":");

            int hour =
                    Integer.parseInt(parts[0].trim());

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

            for (int i = 0;
                 i < classes.length();
                 i++) {

                JSONObject object =
                        classes.getJSONObject(i);

                int day =
                        object.optInt(
                                "day",
                                -1
                        );

                int start =
                        minutes(
                                object.optString(
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
                    best = object;
                }
            }

            return best;

        } catch (Exception e) {

            return null;
        }
    }

    static void update(
            Context context,
            AppWidgetManager manager,
            int widgetId) {

        RemoteViews views =
                new RemoteViews(
                        context.getPackageName(),
                        R.layout.widget
                );

        JSONObject object =
                next(context);

        if (object == null) {

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
                    object.optString(
                            "subject",
                            "Class"
                    )
            );

            views.setTextViewText(
                    R.id.widget_time,
                    object.optString(
                            "start",
                            ""
                    )
                            + " - "
                            + object.optString(
                                    "end",
                                    ""
                            )
            );

            views.setTextViewText(
                    R.id.widget_room,
                    object.optString(
                            "room",
                            ""
                    )
            );

            views.setTextViewText(
                    R.id.widget_teacher,
                    object.optString(
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
                        android.graphics.Color.rgb(
                                11,
                                11,
                                11
                        )
                );

        int textColor =
                preferences.getInt(
                        "textColor",
                        android.graphics.Color.WHITE
                );

        int radius =
                preferences.getInt(
                        "radius",
                        18
                );

        String imagePath =
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

        views.setViewVisibility(
                R.id.widget_bg_image,
                View.GONE
        );

        if (imagePath != null
                && !imagePath.isEmpty()) {

            Bitmap bitmap =
                    loadBitmap(
                            context,
                            imagePath
                    );

            if (bitmap != null) {

                Bitmap roundedBitmap =
                        createRoundedBitmap(
                                bitmap,
                                radius,
                                context
                        );

                if (roundedBitmap != null) {

                    views.setImageViewBitmap(
                            R.id.widget_bg_image,
                            roundedBitmap
                    );

                    views.setViewVisibility(
                            R.id.widget_bg_image,
                            View.VISIBLE
                    );
                }

                if (!bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        }

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
                widgetId,
                views
        );
    }

    private static Bitmap loadBitmap(
            Context context,
            String path) {

        try {

            if (path.startsWith("/")) {

                return BitmapFactory.decodeFile(
                        path
                );
            }

            Uri uri =
                    Uri.parse(path);

            InputStream inputStream =
                    context
                            .getContentResolver()
                            .openInputStream(uri);

            if (inputStream == null) {
                return null;
            }

            Bitmap bitmap =
                    BitmapFactory.decodeStream(
                            inputStream
                    );

            inputStream.close();

            return bitmap;

        } catch (Exception e) {

            return null;
        }
    }

    private static Bitmap createRoundedBitmap(
            Bitmap source,
            int radiusDp,
            Context context) {

        try {

            float density =
                    context
                            .getResources()
                            .getDisplayMetrics()
                            .density;

            float radius =
                    radiusDp * density;

            Bitmap output =
                    Bitmap.createBitmap(
                            source.getWidth(),
                            source.getHeight(),
                            Bitmap.Config.ARGB_8888
                    );

            Canvas canvas =
                    new Canvas(output);

            Path path =
                    new Path();

            RectF rect =
                    new RectF(
                            0,
                            0,
                            source.getWidth(),
                            source.getHeight()
                    );

            path.addRoundRect(
                    rect,
                    radius,
                    radius,
                    Path.Direction.CW
            );

            canvas.save();

            canvas.clipPath(path);

            canvas.drawBitmap(
                    source,
                    0,
                    0,
                    null
            );

            canvas.restore();

            return output;

        } catch (Exception e) {

            return source;
        }
    }
}
