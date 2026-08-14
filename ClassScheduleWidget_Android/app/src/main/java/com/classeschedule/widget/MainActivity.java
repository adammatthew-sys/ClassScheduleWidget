package com.classeschedule.widget;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout list;
    private TextView dateTime;
    private int day = 0;
    private final String[] days = {"Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"};
    private final String PREFS = "schedule";
    private final String KEY = "classes";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        list = findViewById(R.id.listContainer);
        dateTime = findViewById(R.id.currentDateTime);

        int[] ids = {R.id.dayMon,R.id.dayTue,R.id.dayWed,R.id.dayThu,R.id.dayFri,R.id.daySat,R.id.daySun};
        for (int i=0;i<7;i++) {
            final int d=i;
            findViewById(ids[i]).setOnClickListener(v -> { day=d; refresh(); });
        }
        findViewById(R.id.addButton).setOnClickListener(v -> edit(-1));
        findViewById(R.id.todayButton).setOnClickListener(v -> {
            day = todayIndex();
            refresh();
        });
        findViewById(R.id.customizeButton).setOnClickListener(v ->
                startActivity(new Intent(this, CustomizeActivity.class)));
        day = todayIndex();
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        day = Math.max(0, Math.min(6, day));
        refresh();
    }

    private int todayIndex() {
        Calendar c=Calendar.getInstance();
        int dow=c.get(Calendar.DAY_OF_WEEK); // Sunday=1
        return dow==Calendar.SUNDAY ? 6 : dow-Calendar.MONDAY;
    }

    private JSONArray data() {
        try { return new JSONArray(getSharedPreferences(PREFS,0).getString(KEY,"[]")); }
        catch(Exception e){ return new JSONArray(); }
    }

    private void save(JSONArray a) {
        getSharedPreferences(PREFS,0).edit().putString(KEY,a.toString()).apply();
        Intent i=new Intent(this,ScheduleWidgetProvider.class);
        i.setAction("com.classeschedule.widget.UPDATE");
        sendBroadcast(i);
    }

    private int minutes(String s) {
        if(s==null) return 9999;
        s=s.trim().toUpperCase(Locale.US);
        try {
            String[] p=s.split(":");
            int h=Integer.parseInt(p[0]);
            String[] q=p[1].trim().split("\\s+");
            int m=Integer.parseInt(q[0]);
            boolean pm=s.contains("PM");
            if(pm && h<12) h+=12;
            if(!pm && s.contains("AM") && h==12) h=0;
            return h*60+m;
        } catch(Exception e) { return 9999; }
    }

    private void refresh() {
        TextView title=findViewById(R.id.dayTitle);
        title.setText(days[day]);
        dateTime.setText(new SimpleDateFormat("EEEE, MMM d • h:mm a",Locale.US).format(new Date()));
        list.removeAllViews();

        JSONArray a=data();
        ArrayList<Integer> indexes=new ArrayList<>();
        for(int i=0;i<a.length();i++) try {
            if(a.getJSONObject(i).optInt("day",-1)==day) indexes.add(i);
        } catch(Exception ignored){}

        Collections.sort(indexes,(x,y)->{
            try { return Integer.compare(minutes(a.getJSONObject(x).optString("start")), minutes(a.getJSONObject(y).optString("start"))); }
            catch(Exception e){ return 0; }
        });

        for(int idx:indexes) {
            try {
                JSONObject o=a.getJSONObject(idx);
                LinearLayout r=new LinearLayout(this);
                r.setOrientation(LinearLayout.VERTICAL);
                r.setPadding(16,14,16,14);
                r.setBackgroundResource(R.drawable.row_bg);
                TextView t=new TextView(this);
                t.setText(o.optString("subject","Untitled")+"  •  "+o.optString("start",""));
                t.setTextColor(Color.WHITE); t.setTextSize(17); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                TextView s=new TextView(this);
                String details=o.optString("room","");
                String teacher=o.optString("teacher","");
                if(!teacher.isEmpty()) details += (details.isEmpty()?"":"  •  ")+teacher;
                if(details.isEmpty()) details="No room/teacher added";
                s.setText(details); s.setTextColor(Color.LTGRAY); s.setTextSize(13);
                r.addView(t); r.addView(s);
                LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);
                rp.setMargins(0,0,0,10);
                list.addView(r,rp);
                r.setOnClickListener(v->edit(idx));
            } catch(Exception ignored){}
        }

        if(list.getChildCount()==0) {
            TextView e=new TextView(this);
            e.setText("No classes yet.\nTap + ADD CLASS to add one.");
            e.setTextColor(Color.GRAY); e.setTextSize(15); e.setPadding(8,30,8,30);
            list.addView(e);
        }
    }

    private void edit(int idx) {
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad=12; box.setPadding(pad,pad,pad,pad);

        EditText subject=new EditText(this); subject.setHint("Subject");
        EditText start=new EditText(this); start.setHint("Start time, e.g. 7:30 AM"); start.setInputType(1);
        EditText end=new EditText(this); end.setHint("End time, e.g. 8:30 AM"); end.setInputType(1);
        EditText room=new EditText(this); room.setHint("Room");
        EditText teacher=new EditText(this); teacher.setHint("Teacher");
        box.addView(subject); box.addView(start); box.addView(end); box.addView(room); box.addView(teacher);

        JSONArray a=data();
        if(idx>=0) try {
            JSONObject o=a.getJSONObject(idx);
            subject.setText(o.optString("subject"));
            start.setText(o.optString("start"));
            end.setText(o.optString("end"));
            room.setText(o.optString("room"));
            teacher.setText(o.optString("teacher"));
        } catch(Exception ignored){}

        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(idx<0 ? "Add class" : "Edit class")
            .setView(box)
            .setPositiveButton("Save",(x,w)->{
                try {
                    JSONObject o=idx<0 ? new JSONObject() : a.getJSONObject(idx);
                    o.put("day",day);
                    o.put("subject",subject.getText().toString().trim());
                    o.put("start",start.getText().toString().trim());
                    o.put("end",end.getText().toString().trim());
                    o.put("time",start.getText().toString().trim());
                    o.put("room",room.getText().toString().trim());
                    o.put("teacher",teacher.getText().toString().trim());
                    if(idx<0) a.put(o);
                    save(a); refresh(); ScheduleWidgetProvider.refreshAll(this);
                } catch(Exception ignored){}
            })
            .setNegativeButton("Cancel",null)
            .create();

        if(idx>=0) dialog.setButton(AlertDialog.BUTTON_NEUTRAL,"Delete",(x,w)->{
            JSONArray n=data(); n.remove(idx); save(n); refresh(); ScheduleWidgetProvider.refreshAll(this);
        });
        dialog.show();
    }
    @Override protected void onPause() {
        super.onPause();
        ScheduleWidgetProvider.refreshAll(this);
    }
}
