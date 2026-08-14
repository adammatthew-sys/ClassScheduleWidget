package com.classeschedule.widget;

import android.app.Activity;
import android.content.Intent;
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
    private float posX = 0f;
    private float posY = 0f;

    private float lastX;
    private float lastY;
    private boolean dragging = false;

    private ScaleGestureDetector scaleDetector;

    // Same landscape ratio as the widget
    private static final float CROP_RATIO = 16f / 9f;

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
            imageView.setScaleType(ImageView.ScaleType.MATRIX);

        } catch (Exception e) {
            Toast.makeText(this, "Unable to load image", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        scaleDetector = new ScaleGestureDetector(
                this,
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

                        applyTransform();

                        return true;
                    }
                }
        );

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

                        applyTransform();
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

    private void applyTransform() {

        if (originalBitmap == null) {
            return;
        }

        float centerX = imageView.getWidth() / 2f;
        float centerY = imageView.getHeight() / 2f;

        Matrix matrix = new Matrix();

        matrix.postScale(
                scale,
                scale,
                centerX,
                centerY
        );

        matrix.postTranslate(
                posX,
                posY
        );

        imageView.setImageMatrix(matrix);
    }

    private void cropAndSave() {

        if (originalBitmap == null) {
            Toast.makeText(
                    this,
                    "No image selected",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        try {

            int viewWidth = imageView.getWidth();
            int viewHeight = imageView.getHeight();

            if (viewWidth <= 0 || viewHeight <= 0) {
                Toast.makeText(
                        this,
                        "Please wait for the image to load",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            /*
             * Create a LANDSCAPE crop area matching
             * the widget instead of using a square.
             */

            float cropWidth;
            float cropHeight;

            if ((float) viewWidth / viewHeight > CROP_RATIO) {

                cropHeight = viewHeight * 0.85f;
                cropWidth = cropHeight * CROP_RATIO;

            } else {

                cropWidth = viewWidth * 0.85f;
                cropHeight = cropWidth / CROP_RATIO;
            }

            float cropLeft =
                    (viewWidth - cropWidth) / 2f;

            float cropTop =
                    (viewHeight - cropHeight) / 2f;

            /*
             * The image is initially centered using MATRIX.
             * Calculate the actual transformed image.
             */

            float baseScale = Math.max(
                    cropWidth / originalBitmap.getWidth(),
                    cropHeight / originalBitmap.getHeight()
            );

            float totalScale = baseScale * scale;

            float imageWidth =
                    originalBitmap.getWidth() * totalScale;

            float imageHeight =
                    originalBitmap.getHeight() * totalScale;

            float imageLeft =
                    (viewWidth - imageWidth) / 2f + posX;

            float imageTop =
                    (viewHeight - imageHeight) / 2f + posY;

            /*
             * Convert the crop rectangle from screen
             * coordinates back into bitmap coordinates.
             */

            float sourceLeft =
                    (cropLeft - imageLeft) / totalScale;

            float sourceTop =
                    (cropTop - imageTop) / totalScale;

            float sourceWidth =
                    cropWidth / totalScale;

            float sourceHeight =
                    cropHeight / totalScale;

            int left = Math.round(sourceLeft);
            int top = Math.round(sourceTop);

            int width = Math.round(sourceWidth);
            int height = Math.round(sourceHeight);

            /*
             * Keep the crop inside the source bitmap.
             */

            if (left < 0) {
                width += left;
                left = 0;
            }

            if (top < 0) {
                height += top;
                top = 0;
            }

            if (left + width > originalBitmap.getWidth()) {
                width = originalBitmap.getWidth() - left;
            }

            if (top + height > originalBitmap.getHeight()) {
                height = originalBitmap.getHeight() - top;
            }

            if (width <= 0 || height <= 0) {

                Toast.makeText(
                        this,
                        "Invalid crop area",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            Bitmap cropped = Bitmap.createBitmap(
                    originalBitmap,
                    left,
                    top,
                    width,
                    height
            );

            /*
             * Save the cropped image inside the app.
             */

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

            /*
             * Store the actual file URI.
             */

            Uri savedUri =
                    Uri.fromFile(file);

            getSharedPreferences(
                    "widget_settings",
                    0
            )
                    .edit()
                    .putString(
                            "bgUri",
                            savedUri.toString()
                    )
                    .apply();

            Intent result = new Intent();

            result.putExtra(
                    "croppedPath",
                    file.getAbsolutePath()
            );

            result.putExtra(
                    "croppedUri",
                    savedUri.toString()
            );

            setResult(
                    RESULT_OK,
                    result
            );

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
