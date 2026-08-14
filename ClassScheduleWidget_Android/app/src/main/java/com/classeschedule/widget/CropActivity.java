package com.classeschedule.widget;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class CropActivity extends Activity {

    private ImageView imageView;
    private View cropFrame;
    private Bitmap originalBitmap;

    private float scale = 1f;
    private float posX = 0f;
    private float posY = 0f;

    private float lastX;
    private float lastY;
    private boolean dragging = false;

    private ScaleGestureDetector scaleDetector;

    // Widget crop ratio.
    // Landscape so the crop screen matches the widget style.
    private static final float CROP_RATIO = 16f / 9f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_crop);

        imageView = findViewById(R.id.cropImage);
        cropFrame = findViewById(R.id.cropFrame);

        Button useButton = findViewById(R.id.cropUseButton);
        Button cancelButton = findViewById(R.id.cropCancelButton);

        String uriString =
                getIntent().getStringExtra("imageUri");

        if (uriString == null || uriString.isEmpty()) {
            Toast.makeText(
                    this,
                    "Unable to load image",
                    Toast.LENGTH_SHORT
            ).show();

            finish();
            return;
        }

        loadImage(uriString);

        scaleDetector =
                new ScaleGestureDetector(
                        this,
                        new ScaleGestureDetector.SimpleOnScaleGestureListener() {

                            @Override
                            public boolean onScale(
                                    ScaleGestureDetector detector) {

                                scale *= detector.getScaleFactor();

                                if (scale < 1f) {
                                    scale = 1f;
                                }

                                if (scale > 6f) {
                                    scale = 6f;
                                }

                                applyTransform();

                                return true;
                            }
                        }
                );

        imageView.setOnTouchListener(
                (v, event) -> {

                    scaleDetector.onTouchEvent(event);

                    switch (event.getActionMasked()) {

                        case MotionEvent.ACTION_DOWN:

                            lastX = event.getX();
                            lastY = event.getY();

                            dragging = true;

                            return true;

                        case MotionEvent.ACTION_MOVE:

                            if (event.getPointerCount() == 1 &&
                                    dragging) {

                                float dx =
                                        event.getX() - lastX;

                                float dy =
                                        event.getY() - lastY;

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
                }
        );

        useButton.setOnClickListener(
                v -> cropAndSave()
        );

        cancelButton.setOnClickListener(
                v -> finish()
        );

        cropFrame.post(
                this::applyCropFrameRatio
        );
    }

    private void loadImage(String uriString) {

        try {

            InputStream in =
                    getContentResolver()
                            .openInputStream(
                                    Uri.parse(uriString)
                            );

            originalBitmap =
                    BitmapFactory.decodeStream(in);

            if (in != null) {
                in.close();
            }

            if (originalBitmap == null) {
                throw new Exception();
            }

            imageView.setImageBitmap(
                    originalBitmap
            );

            imageView.setScaleType(
                    ImageView.ScaleType.MATRIX
            );

            cropFrame.post(
                    this::applyInitialTransform
            );

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "Unable to load image",
                    Toast.LENGTH_SHORT
            ).show();

            finish();
        }
    }

    private void applyCropFrameRatio() {

        if (cropFrame == null) {
            return;
        }

        int availableWidth =
                ((View) cropFrame.getParent()).getWidth();

        int availableHeight =
                ((View) cropFrame.getParent()).getHeight();

        if (availableWidth <= 0 ||
                availableHeight <= 0) {
            return;
        }

        int frameWidth;
        int frameHeight;

        if ((float) availableWidth / availableHeight >
                CROP_RATIO) {

            frameHeight = availableHeight;
            frameWidth =
                    Math.round(
                            frameHeight * CROP_RATIO
                    );

        } else {

            frameWidth = availableWidth;
            frameHeight =
                    Math.round(
                            frameWidth / CROP_RATIO
                    );
        }

        android.view.ViewGroup.LayoutParams lp =
                cropFrame.getLayoutParams();

        lp.width = frameWidth;
        lp.height = frameHeight;

        cropFrame.setLayoutParams(lp);

        cropFrame.post(
                this::applyInitialTransform
        );
    }

    private void applyInitialTransform() {

        if (originalBitmap == null ||
                imageView.getWidth() <= 0 ||
                imageView.getHeight() <= 0) {
            return;
        }

        float viewWidth =
                imageView.getWidth();

        float viewHeight =
                imageView.getHeight();

        float bitmapWidth =
                originalBitmap.getWidth();

        float bitmapHeight =
                originalBitmap.getHeight();

        float baseScale =
                Math.max(
                        viewWidth / bitmapWidth,
                        viewHeight / bitmapHeight
                );

        scale = 1f;
        posX = 0f;
        posY = 0f;

        Matrix matrix =
                new Matrix();

        matrix.setScale(
                baseScale,
                baseScale
        );

        matrix.postTranslate(
                (viewWidth -
                        bitmapWidth * baseScale) / 2f,
                (viewHeight -
                        bitmapHeight * baseScale) / 2f
        );

        imageView.setImageMatrix(
                matrix
        );
    }

    private void applyTransform() {

        if (originalBitmap == null) {
            return;
        }

        float viewWidth =
                imageView.getWidth();

        float viewHeight =
                imageView.getHeight();

        if (viewWidth <= 0 ||
                viewHeight <= 0) {
            return;
        }

        float bitmapWidth =
                originalBitmap.getWidth();

        float bitmapHeight =
                originalBitmap.getHeight();

        float baseScale =
                Math.max(
                        viewWidth / bitmapWidth,
                        viewHeight / bitmapHeight
                );

        float totalScale =
                baseScale * scale;

        float imageWidth =
                bitmapWidth * totalScale;

        float imageHeight =
                bitmapHeight * totalScale;

        float left =
                (viewWidth - imageWidth) / 2f
                        + posX;

        float top =
                (viewHeight - imageHeight) / 2f
                        + posY;

        Matrix matrix =
                new Matrix();

        matrix.setScale(
                totalScale,
                totalScale
        );

        matrix.postTranslate(
                left,
                top
        );

        imageView.setImageMatrix(
                matrix
        );
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

            int frameWidth =
                    cropFrame.getWidth();

            int frameHeight =
                    cropFrame.getHeight();

            if (frameWidth <= 0 ||
                    frameHeight <= 0) {

                Toast.makeText(
                        this,
                        "Crop area is not ready",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            float bitmapWidth =
                    originalBitmap.getWidth();

            float bitmapHeight =
                    originalBitmap.getHeight();

            float baseScale =
                    Math.max(
                            frameWidth / bitmapWidth,
                            frameHeight / bitmapHeight
                    );

            float totalScale =
                    baseScale * scale;

            float imageWidth =
                    bitmapWidth * totalScale;

            float imageHeight =
                    bitmapHeight * totalScale;

            float imageLeft =
                    (frameWidth - imageWidth) / 2f
                            + posX;

            float imageTop =
                    (frameHeight - imageHeight) / 2f
                            + posY;

            float sourceLeft =
                    (-imageLeft) / totalScale;

            float sourceTop =
                    (-imageTop) / totalScale;

            float sourceWidth =
                    frameWidth / totalScale;

            float sourceHeight =
                    frameHeight / totalScale;

            int left =
                    Math.round(sourceLeft);

            int top =
                    Math.round(sourceTop);

            int width =
                    Math.round(sourceWidth);

            int height =
                    Math.round(sourceHeight);

            left =
                    Math.max(
                            0,
                            Math.min(
                                    left,
                                    originalBitmap.getWidth() - 1
                            )
                    );

            top =
                    Math.max(
                            0,
                            Math.min(
                                    top,
                                    originalBitmap.getHeight() - 1
                            )
                    );

            width =
                    Math.min(
                            width,
                            originalBitmap.getWidth() - left
                    );

            height =
                    Math.min(
                            height,
                            originalBitmap.getHeight() - top
                    );

            if (width <= 0 ||
                    height <= 0) {

                Toast.makeText(
                        this,
                        "Invalid crop area",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            Bitmap cropped =
                    Bitmap.createBitmap(
                            originalBitmap,
                            left,
                            top,
                            width,
                            height
                    );

            File file =
                    new File(
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
            )
                    .edit()
                    .putString(
                            "bgUri",
                            file.getAbsolutePath()
                    )
                    .apply();

            Intent result =
                    new Intent();

            result.putExtra(
                    "croppedPath",
                    file.getAbsolutePath()
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
