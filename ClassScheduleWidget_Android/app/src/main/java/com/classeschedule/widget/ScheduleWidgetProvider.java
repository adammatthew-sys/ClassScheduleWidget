package com.classeschedule.widget;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.Bundle;
import android.widget.RemoteViews;
import org.json.*;
import java.util.*;

public class ScheduleWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for(int id:ids) update(c,m,id);
    }

    @Override public void onReceive(Context c, Intent i) {
        super.onReceive(c,i);
        String a=i.getAction();
        if("com.classeschedule.widget.UPDATE".equals(a)
            || Intent.ACTION_TIME_CHANGED.equals(a)
            || Intent.ACTION_TIMEZONE_CHANGED.equals(a)
            || Intent.ACTION_DATE_CHANGED.equals(a)) {
            AppWidgetManager m=AppWidgetManager.getInstance(c);
            int[] ids=m.getAppWidgetIds(new ComponentName(c,ScheduleWidgetProvider.class));
            for(int id:ids) update(c,m,id);
        }
    }

    private static int minutes(String s) {
        try {
            s=s.trim().toUpperCase(Locale.US);
            String[] p=s.split(":");
            int h=Integer.parseInt(p[0]);
            int m=Integer.parseInt(p[1].trim().split("\\s+")[0]);
            boolean pm=s.contains("PM");
            if(pm&&h<12)h+=12;
            if(!pm&&s.contains("AM")&&h==12)h=0;
            return h*60+m;
        } catch(Exception e){ return 9999; }
    }

    private static JSONObject next(Context c) {
        JSONArray a;
        try { a=new JSONArray(c.getSharedPreferences("schedule",0).getString("classes","[]")); }
        catch(Exception e){ return null; }

        Calendar now=Calendar.getInstance();
        int today=now.get(Calendar.DAY_OF_WEEK)==Calendar.SUNDAY ? 6 : now.get(Calendar.DAY_OF_WEEK)-Calendar.MONDAY;
        int nowMin=now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE);
        JSONObject best=null;
        int bestDelta=Integer.MAX_VALUE;

        for(int i=0;i<a.length();i++) try {
            JSONObject o=a.getJSONObject(i);
            int d=o.optInt("day",-1);
            if(d<0||d>6) continue;
            int t=minutes(o.optString("start"));
            if(t==9999) continue;
            int delta=(d-today+7)%7;
            if(delta==0 && t<=nowMin) delta=7;
            int score=delta*1440+t-nowMin;
            if(score<bestDelta){bestDelta=score;best=o;}
        } catch(Exception ignored){}
        return best;
    }

    static void update(Context c, AppWidgetManager m, int id) {
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget);
        JSONObject o=next(c);
        if(o==null){
            v.setTextViewText(R.id.widget_label,"NEXT CLASS");
            v.setTextViewText(R.id.widget_subject,"No classes scheduled");
            v.setTextViewText(R.id.widget_time,"Open app to add your schedule");
            v.setTextViewText(R.id.widget_room,"");
            v.setTextViewText(R.id.widget_teacher,"");
        } else {
            v.setTextViewText(R.id.widget_label,"NEXT CLASS");
            v.setTextViewText(R.id.widget_subject,o.optString("subject"));
            v.setTextViewText(R.id.widget_time,o.optString("start")+" - "+o.optString("end"));
            v.setTextViewText(R.id.widget_room,o.optString("room"));
            v.setTextViewText(R.id.widget_teacher,o.optString("teacher"));
        }
        Intent x=new Intent(c,MainActivity.class);
        PendingIntent p=PendingIntent.getActivity(c,0,x,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.widget_root,p);
        m.updateAppWidget(id,v);
    }
}
