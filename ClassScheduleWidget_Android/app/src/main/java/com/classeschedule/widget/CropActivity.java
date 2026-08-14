package com.classeschedule.widget;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class CropActivity extends Activity {

    private ImageView imageView;
    private Bitmap originalBitmap;

    private float scale = 1f;
    private float lastX;
    private float lastY;
    private float posX = 0f;
    private float posY = 0f;
    private boolean dragging = false;

    private ScaleGestureDetector scaleDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        imageView = findViewById(R.id.cropImage);

        Button useButton = findViewById(R.id.cropUseButton);
        Button cancelButton = findViewById(R.id.cropCancelButton);

        String uriString = getIntent().getStringExtra("imageUri");

        if (uriString == null || uriString.isEmpty()) {
            Toast.makeText(this, "Unable to load image", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            InputStream in = getContentResolver()
                    .openInputStream(Uri.parse(uriString));

            originalBitmap = BitmapFactory.decodeStream(in);

            if (in != null) {
                in.close();
            }

            if (originalBitmap == null) {
                Toast.makeText(this, "Unable to load image", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            imageView.setImageBitmap(originalBitmap);

        } catch (Exception e) {
            Toast.makeText(this, "Unable to load image", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        scale *= detector.getScaleFactor();

                        if (scale < 0.5f) {
                            scale = 0.5f;
                        }

                        if (scale > 5f) {
                            scale = 5f;
                        }

                        applyImageTransform();
                        return true;
                    }
                });

        imageView.setOnTouchListener((v, event) -> {

            scaleDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {

                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    dragging = true;
                    return true;

                case MotionEvent.ACTION_MOVE:

                    if (event.getPointerCount() == 1 && dragging) {
                        float dx = event.getX() - lastX;
                        float dy = event.getY() - lastY;

                        posX += dx;
                        posY += dy;

                        lastX = event.getX();
                        lastY = event.getY();

                        applyImageTransform();
                    }

                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    return true;
            }

            return true;
        });

        useButton.setOnClickListener(v -> cropAndSave());

        cancelButton.setOnClickListener(v -> finish());
    }

    private void applyImageTransform() {

        Matrix matrix = new Matrix();

        matrix.postScale(
                scale,
                scale,
                imageView.getWidth() / 2f,
                imageView.getHeight() / 2f
        );

        matrix.postTranslate(posX, posY);

        imageView.setImageMatrix(matrix);
        imageView.setScaleType(ImageView.ScaleType.MATRIX);
    }

    private void cropAndSave() {

        if (originalBitmap == null) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
            return;
        }

        try {

            int cropSize = Math.min(
                    originalBitmap.getWidth(),
                    originalBitmap.getHeight()
            );

            int left =
                    (originalBitmap.getWidth() - cropSize) / 2;

            int top =
                    (originalBitmap.getHeight() - cropSize) / 2;

            Bitmap cropped = Bitmap.createBitmap(
                    originalBitmap,
                    left,
                    top,
                    cropSize,
                    cropSize
            );

            File file = new File(
                    getFilesDir(),
                    "widget_background.jpg"
            );

            FileOutputStream out =
                    new FileOutputStream(file);

            cropped.compress(
                    Bitmap.CompressFormat.JPEG,
                    95,
                    out
            );

            out.flush();
            out.close();

            cropped.recycle();

            getSharedPreferences(
                    "widget_settings",
                    0
            ).edit()
                    .putString(
                            "bgUri",
                            Uri.fromFile(file).toString()
                    )
                    .apply();

            setResult(RESULT_OK);

            Toast.makeText(
                    this,
                    "Photo cropped",
                    Toast.LENGTH_SHORT
            ).show();

            finish();

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Could not crop photo",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }
}
