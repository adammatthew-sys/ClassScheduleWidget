package com.classeschedule.widget;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.widget.RemoteViews;
import java.io.InputStream;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class ScheduleWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_UPDATE = "com.classeschedule.widget.UPDATE";

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
    }

    @Override public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        String a=i.getAction();
        if(ACTION_UPDATE.equals(a)||Intent.ACTION_TIME_CHANGED.equals(a)||
           Intent.ACTION_TIMEZONE_CHANGED.equals(a)||Intent.ACTION_DATE_CHANGED.equals(a)) refreshAll(c);
    }

    public static void refreshAll(Context c) {
        AppWidgetManager m=AppWidgetManager.getInstance(c.getApplicationContext());
        ComponentName cn=new ComponentName(c,ScheduleWidgetProvider.class);
        for(int id:m.getAppWidgetIds(cn)) update(c,m,id);
    }

    private static int minutes(String s) {
        try {
            s=s.trim().toUpperCase(Locale.US);
            boolean pm=s.contains("PM"),am=s.contains("AM");
            s=s.replace("AM","").replace("PM","").trim();
            String[] p=s.split(":");
            int h=Integer.parseInt(p[0].trim());
            int min=Integer.parseInt(p[1].trim().split("\\s+")[0]);
            if(pm&&h<12)h+=12;if(am&&h==12)h=0;
            return h*60+min;
        } catch(Exception e){return 9999;}
    }

    private static JSONObject next(Context c) {
        try {
            JSONArray a=new JSONArray(c.getSharedPreferences("schedule",0).getString("classes","[]"));
            Calendar now=Calendar.getInstance();
            int dow=now.get(Calendar.DAY_OF_WEEK);
            int today=dow==Calendar.SUNDAY?6:dow-Calendar.MONDAY;
            int nowMin=now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE);
            JSONObject best=null; int bestScore=Integer.MAX_VALUE;
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                int d=o.optInt("day",-1),t=minutes(o.optString("start",""));
                if(d<0||d>6||t==9999)continue;
                int daysAhead=(d-today+7)%7;
                int score=daysAhead*1440+t-nowMin;
                if(score<=0)score+=7*1440;
                if(score<bestScore){bestScore=score;best=o;}
            }
            return best;
        } catch(Exception e){return null;}
    }

    static void update(Context c,AppWidgetManager m,int id) {
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget);
        JSONObject o=next(c);
        if(o==null){
            v.setTextViewText(R.id.widget_label,"NEXT CLASS");
            v.setTextViewText(R.id.widget_subject,"No classes scheduled");
            v.setTextViewText(R.id.widget_time,"Open app to add your schedule");
            v.setTextViewText(R.id.widget_room,""); v.setTextViewText(R.id.widget_teacher,"");
        } else {
            v.setTextViewText(R.id.widget_label,"NEXT CLASS");
            v.setTextViewText(R.id.widget_subject,o.optString("subject","Class"));
            v.setTextViewText(R.id.widget_time,o.optString("start","")+" - "+o.optString("end",""));
            v.setTextViewText(R.id.widget_room,o.optString("room",""));
            v.setTextViewText(R.id.widget_teacher,o.optString("teacher",""));
        }

        android.content.SharedPreferences p=c.getSharedPreferences("widget_settings",0);
        int bg=p.getInt("bgColor",Color.rgb(11,11,11));
        int text=p.getInt("textColor",Color.WHITE);
        int radius=p.getInt("radius",18);
        String uri=p.getString("bgUri","");
        v.setTextColor(R.id.widget_label,text);
        v.setTextColor(R.id.widget_subject,text);
        v.setTextColor(R.id.widget_time,text);
        v.setTextColor(R.id.widget_room,text);
        v.setTextColor(R.id.widget_teacher,text);
       int bgRes = radius <= 0
        ? R.drawable.widget_bg_square
        : radius <= 12
            ? R.drawable.widget_bg_slight
            : radius <= 24
                ? R.drawable.widget_bg_round
                : R.drawable.widget_bg_veryround;
        v.setInt(R.id.widget_root,"setBackgroundResource",bgRes);
        v.setInt(R.id.widget_root,"setBackgroundColor",bg);
        if (uri.isEmpty()) {
    v.setViewVisibility(R.id.widget_bg_image, android.view.View.GONE);
} else {
    try {
        InputStream in = c.getContentResolver()
                .openInputStream(Uri.parse(uri));

        Bitmap original = BitmapFactory.decodeStream(in);

        if (in != null) {
            in.close();
        }

        if (original != null) {
            int targetSize = 500;

            float scale = Math.min(
                    (float) targetSize / original.getWidth(),
                    (float) targetSize / original.getHeight()
            );

            Bitmap scaled = Bitmap.createScaledBitmap(
                    original,
                    Math.max(1, (int)(original.getWidth() * scale)),
                    Math.max(1, (int)(original.getHeight() * scale)),
                    true
            );

            v.setImageViewBitmap(R.id.widget_bg_image, scaled);
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

    } catch (Exception e) {
        v.setViewVisibility(
                R.id.widget_bg_image,
                android.view.View.GONE
        );
    }
}

        Intent x=new Intent(c,MainActivity.class);
        PendingIntent pnd=PendingIntent.getActivity(c,0,x,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.widget_root,pnd);
        m.updateAppWidget(id,v);
    }
}
