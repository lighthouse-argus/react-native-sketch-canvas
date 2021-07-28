package com.wwimmo.imageeditor;

import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ScaleGestureDetector;
import android.view.GestureDetector;
import android.view.MotionEvent;
import androidx.core.view.GestureDetectorCompat;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.lang.Math;

import com.wwimmo.imageeditor.utils.CanvasText;
import com.wwimmo.imageeditor.utils.Utility;
import com.wwimmo.imageeditor.utils.layers.Font;
import com.wwimmo.imageeditor.utils.layers.Layer;
import com.wwimmo.imageeditor.utils.layers.TextLayer;
import com.wwimmo.imageeditor.utils.entities.ArrowEntity;
import com.wwimmo.imageeditor.utils.entities.BorderStyle;
import com.wwimmo.imageeditor.utils.entities.EntityType;
import com.wwimmo.imageeditor.utils.entities.CircleEntity;
import com.wwimmo.imageeditor.utils.entities.RectEntity;
import com.wwimmo.imageeditor.utils.entities.CloudEntity;
import com.wwimmo.imageeditor.utils.entities.LineEntity;
import com.wwimmo.imageeditor.utils.entities.MotionEntity;
import com.wwimmo.imageeditor.utils.entities.TriangleEntity;
import com.wwimmo.imageeditor.utils.entities.TextEntity;
import com.wwimmo.imageeditor.utils.gestureDetectors.MoveGestureDetector;
import com.wwimmo.imageeditor.utils.gestureDetectors.RotateGestureDetector;

public class ImageEditor extends View {
    // Data
    private ArrayList<SketchData> mPaths = new ArrayList<SketchData>();
    private SketchData mCurrentPath = null;

    // Gesture Detection
    private ScaleGestureDetector mScaleGestureDetector;
    private RotateGestureDetector mRotateGestureDetector;
    private MoveGestureDetector mMoveGestureDetector;
    private GestureDetectorCompat mGestureDetectorCompat;

    // Shapes/Entities
    private final ArrayList<MotionEntity> mEntities = new ArrayList<MotionEntity>();
    private MotionEntity mSelectedEntity;
    private int mEntityBorderColor = Color.TRANSPARENT;
    private BorderStyle mEntityBorderStyle = BorderStyle.DASHED;
    private float mEntityBorderStrokeWidth = 1;
    private float mEntityStrokeWidth = 5;
    private int mEntityStrokeColor = Color.BLACK;

    // Text
    private ArrayList<CanvasText> mArrCanvasText = new ArrayList<CanvasText>();
    private ArrayList<CanvasText> mArrTextOnSketch = new ArrayList<CanvasText>();
    private ArrayList<CanvasText> mArrSketchOnText = new ArrayList<CanvasText>();
    private Typeface mTypeface;

    // Bitmap
    private Bitmap mDrawingBitmap = null, mTranslucentDrawingBitmap = null;
    private Bitmap mBackgroundImage;
    private Canvas mDrawingCanvas = null, mTranslucentDrawingCanvas = null;
    private int mOriginalBitmapWidth, mOriginalBitmapHeight;
    private String mBitmapContentMode;

    // General
    private Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Canvas mSketchCanvas = null;
    private ThemedReactContext mContext;
    private boolean mDisableHardwareAccelerated = false;
    private boolean mNeedsFullRedraw = true;

    public ImageEditor(ThemedReactContext context) {
        super(context);
        mContext = context;

        this.mScaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        this.mRotateGestureDetector = new RotateGestureDetector(context, new RotateListener());
        this.mMoveGestureDetector = new MoveGestureDetector(context, new MoveListener());
        this.mGestureDetectorCompat = new GestureDetectorCompat(context, new TapsListener());

        // Is initialized at bottom of class w/ other GestureDetectors
        setOnTouchListener(mOnTouchListener);
    }

    public String getBase64(String format, boolean transparent, boolean includeImage, boolean includeText, boolean cropToImageSize) {
        WritableMap event = Arguments.createMap();
        Bitmap bitmap = createImage(format.equals("png") && transparent, includeImage, includeText, cropToImageSize);
        ByteArrayOutputStream byteArrayOS = new ByteArrayOutputStream();

        bitmap.compress(
                format.equals("png") ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG,
                format.equals("png") ? 100 : 90,
                byteArrayOS);
        return Base64.encodeToString(byteArrayOS.toByteArray(), Base64.DEFAULT);
    }

