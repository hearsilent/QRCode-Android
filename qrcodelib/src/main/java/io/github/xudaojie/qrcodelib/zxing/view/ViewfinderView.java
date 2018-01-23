/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.xudaojie.qrcodelib.zxing.view;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

import com.google.zxing.ResultPoint;

import java.util.Collection;
import java.util.HashSet;

import io.github.xudaojie.qrcodelib.R;
import io.github.xudaojie.qrcodelib.zxing.camera.CameraManager;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

	public static int RECT_OFFSET_X; // 扫描区域偏移量 默认位于屏幕中间
	public static int RECT_OFFSET_Y;

	private static final int OPAQUE = 0xFF;
	private static long ANIMATION_DELAY = 10L;

	private final Paint paint;
	private final int resultPointColor;
	private final int angleColor;
	private String hint;
	private int hintColor;
	private String errorHint;
	private int errorHintColor;
	private boolean showPossiblePoint;
	private Bitmap resultBitmap;
	private Collection<ResultPoint> possibleResultPoints;
	private Collection<ResultPoint> lastPossibleResultPoints;

	private float translateY = 5f;
	private int cameraPermission = PackageManager.PERMISSION_DENIED;

	// This constructor is used when the class is built from an XML resource.
	public ViewfinderView(Context context, AttributeSet attrs) {
		super(context, attrs);

		TypedArray typedArray =
				context.obtainStyledAttributes(attrs,R.styleable.qr_ViewfinderView);
		angleColor = Color.WHITE;
		hint = typedArray.getString(R.styleable.qr_ViewfinderView_qr_hint);
		hintColor = typedArray.getColor(R.styleable.qr_ViewfinderView_qr_textHintColor, Color.GRAY);
		errorHint = typedArray.getString(R.styleable.qr_ViewfinderView_qr_errorHint);
		errorHintColor = typedArray
				.getColor(R.styleable.qr_ViewfinderView_qr_textErrorHintColor, Color.WHITE);
		showPossiblePoint =
				typedArray.getBoolean(R.styleable.qr_ViewfinderView_qr_showPossiblePoint, false);

		RECT_OFFSET_X = typedArray.getInt(R.styleable.qr_ViewfinderView_qr_offsetX, 0);
		RECT_OFFSET_Y = typedArray.getInt(R.styleable.qr_ViewfinderView_qr_offsetY, 0);

		if (TextUtils.isEmpty(hint)) {
			hint = "將二维碼/條形碼置於框内即自動掃描";
		}
		if (TextUtils.isEmpty(errorHint)) {
			errorHint = "請允許相機權限後重試";
		}
		if (showPossiblePoint) {
			ANIMATION_DELAY = 100L;
		}

		// Initialize these once for performance rather than calling them every time in onDraw().
		paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		Resources resources = getResources();
		resultPointColor = resources.getColor(R.color.possible_result_points);
		possibleResultPoints = new HashSet<>(5);

		typedArray.recycle();
	}

	@Override
	public void onDraw(Canvas canvas) {
		Rect frame = null;
		if (!isInEditMode()) {
			if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
				cameraPermission = CameraManager.get().checkCameraPermission();
			}
			frame = CameraManager.get().getFramingRect(RECT_OFFSET_X, RECT_OFFSET_Y);
		}

		if (frame == null) {
			// Android Studio中预览时和未获得相机权限时都为null
			int screenWidth = getResources().getDisplayMetrics().widthPixels;
			int screenHeight = getResources().getDisplayMetrics().heightPixels;
			int width = 675;
			int height = 675;
			int leftOffset = (screenWidth - width) / 2;
			int topOffset = (screenHeight - height) / 2;
			frame = new Rect(leftOffset + RECT_OFFSET_X, topOffset + RECT_OFFSET_Y,
					leftOffset + width + RECT_OFFSET_X, topOffset + height + RECT_OFFSET_Y);
			//            return;
		}
		int width = canvas.getWidth();
		int height = canvas.getHeight();

		// Draw the exterior (i.e. outside the framing rect) darkened
		paint.setColor(0x8a000000);
		canvas.drawRect(0, 0, width, frame.top, paint);
		canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
		canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
		canvas.drawRect(0, frame.bottom + 1, width, height, paint);

		drawText(canvas, frame);

		if (resultBitmap != null) {
			// Draw the opaque result bitmap over the scanning rectangle
			paint.setAlpha(OPAQUE);
			canvas.drawBitmap(resultBitmap, frame.left, frame.top, paint);
		} else {
			drawAngle(canvas, frame);
			if (showPossiblePoint) {
				drawPossiblePoint(canvas, frame);
			}

			// Request another update at the animation interval, but only repaint the laser line,
			// not the entire viewfinder mask.
			postInvalidateDelayed(ANIMATION_DELAY, frame.left, frame.top, frame.right,
					frame.bottom);
		}
	}

	public void drawViewfinder() {
		resultBitmap = null;
		invalidate();
	}

	/**
	 * Draw a bitmap with the result points highlighted instead of the live scanning display.
	 *
	 * @param barcode An image of the decoded barcode.
	 */
	public void drawResultBitmap(Bitmap barcode) {
		resultBitmap = barcode;
		invalidate();
	}

	public void addPossibleResultPoint(ResultPoint point) {
		possibleResultPoints.add(point);
	}

	private void drawAngle(Canvas canvas, Rect frame) {
		int angleLength = (int) convertDpToPixel(32, getContext());
		int angleWidth = (int) convertDpToPixel(4, getContext());
		int top = frame.top;
		int bottom = frame.bottom;
		int left = frame.left;
		int right = frame.right;
		float radius = (int) convertDpToPixel(4, getContext());

		paint.setColor(angleColor);
		// 左上
		canvas.drawPath(
				RoundedRect(left - radius, top - radius, left, top, radius, radius, true, false,
						false, false), paint);
		canvas.drawRect(left, top - angleWidth, left + angleLength - radius, top, paint);
		canvas.drawRect(left - angleWidth, top, left, top + angleLength - radius, paint);
		// 左下
		canvas.drawPath(
				RoundedRect(left - radius, bottom, left, bottom + radius, radius, radius, false,
						false, false, true), paint);
		canvas.drawRect(left, bottom, left + angleLength - radius, bottom + angleWidth, paint);
		canvas.drawRect(left - angleWidth, bottom - angleLength + radius, left, bottom, paint);
		// 右上
		canvas.drawPath(
				RoundedRect(right, top - radius, right + radius, top, radius, radius, false, true,
						false, false), paint);
		canvas.drawRect(right - angleLength + radius, top - angleWidth, right, top, paint);
		canvas.drawRect(right, top, right + angleWidth, top + angleLength - radius, paint);
		// 右下
		canvas.drawPath(
				RoundedRect(right, bottom, right + radius, bottom + radius, radius, radius, false,
						false, true, false), paint);
		canvas.drawRect(right - angleLength + radius, bottom, right, bottom + angleWidth, paint);
		canvas.drawRect(right, bottom - angleLength + radius, right + angleWidth, bottom, paint);
	}

	public static Path RoundedRect(float left, float top, float right, float bottom, float rx,
	                               float ry, boolean tl, boolean tr, boolean br, boolean bl) {
		Path path = new Path();
		if (rx < 0) {
			rx = 0;
		}
		if (ry < 0) {
			ry = 0;
		}
		float width = right - left;
		float height = bottom - top;
		if (rx > width / 2) {
			rx = width / 2;
		}
		if (ry > height / 2) {
			ry = height / 2;
		}
		float widthMinusCorners = (width - (2 * rx));
		float heightMinusCorners = (height - (2 * ry));

		path.moveTo(right, top + ry);
		if (tr) {
			path.rQuadTo(0, -ry, -rx, -ry);//top-right corner
		} else {
			path.rLineTo(0, -ry);
			path.rLineTo(-rx, 0);
		}
		path.rLineTo(-widthMinusCorners, 0);
		if (tl) {
			path.rQuadTo(-rx, 0, -rx, ry); //top-left corner
		} else {
			path.rLineTo(-rx, 0);
			path.rLineTo(0, ry);
		}
		path.rLineTo(0, heightMinusCorners);

		if (bl) {
			path.rQuadTo(0, ry, rx, ry);//bottom-left corner
		} else {
			path.rLineTo(0, ry);
			path.rLineTo(rx, 0);
		}

		path.rLineTo(widthMinusCorners, 0);
		if (br) {
			path.rQuadTo(rx, 0, rx, -ry); //bottom-right corner
		} else {
			path.rLineTo(rx, 0);
			path.rLineTo(0, -ry);
		}

		path.rLineTo(0, -heightMinusCorners);

		path.close();//Given close, last lineto can be removed.

		return path;
	}

	public float convertDpToPixel(float dp, Context context) {
		return dp * (getDisplayMetrics(context).densityDpi / 160f);
	}

	public DisplayMetrics getDisplayMetrics(Context context) {
		Resources resources = context.getResources();
		return resources.getDisplayMetrics();
	}

	private void drawText(Canvas canvas, Rect frame) {
		boolean isPermissionGranted = cameraPermission == PackageManager.PERMISSION_GRANTED;

		TextPaint mTextPaint = new TextPaint();
		mTextPaint.setColor(isPermissionGranted ? hintColor : errorHintColor);
		mTextPaint.setTextSize(convertDpToPixel(14, getContext()));
		StaticLayout mTextLayout =
				new StaticLayout(isPermissionGranted ? hint : errorHint, mTextPaint,
						frame.right - frame.left, Layout.Alignment.ALIGN_CENTER, 1.1f, 0.0f, false);

		canvas.save();

		canvas.translate(frame.left, frame.bottom + convertDpToPixel(32, getContext()));
		mTextLayout.draw(canvas);
		canvas.restore();
	}

	// Draw a yellow "possible points"
	private void drawPossiblePoint(Canvas canvas, Rect frame) {
		Collection<ResultPoint> currentPossible = possibleResultPoints;
		Collection<ResultPoint> currentLast = lastPossibleResultPoints;
		if (currentPossible.isEmpty()) {
			lastPossibleResultPoints = null;
		} else {
			possibleResultPoints = new HashSet<>(5);
			lastPossibleResultPoints = currentPossible;
			paint.setAlpha(OPAQUE);
			paint.setColor(resultPointColor);
			for (ResultPoint point : currentPossible) {
				canvas.drawCircle(frame.left + point.getX(), frame.top + point.getY(), 6.0f, paint);
			}
		}
		if (currentLast != null) {
			paint.setAlpha(OPAQUE / 2);
			paint.setColor(resultPointColor);
			for (ResultPoint point : currentLast) {
				canvas.drawCircle(frame.left + point.getX(), frame.top + point.getY(), 3.0f, paint);
			}
		}
	}
}
