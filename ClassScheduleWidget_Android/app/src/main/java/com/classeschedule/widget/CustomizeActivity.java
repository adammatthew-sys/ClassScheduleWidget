package com.classeschedule.widget;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.InputStream;
import java.util.*;

public class CustomizeActivity extends Activity {
    private static final String PREFS = "widget_settings";
    private static final String BG_COLOR = "bgColor";
    private static final String TEXT_COLOR = "textColor";
    private static final String RADIUS = "radius";
    private static final String BG_URI = "bgUri";

    private LinearLayout preview;
    private TextView pLabel, pSubject, pTime, pRoom, pTeacher;
    private int bgColor, textColor, radius;
    private String bgUri = "";

    private final int[] bgChoices = {
        Color.rgb(11,11,11), Color.rgb(30,30,30), Color.rgb(45,45,45),
        Color.rgb(20,35,55), Color.rgb(35,20,50), Color.rgb(20,50,40),
        Color.rgb(70,35,20), Color.WHITE
    };
    private final String[] bgNames = {"Black","Dark Gray","Gray","Navy","Purple","Green","Brown","White"};
    private final int[] textChoices = {
        Color.WHITE, Color.BLACK, Color.rgb(220,220,220), Color.rgb(120,200,255),
        Color.rgb(255,220,100), Color.rgb(255,150,150), Color.rgb(170,255,190)
    };
    private final String[] textNames = {"White","Black","Light Gray","Blue","Yellow","Pink","Mint"};

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_customize);
        preview = findViewById(R.id.customPreview);
        pLabel=findViewById(R.id.previewLabel);
        pSubject=findViewById(R.id.previewSubject);
        pTime=findViewById(R.id.previewTime);
        pRoom=findViewById(R.id.previewRoom);
        pTeacher=findViewById(R.id.previewTeacher);

        android.content.SharedPreferences p=getSharedPreferences(PREFS,0);
        bgColor=p.getInt(BG_COLOR,Color.rgb(11,11,11));
        textColor=p.getInt(TEXT_COLOR,Color.WHITE);
        radius=p.getInt(RADIUS,18);
        bgUri=p.getString(BG_URI,"");

        findViewById(R.id.backgroundColorButton).setOnClickListener(v -> chooseColor(true));
        findViewById(R.id.textColorButton).setOnClickListener(v -> chooseColor(false));
        findViewById(R.id.cornerButton).setOnClickListener(v -> chooseRadius());
        findViewById(R.id.imageButton).setOnClickListener(v -> pickImage());
        findViewById(R.id.clearImageButton).setOnClickListener(v -> { bgUri=""; updatePreview(); });
        findViewById(R.id.saveButton).setOnClickListener(v -> saveAndExit());

        updatePreview();
    }

    private void chooseColor(boolean background) {
        int[] colors=background?bgChoices:textChoices;
        String[] names=background?bgNames:textNames;
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(background?"Background color":"Text color");
        b.setItems(names,(d,w)->{
            if(background) bgColor=colors[w]; else textColor=colors[w];
            updatePreview();
        });
        b.show();
    }

    private void chooseRadius() {
        String[] names={"Square","Slightly rounded","Rounded","Very rounded"};
        int[] vals={0,12,20,30};
        new AlertDialog.Builder(this).setTitle("Corner roundness")
            .setItems(names,(d,w)->{ radius=vals[w]; updatePreview(); }).show();
    }

    private void pickImage() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,41);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==41 && resultCode==RESULT_OK && data!=null && data.getData()!=null) {
            Uri u=data.getData();
            try {
                getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch(Exception ignored){}
            bgUri=u.toString();
            updatePreview();
        }
    }

    private GradientDrawable shape(int color,int r) {
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r*getResources().getDisplayMetrics().density);
        return g;
    }

    private void updatePreview() {
        preview.setBackground(shape(bgColor,radius));
        int c=textColor;
        pLabel.setTextColor(c); pSubject.setTextColor(c); pTime.setTextColor(c);
        pRoom.setTextColor(c); pTeacher.setTextColor(c);
        if(!bgUri.isEmpty()) {
            try {
                InputStream in=getContentResolver().openInputStream(Uri.parse(bgUri));
                Bitmap b=BitmapFactory.decodeStream(in);
                if(in!=null) in.close();
                ImageView image=findViewById(R.id.previewImage);
                image.setImageBitmap(b); image.setVisibility(View.VISIBLE);
                preview.setBackground(shape(Color.TRANSPARENT,radius));
            } catch(Exception e) {
                findViewById(R.id.previewImage).setVisibility(View.GONE);
            }
        } else findViewById(R.id.previewImage).setVisibility(View.GONE);
    }

    private void saveAndExit() {
        getSharedPreferences(PREFS,0).edit()
            .putInt(BG_COLOR,bgColor)
            .putInt(TEXT_COLOR,textColor)
            .putInt(RADIUS,radius)
            .putString(BG_URI,bgUri)
            .apply();
        ScheduleWidgetProvider.refreshAll(this);
        Toast.makeText(this,"Widget updated",Toast.LENGTH_SHORT).show();
        finish();
    }
}