    private Bitmap createImage(boolean transparent, boolean includeImage, boolean includeText, boolean cropToImageSize) {
        Bitmap bitmap = Bitmap.createBitmap(
                mBackgroundImage != null && cropToImageSize ? mOriginalBitmapWidth : getWidth(),
                mBackgroundImage != null && cropToImageSize ? mOriginalBitmapHeight : getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawARGB(transparent ? 0 : 255, 255, 255, 255);

        if (mBackgroundImage != null && includeImage) {
            Rect targetRect = new Rect();
            Utility.fillImage(mBackgroundImage.getWidth(), mBackgroundImage.getHeight(),
                    bitmap.getWidth(), bitmap.getHeight(), mBitmapContentMode).roundOut(targetRect);
            canvas.drawBitmap(mBackgroundImage, null, targetRect, null);
        }

        if (includeText) {
            for(CanvasText text: mArrSketchOnText) {
                canvas.drawText(text.text, text.drawPosition.x + text.lineOffset.x, text.drawPosition.y + text.lineOffset.y, text.paint);
            }
        }

        if (mBackgroundImage != null && cropToImageSize) {
            Rect targetRect = new Rect();
            Utility.fillImage(mDrawingBitmap.getWidth(), mDrawingBitmap.getHeight(),
                    bitmap.getWidth(), bitmap.getHeight(), "AspectFill").roundOut(targetRect);
            canvas.drawBitmap(mDrawingBitmap, null, targetRect, mPaint);
        } else {
            canvas.drawBitmap(mDrawingBitmap, 0, 0, mPaint);
        }

        if (includeText) {
            for(CanvasText text: mArrTextOnSketch) {
                canvas.drawText(text.text, text.drawPosition.x + text.lineOffset.x, text.drawPosition.y + text.lineOffset.y, text.paint);
            }
        }

        drawAllEntities(canvas);

        return bitmap;
    }

    /**
     *
     * Canvas/Draw related code
     *
     **/
    public void clear() {
        mPaths.clear();
        mCurrentPath = null;
        mNeedsFullRedraw = true;
        invalidateCanvas(true);
    }

    public void newPath(int id, int strokeColor, float strokeWidth) {
        mCurrentPath = new SketchData(id, strokeColor, strokeWidth);
        if (strokeColor != Color.TRANSPARENT) {
            mEntityStrokeColor = strokeColor;
        }
        mEntityStrokeWidth = Utility.convertPxToDpAsFloat(mContext.getResources().getDisplayMetrics(), strokeWidth);
        mPaths.add(mCurrentPath);
        boolean isErase = strokeColor == Color.TRANSPARENT;
        if (isErase && mDisableHardwareAccelerated == false) {
            mDisableHardwareAccelerated = true;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        invalidateCanvas(true);
    }

    public void addPoint(float x, float y, boolean isMove) {
        if (mSelectedEntity == null && (findEntityAtPoint(x, y) == null || isMove)) {
            Rect updateRect = mCurrentPath.addPoint(new PointF(x, y));
            if (mCurrentPath.isTranslucent) {
                mTranslucentDrawingCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
                mCurrentPath.draw(mTranslucentDrawingCanvas);
            } else {
                mCurrentPath.drawLastPoint(mDrawingCanvas);
            }
            invalidate(updateRect);
        }
    }

    public void addPath(int id, int strokeColor, float strokeWidth, ArrayList<PointF> points) {
        if (strokeColor != Color.TRANSPARENT) {
            mEntityStrokeColor = strokeColor;
        }

        boolean exist = false;
        for(SketchData data: mPaths) {
            if (data.id == id) {
                exist = true;
                break;
            }
        }

        if (!exist) {
            SketchData newPath = new SketchData(id, strokeColor, strokeWidth, points);
            mPaths.add(newPath);
            boolean isErase = strokeColor == Color.TRANSPARENT;
            if (isErase && mDisableHardwareAccelerated == false) {
                mDisableHardwareAccelerated = true;
                setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            }
            newPath.draw(mDrawingCanvas);
            invalidateCanvas(true);
        }
    }

    public void deletePath(int id) {
        int index = -1;
        for(int i = 0; i<mPaths.size(); i++) {
            if (mPaths.get(i).id == id) {
                index = i;
                break;
            }
        }

        if (index > -1) {
            mPaths.remove(index);
            mNeedsFullRedraw = true;
            invalidateCanvas(true);
        }
    }

    public void end() {
        if (mCurrentPath != null) {
            if (mCurrentPath.isTranslucent) {
                mCurrentPath.draw(mDrawingCanvas);
                mTranslucentDrawingCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
            }
            mCurrentPath = null;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (getWidth() > 0 && getHeight() > 0) {
            mDrawingBitmap = Bitmap.createBitmap(getWidth(), getHeight(),
                    Bitmap.Config.ARGB_8888);
            mDrawingCanvas = new Canvas(mDrawingBitmap);
            mTranslucentDrawingBitmap = Bitmap.createBitmap(getWidth(), getHeight(),
                    Bitmap.Config.ARGB_8888);
            mTranslucentDrawingCanvas = new Canvas(mTranslucentDrawingBitmap);

            for(CanvasText text: mArrCanvasText) {
                PointF position = new PointF(text.position.x, text.position.y);
                if (!text.isAbsoluteCoordinate) {
                    position.x *= getWidth();
                    position.y *= getHeight();
                }

                position.x -= text.textBounds.left;
                position.y -= text.textBounds.top;
                position.x -= (text.textBounds.width() * text.anchor.x);
                position.y -= (text.height * text.anchor.y);
                text.drawPosition = position;

            }

            mNeedsFullRedraw = true;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mSketchCanvas = canvas;

        if (mNeedsFullRedraw && mDrawingCanvas != null) {
            mDrawingCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);

            for(SketchData path: mPaths) {
                path.draw(mDrawingCanvas);
            }
            mNeedsFullRedraw = false;
        }

        if (mBackgroundImage != null) {
            Rect dstRect = new Rect();
            mSketchCanvas.getClipBounds(dstRect);
            mSketchCanvas.drawBitmap(mBackgroundImage, null,
                    Utility.fillImage(mBackgroundImage.getWidth(), mBackgroundImage.getHeight(), dstRect.width(), dstRect.height(), mBitmapContentMode),
                    null);
        }

        for(CanvasText text: mArrSketchOnText) {
            mSketchCanvas.drawText(text.text, text.drawPosition.x + text.lineOffset.x, text.drawPosition.y + text.lineOffset.y, text.paint);
        }

        if (mDrawingBitmap != null) {
            mSketchCanvas.drawBitmap(mDrawingBitmap, 0, 0, mPaint);
        }

        if (mTranslucentDrawingBitmap != null && mCurrentPath != null && mCurrentPath.isTranslucent) {
            mSketchCanvas.drawBitmap(mTranslucentDrawingBitmap, 0, 0, mPaint);
        }

        for(CanvasText text: mArrTextOnSketch) {
            mSketchCanvas.drawText(text.text, text.drawPosition.x + text.lineOffset.x, text.drawPosition.y + text.lineOffset.y, text.paint);
        }

        if (!mEntities.isEmpty()) {
            drawAllEntities(mSketchCanvas);
        }
    }

    private void invalidateCanvas(boolean shouldDispatchEvent) {
        if (shouldDispatchEvent) {
            WritableMap event = Arguments.createMap();
            event.putInt("pathsUpdate", mPaths.size());
            mContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                    getId(),
                    "topChange",
                    event);
        }
        invalidate();
    }

    private int exifToDegrees(int exifOrientation) {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
            return 90;
        } else if(exifOrientation == ExifInterface.ORIENTATION_NORMAL) { 
            return 0;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
            return 180;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
            return 270;
        }
        return 0;
    }

    /**
     *
     * Outgoing Events related code
     *
     **/
    public void onShapeSelectionChanged(MotionEntity nextSelectedEntity) {
        final WritableMap event = Arguments.createMap();
        boolean isShapeSelected = nextSelectedEntity != null;

        if (!isShapeSelected) {
            // This is ugly and actually was my last resort to fix the "do not draw when deselecting" problem
            // without breaking existing functionality
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    mContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                            getId(),
                            "topChange",
                            event);
                }
            }, 250);
        } else {
            selectEntity(nextSelectedEntity);
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    TextEntity textEntity = getSelectedTextEntity();
                    if(textEntity != null){
                        event.putBoolean("isShapeSelected",true);
                        event.putString("shapeText",textEntity.getLayer().getText());
                    }
                    mContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                            getId(),
                            "topChange",
                            event);
                }
            }, 250);


        }
    }

    public void onSaved(boolean success, String path) {
        WritableMap event = Arguments.createMap();
        event.putBoolean("success", success);
        event.putString("path", path);
        mContext.getJSModule(RCTEventEmitter.class).receiveEvent(
                getId(),
                "topChange",
                event);
    }

    /**
     *
     * Incoming Events related code
     *
     **/
    public void setShapeConfiguration(ReadableMap shapeConfiguration) {
        if (shapeConfiguration.hasKey("shapeBorderColor")) {
            int color = shapeConfiguration.getInt("shapeBorderColor");
            if (color != Color.TRANSPARENT) {
                mEntityBorderColor = color;
            }
        }
        if (shapeConfiguration.hasKey("shapeBorderStyle")) {
            String borderStyle = shapeConfiguration.getString("shapeBorderStyle");
            switch(borderStyle) {
                case "Dashed":
                    mEntityBorderStyle = BorderStyle.DASHED;
                    break;
                case "Solid":
                    mEntityBorderStyle = BorderStyle.SOLID;
                    break;
                default:
                    mEntityBorderStyle = BorderStyle.DASHED;
                    break;
            }
        }
        if (shapeConfiguration.hasKey("shapeBorderStrokeWidth")) {
            mEntityBorderStrokeWidth = shapeConfiguration.getInt("shapeBorderStrokeWidth");
        }
        if (shapeConfiguration.hasKey("shapeColor")) {
            int color = shapeConfiguration.getInt("shapeColor");
            if (color != Color.TRANSPARENT) {
                mEntityStrokeColor = color;
            }
        }
        if (shapeConfiguration.hasKey("shapeStrokeWidth")) {
            mEntityStrokeWidth = shapeConfiguration.getInt("shapeStrokeWidth");
        }
    }

    public void save(String format, String folder, String filename, boolean transparent, boolean includeImage, boolean includeText, boolean cropToImageSize) {
        File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + File.separator + folder);
        boolean success = f.exists() ? true : f.mkdirs();
        if (success) {
            Bitmap bitmap = createImage(format.equals("png") && transparent, includeImage, includeText, cropToImageSize);

            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) +
                    File.separator + folder + File.separator + filename + (format.equals("png") ? ".png" : ".jpg"));
            try {
                bitmap.compress(
                        format.equals("png") ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG,
                        format.equals("png") ? 100 : 90,
                        new FileOutputStream(file));
                this.onSaved(true, file.getPath());
            } catch (Exception e) {
                e.printStackTrace();
                onSaved(false, null);
            }
        } else {
            Log.e("SketchCanvas", "Failed to create folder!");
            onSaved(false, null);
        }
    }

    public boolean openImageFile(String filename, String directory, String mode) {
        if(filename != null) {
            int res = mContext.getResources().getIdentifier(
                    filename.lastIndexOf('.') == -1 ? filename : filename.substring(0, filename.lastIndexOf('.')),
                    "drawable",
                    mContext.getPackageName());
            BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
            Bitmap bitmap = null;
            
            try {
                if (res == 0) {
                    String convertedDirectory = directory == null ? "" : directory;
                    String path = filename + convertedDirectory;
                    ExifInterface exif = new ExifInterface(path);
                    int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                    int rotationInDegrees = exifToDegrees(exifOrientation);
                    Bitmap tempBitmap = BitmapFactory.decodeFile(new File(path).toString(), bitmapOptions);

                    // Let's rotate the loaded image into the correct orientation :-)
                    Matrix matrix = new Matrix();
                    if (exifOrientation != 0f) {
                        matrix.preRotate(rotationInDegrees);
                        bitmap = Bitmap.createBitmap(tempBitmap, 0, 0, tempBitmap.getWidth(), tempBitmap.getHeight(), matrix, true);
                    } else {
                        bitmap = tempBitmap;
                    }
                } else {
                    bitmap = BitmapFactory.decodeResource(mContext.getResources(), res);
                }
            } catch (Exception e) {
                Log.e("SKETCHCANVAS", "exception in openImageFile when creating ExifInterface: " + e);
                bitmap = BitmapFactory.decodeFile(new File(filename, directory == null ? "" : directory).toString(), bitmapOptions);
            }

            if(bitmap != null) {
                mBackgroundImage = bitmap;
                mOriginalBitmapHeight = bitmap.getHeight();
                mOriginalBitmapWidth = bitmap.getWidth();
                mBitmapContentMode = mode;

                invalidateCanvas(true);

                return true;
            }
        }
        return false;
    }

    public void setCanvasText(ReadableArray aText) {
        mArrCanvasText.clear();
        mArrSketchOnText.clear();
        mArrTextOnSketch.clear();

        if (aText != null) {
            for (int i=0; i<aText.size(); i++) {
                ReadableMap property = aText.getMap(i);
                if (property.hasKey("text")) {
                    String alignment = property.hasKey("alignment") ? property.getString("alignment") : "Left";
                    int lineOffset = 0, maxTextWidth = 0;
                    String[] lines = property.getString("text").split("\n");
                    ArrayList<CanvasText> textSet = new ArrayList<CanvasText>(lines.length);
                    for (String line: lines) {
                        ArrayList<CanvasText> arr = property.hasKey("overlay") && "TextOnSketch".equals(property.getString("overlay")) ? mArrTextOnSketch : mArrSketchOnText;
                        CanvasText text = new CanvasText();
                        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                        p.setTextAlign(Paint.Align.LEFT);
                        text.text = line;
                        if (property.hasKey("font")) {
                            try {
                                mTypeface = Typeface.createFromAsset(mContext.getAssets(), property.getString("font"));
                            } catch(Exception ex) {
                                mTypeface = Typeface.create(property.getString("font"), Typeface.NORMAL);
                            }
                            p.setTypeface(mTypeface);
                        }
                        p.setTextSize(property.hasKey("fontSize") ? (float)property.getDouble("fontSize") : 12);
                        p.setColor(property.hasKey("fontColor") ? property.getInt("fontColor") : 0xFF000000);
                        text.anchor = property.hasKey("anchor") ? new PointF((float)property.getMap("anchor").getDouble("x"), (float)property.getMap("anchor").getDouble("y")) : new PointF(0, 0);
                        text.position = property.hasKey("position") ? new PointF((float)property.getMap("position").getDouble("x"), (float)property.getMap("position").getDouble("y")) : new PointF(0, 0);
                        text.paint = p;
                        text.isAbsoluteCoordinate = !(property.hasKey("coordinate") && "Ratio".equals(property.getString("coordinate")));
                        text.textBounds = new Rect();
                        p.getTextBounds(text.text, 0, text.text.length(), text.textBounds);

                        text.lineOffset = new PointF(0, lineOffset);
                        lineOffset += text.textBounds.height() * 1.5 * (property.hasKey("lineHeightMultiple") ? property.getDouble("lineHeightMultiple") : 1);
                        maxTextWidth = Math.max(maxTextWidth, text.textBounds.width());

                        arr.add(text);
                        mArrCanvasText.add(text);
                        textSet.add(text);
                    }
                    for(CanvasText text: textSet) {
                        text.height = lineOffset;
                        if (text.textBounds.width() < maxTextWidth) {
                            float diff = maxTextWidth - text.textBounds.width();
                            text.textBounds.left += diff * text.anchor.x;
                            text.textBounds.right += diff * text.anchor.x;
                        }
                    }
                    if (getWidth() > 0 && getHeight() > 0) {
                        for(CanvasText text: textSet) {
                            text.height = lineOffset;
                            PointF position = new PointF(text.position.x, text.position.y);
                            if (!text.isAbsoluteCoordinate) {
                                position.x *= getWidth();
                                position.y *= getHeight();
                            }
                            position.x -= text.textBounds.left;
                            position.y -= text.textBounds.top;
                            position.x -= (text.textBounds.width() * text.anchor.x);
                            position.y -= (text.height * text.anchor.y);
                            text.drawPosition = position;
                        }
                    }
                    if (lines.length > 1) {
                        for(CanvasText text: textSet) {
                            switch(alignment) {
                                case "Left":
                                default:
                                    break;
                                case "Right":
                                    text.lineOffset.x = (maxTextWidth - text.textBounds.width());
                                    break;
                                case "Center":
                                    text.lineOffset.x = (maxTextWidth - text.textBounds.width()) / 2;
                                    break;
                            }
                        }
                    }
                }
            }
        }

        invalidateCanvas(false);
    }

    /**
     *
     * MotionEntities related code
     *
     **/
    public void addEntity(String entityId, EntityType shapeType, String textShapeFontType, int textShapeFontSize, String textShapeText, String imageShapeAsset) {
        switch(shapeType) {
            case CIRCLE:
                addCircleEntity(entityId);
                break;
            case TEXT:
                addTextEntity(entityId, textShapeFontType, textShapeFontSize, textShapeText);
                break;
            case RECT:
                addRectEntity(entityId, 600, 300);
                break;
            case SQUARE:
                addSquareEntity(entityId, 600);
                break;
            case TRIANGLE:
                addTriangleEntity(entityId);
                break;
            case ARROW:
                addArrowEntity(entityId);
                break;
            case IMAGE:
                // TODO: Doesn't exist yet
                break;
            case CLOUD:
                addCloudEntity(entityId, 600, 600);
                break;
            case LINE:
                addLineEntity(entityId, 600, 100);
                break;
            default:
                addCircleEntity(entityId);
                break;
        }
    }
    
    protected void addCircleEntity(String entityId) {
        Layer circleLayer = new Layer();
        CircleEntity circleEntity = null;
        circleEntity = new CircleEntity(entityId, circleLayer, mDrawingCanvas.getWidth(), mDrawingCanvas.getHeight(), 300, 20f, mEntityStrokeWidth, mEntityStrokeColor);
        addEntityAndPosition(circleEntity);

        PointF center = circleEntity.absoluteCenter();
        center.y = center.y * 0.5F;
        circleEntity.moveCenterTo(center);

        invalidateCanvas(true);
    }

    protected void addTriangleEntity(String entityId) {
        Layer triangleLayer = new Layer();
        TriangleEntity triangleEntity = null;
        triangleEntity = new TriangleEntity(entityId, triangleLayer, mDrawingCanvas.getWidth(), mDrawingCanvas.getHeight(), 600, 20f, mEntityStrokeWidth, mEntityStrokeColor);
        addEntityAndPosition(triangleEntity);

        PointF center = triangleEntity.absoluteCenter();
        center.y = center.y * 0.5F;
        triangleEntity.moveCenterTo(center);

        invalidateCanvas(true);
    }

    protected void addArrowEntity(String entityId) {
        Layer arrowLayer = new Layer();
        ArrowEntity arrowEntity = null;
        arrowEntity = new ArrowEntity(entityId, arrowLayer, mDrawingCanvas.getWidth(), mDrawingCanvas.getHeight(), 600, 600, 20f, mEntityStrokeWidth, mEntityStrokeColor);
        addEntityAndPosition(arrowEntity);

        PointF center = arrowEntity.absoluteCenter();
        center.y = center.y * 0.5F;
        arrowEntity.moveCenterTo(center);

        invalidateCanvas(true);
    }

    protected void addSquareEntity(String entityId, int width) {
        addRectEntity(entityId, width, width);
    }

    protected void addRectEntity(String entityId, int width, int height) {
        Layer rectLayer = new Layer();
        RectEntity rectEntity = null;
        rectEntity = new RectEntity(entityId, rectLayer, mDrawingCanvas.getWidth(), mDrawingCanvas.getHeight(), width, height, 30f, mEntityStrokeWidth, mEntityStrokeColor);
        addEntityAndPosition(rectEntity);

        PointF center = rectEntity.absoluteCenter();
        center.y = center.y * 0.5F;
        rectEntity.moveCenterTo(center);

        invalidateCanvas(true);
    }

    protected void addCloudEntity(String entityId, int width, int height) {
        Layer rectLayer = new Layer();
        CloudEntity cloudEntity = null;
        cloudEntity = new CloudEntity(mContext, entityId, rectLayer, mDrawingCanvas.getWidth(), mDrawingCanvas.getHeight(), width, height, 30f, mEntityStrokeWidth, mEntityStrokeColor);
        addEntityAndPosition(cloudEntity);

        PointF center = cloudEntity.absoluteCenter();
        center.y = center.y * 0.5F;
        cloudEntity.moveCenterTo(center);

        invalidateCanvas(true);
    }

    protected void addLineEntity(String entityId, int width, int height) {
        Layer lineLayer = new Layer();
        LineEntity lineEntity = null;
        lineEntity = new LineEntity(entityId, lineLayer, mDrawingCanvas.getWidth(), mDrawingCanvas.getHeight(), width, height, 30f, mEntityStrokeWidth, mEntityStrokeColor);
        addEntityAndPosition(lineEntity);

        PointF center = lineEntity.absoluteCenter();
        center.y = center.y * 0.5F;
        lineEntity.moveCenterTo(center);

        invalidateCanvas(true);
    }

    protected void addTextEntity(String entityId, String fontType, int fontSize, String text) {
        TextLayer textLayer = createTextLayer(fontType, fontSize);
        if (text != null) {
            textLayer.setText(text);
        } else {
            textLayer.setText("No Text provided!");
        }

        TextEntity textEntity = null;
        textEntity = new TextEntity(entityId, textLayer, mDrawingCanvas.getWidth(), mDrawingCanvas.getHeight());
        addEntityAndPosition(textEntity);

        PointF center = textEntity.absoluteCenter();
        center.y = center.y * 0.5F;
        textEntity.moveCenterTo(center);

        invalidateCanvas(true);
    }

    private TextLayer createTextLayer(String fontType, int fontSize) {
        TextLayer textLayer = new TextLayer(mContext);
        Font font = new Font(mContext, null);
        font.setColor(mEntityStrokeColor);

        if (fontSize > 0) {
            float convertedFontSize = (float)fontSize / 200;
            font.setSize(convertedFontSize);
        } else {
            font.setSize(TextLayer.Limits.INITIAL_FONT_SIZE);
        }

        if (fontType != null) {
            Typeface typeFace = null;
            try {
                typeFace = Typeface.createFromAsset(mContext.getAssets(), fontType);
            } catch(Exception ex) {
                typeFace = Typeface.create(fontType, Typeface.NORMAL);
            }
            font.setTypeface(typeFace);
        }

        textLayer.setFont(font);
        return textLayer;
    }

    public void addEntityAndPosition(MotionEntity entity) {
        if (entity != null) {
            if (mEntityBorderStyle == BorderStyle.DASHED) {
                // Make DashPathEffect work with drawLines (drawSelectedBg in MotionEntity)
                mDisableHardwareAccelerated = true;
                setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            }

            initEntityBorder(entity);
            initialTranslateAndScale(entity);
            mEntities.add(entity);
            onShapeSelectionChanged(entity);
            selectEntity(entity);
        }
    }

    private void initEntityBorder(MotionEntity entity) {
        int strokeSize = Utility.convertDpToPx(mContext.getResources().getDisplayMetrics(), mEntityBorderStrokeWidth);
        Paint borderPaint = new Paint();
        borderPaint.setStrokeWidth(strokeSize);
        borderPaint.setAntiAlias(true);
        borderPaint.setColor(mEntityBorderColor);
        entity.setBorderPaint(borderPaint);
        entity.setBorderStyle(mEntityBorderStyle);
    }

    private void drawAllEntities(Canvas canvas) {
        Paint paint = new Paint();
        paint.setColor(mEntityStrokeColor);
        paint.setStrokeWidth(mEntityStrokeWidth);

        for (int i = 0; i < mEntities.size(); i++) {
            mEntities.get(i).draw(canvas, paint);
        }
    }

    private void handleTranslate(PointF delta) {
        if (mSelectedEntity != null) {
            float newCenterX = mSelectedEntity.absoluteCenterX() + delta.x;
            float newCenterY = mSelectedEntity.absoluteCenterY() + delta.y;
            
            // limit entity center to screen bounds
            boolean needUpdateUI = false;
            if (newCenterX >= 0 && newCenterX <= getWidth()) {
                mSelectedEntity.getLayer().postTranslate(delta.x / getWidth(), 0.0F);
                needUpdateUI = true;
            }
            if (newCenterY >= 0 && newCenterY <= getHeight()) {
                mSelectedEntity.getLayer().postTranslate(0.0F, delta.y / getHeight());
                needUpdateUI = true;
            }
            if (needUpdateUI) {
                invalidateCanvas(true);
            }
        }
    }

    private void initialTranslateAndScale(MotionEntity entity) {
        entity.moveToCanvasCenter();
        entity.getLayer().setScale(entity.getLayer().initialScale());
    }

    private void selectEntity(MotionEntity entity) {
        if (mSelectedEntity != null) {
            mSelectedEntity.setIsSelected(false);
        }
        if (entity != null) {
            entity.setIsSelected(true);
        }
        mSelectedEntity = entity;
        invalidateCanvas(true);
    }

    private MotionEntity findEntityAtPoint(float x, float y) {
        MotionEntity selected = null;
        PointF p = new PointF(x, y);
        for (int i = mEntities.size() - 1; i >= 0; i--) {
            if (mEntities.get(i).pointInLayerRect(p)) {
                selected = mEntities.get(i);
                break;
            }
        }
        return selected;
    }

    private void updateSelectionOnTap(MotionEvent e) {
        MotionEntity entity = findEntityAtPoint(e.getX(), e.getY());
        onShapeSelectionChanged(entity);
        selectEntity(entity);
    }

    public void releaseSelectedEntity() {
        MotionEntity toRemoveEntity = null;
        for (MotionEntity entity : mEntities) {
            if (entity.isSelected()) {
                toRemoveEntity = entity;
                break;
            }
        }
        if (toRemoveEntity != null) {
            toRemoveEntity.setIsSelected(false);
            if (mEntities.remove(toRemoveEntity)) {
                toRemoveEntity.release();
                toRemoveEntity = null;
                mSelectedEntity = toRemoveEntity;
                onShapeSelectionChanged(toRemoveEntity);
                invalidateCanvas(true);
            }
        }
    }

    public void deleteShapeById(String entityId) {
        MotionEntity toRemoveEntity = null;
        for (MotionEntity entity : mEntities) {
            if (entity.hasId(entityId)) {
                toRemoveEntity = entity;
                break;
            }
        }
        if (toRemoveEntity != null) {
            toRemoveEntity.setIsSelected(false);
            if (mEntities.remove(toRemoveEntity)) {
                toRemoveEntity.release();
                toRemoveEntity = null;
                mSelectedEntity = toRemoveEntity;
                onShapeSelectionChanged(toRemoveEntity);
                invalidateCanvas(true);
            }
        }
    }

    public void unselectShape() {
        selectEntity(null);
    }

    public void increaseTextEntityFontSize() {
        TextEntity textEntity = getSelectedTextEntity();
        if (textEntity != null) {
            textEntity.getLayer().getFont().increaseSize(TextLayer.Limits.FONT_SIZE_STEP);
            textEntity.updateEntity();
            invalidateCanvas(true);
        }
    }

    public void decreaseTextEntityFontSize() {
        TextEntity textEntity = getSelectedTextEntity();
        if (textEntity != null) {
            textEntity.getLayer().getFont().decreaseSize(TextLayer.Limits.FONT_SIZE_STEP);
            textEntity.updateEntity();
            invalidateCanvas(true);
        }
    }

    public void setTextEntityText(String newText) {
        TextEntity textEntity = getSelectedTextEntity();
        if (textEntity != null && newText != null && newText.length() > 0) {
            textEntity.getLayer().setText(newText);
            textEntity.updateEntity();
            invalidateCanvas(true);
        }
    }

    private TextEntity getSelectedTextEntity() {
        if (mSelectedEntity != null && mSelectedEntity instanceof TextEntity) {
            return (TextEntity) mSelectedEntity;
        } else {
            return null;
        }
    }

    /**
     *
     * Gesture Listeners
     *
     * Connect the gesture detectors to the native touch listener. The
     * JS-PanResponder is disabled while a MotionEntity is selected immediately. The
     * JS-PanResponder is enabled again with a 150ms delay, through the
     * onShapeSelectionChanged event, when the MotionEntity is deselected.
     *
     * The 100-150ms delay is there to ensure no point is drawn when deselecting a
     * shape.
     *
     **/
    private final View.OnTouchListener mOnTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mScaleGestureDetector != null) {
                mGestureDetectorCompat.onTouchEvent(event);
                mScaleGestureDetector.onTouchEvent(event);
                mRotateGestureDetector.onTouchEvent(event);
                mMoveGestureDetector.onTouchEvent(event);
                return true;
            } else {
              return false;
            }
        }
    };

    private class TapsListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (mSelectedEntity != null) {
                return true;
            }
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            // TODO: We may not need this...
            // updateOnLongPress(e);
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            // Update mSelectedEntity. 
            // Fires onShapeSelectionChanged (JS-PanResponder enabling/disabling)
            updateSelectionOnTap(e);
            return true;
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private PointF viewportFocus = new PointF();
        private float lastSpanX;
        private float lastSpanY;
        private float lastSpan;
        private float lastX;
        private float lastY;
        private float slope = -1;

        @Override
        public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
            lastSpanX = scaleGestureDetector.getCurrentSpanX();
            lastSpanY = scaleGestureDetector.getCurrentSpanY();
            lastSpan = scaleGestureDetector.getCurrentSpan();
            lastX = scaleGestureDetector.getFocusX();
            lastY = scaleGestureDetector.getFocusY();
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {
            slope = -1;
        }

        @Override
        public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
            if (mSelectedEntity != null) {
                float spanX = scaleGestureDetector.getCurrentSpanX();
                float spanY = scaleGestureDetector.getCurrentSpanY();
                float span = scaleGestureDetector.getCurrentSpan();
                float currentX = scaleGestureDetector.getFocusX();
                float currentY = scaleGestureDetector.getFocusY();

                // Ensure we don't accidentally scale what was meant to be a rotate
                float diff = Math.abs(span - lastSpan);
                if (diff < 35.0f) {
                    return false;
                }

                float scaleFactorX = spanX / lastSpanX;
                float scaleFactorY = spanY / lastSpanY;

                // Slope is set once per pinch
                if (slope < 0) {
                    if (currentX == lastX) {
                        slope = 1000;
                    } else if (currentY == lastY) {
                        slope = 0;
                    } else {
                        slope = Math.abs((currentY - lastY) / (currentX - lastX));
                    }
                }

                // We set min/max for smooth scrolling
                float MIN_SCALE = 0.985f;
                float MAX_SCALE = 1.015f;
                float scaleFactorXFinal = scaleFactorX < MIN_SCALE ? MIN_SCALE : (scaleFactorX > MAX_SCALE ? MAX_SCALE : scaleFactorX);
                float scaleFactorYFinal = scaleFactorY < MIN_SCALE ? MIN_SCALE : (scaleFactorY > MAX_SCALE ? MAX_SCALE : scaleFactorY);

                if (slope < 0.45f) {
                    mSelectedEntity.getLayer().postScale(scaleFactorXFinal - 1.0f, 0);
                } else if (slope > 3.0f) {
                    mSelectedEntity.getLayer().postScale(0, scaleFactorYFinal - 1.0f);
                } else {
                    mSelectedEntity.getLayer().postScale(scaleFactorXFinal - 1.0f, scaleFactorXFinal - 1.0f);
                }

                invalidateCanvas(true);
                return true;
            }
            return false;
        }
    }

    private class RotateListener extends RotateGestureDetector.SimpleOnRotateGestureListener {
        @Override
        public boolean onRotate(RotateGestureDetector detector) {
            if (mSelectedEntity != null) {
                mSelectedEntity.getLayer().postRotate(-detector.getRotationDegreesDelta());
                invalidateCanvas(true);
                return true;
            }
            return false;
        }
    }

    private class MoveListener extends MoveGestureDetector.SimpleOnMoveGestureListener {
        @Override
        public boolean onMove(MoveGestureDetector detector) {
            if (mSelectedEntity != null) {
                handleTranslate(detector.getFocusDelta());
                return true;
            }
            return false;
        }
    }
}
