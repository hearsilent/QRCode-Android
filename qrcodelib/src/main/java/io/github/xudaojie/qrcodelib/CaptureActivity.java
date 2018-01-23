package io.github.xudaojie.qrcodelib;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;

import java.io.IOException;
import java.util.Vector;

import io.github.xudaojie.qrcodelib.zxing.camera.CameraManager;
import io.github.xudaojie.qrcodelib.zxing.decoding.CaptureActivityHandler;
import io.github.xudaojie.qrcodelib.zxing.view.ViewfinderView;

/**
 * Initial the camera
 *
 * @author Ryan.Tang
 */
public abstract class CaptureActivity extends AppCompatActivity
		implements Callback, ViewfinderView.OnDrawListener {

	private static final String TAG = CaptureActivity.class.getSimpleName();

	private static final int REQUEST_PERMISSION_CAMERA = 1000;

	private CaptureActivity mActivity;

	private CaptureActivityHandler handler;
	private ViewfinderView viewfinderView;
	private boolean hasSurface;
	private Vector<BarcodeFormat> decodeFormats;
	private String characterSet;
	private MediaPlayer mediaPlayer;
	private boolean playBeep;
	private static final float BEEP_VOLUME = 0.10f;
	private boolean vibrate;
	private boolean flashLightOpen = false;
	private ImageButton mFlashButton;

	private FrameLayout mHintView;
	private Button mRequestPermissionButton;
	private TextView mHintTextView;

	private Rect mFrame;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		mActivity = this;
		hasSurface = false;
		CameraManager.init(getApplication());
		requestCameraPermission();
		initView();
	}

	private void requestCameraPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (checkSelfPermission(Manifest.permission.CAMERA) !=
					PackageManager.PERMISSION_GRANTED) {
				requestPermissions(new String[]{Manifest.permission.CAMERA},
						REQUEST_PERMISSION_CAMERA);
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.d(TAG, "xxxxxxxxxxxxxxxxxxxonResume");
		SurfaceView surfaceView = findViewById(R.id.view_preview);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			initCamera(surfaceHolder);
		} else {
			surfaceHolder.addCallback(this);
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}
		decodeFormats = null;
		characterSet = null;

		playBeep = true;
		final AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
		if (audioService != null &&
				audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
			playBeep = false;
		}
		initBeepSound();
		vibrate = true;
	}

	@Override
	protected void onPause() {
		super.onPause();
		Log.d(TAG, "xxxxxxxxxxxxxxxxxxxonPause");
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		if (mFlashButton != null) {
			mFlashButton.setActivated(false);
		}
		CameraManager.get().closeDriver();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
	                                       @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (grantResults.length > 0 && requestCode == REQUEST_PERMISSION_CAMERA) {
			setUpHint(false);
		}
	}

	/**
	 * Handler scan result
	 */
	public void handleDecode(Result result, Bitmap barcode) {
		playBeepSoundAndVibrate();
		String resultString = result.getText();
		handleResult(resultString);
	}

	protected void handleResult(String resultString) {
		if (TextUtils.isEmpty(resultString)) {
			Toast.makeText(CaptureActivity.this, R.string.scan_failed, Toast.LENGTH_SHORT).show();
		} else {
			Intent resultIntent = new Intent();
			Bundle bundle = new Bundle();
			bundle.putString("result", resultString);
			resultIntent.putExtras(bundle);
			this.setResult(RESULT_OK, resultIntent);
		}
		mActivity.finish();
	}

	protected void initView() {
		setTheme(R.style.CaptureTheme);
		setContentView(R.layout.activity_capture);

		viewfinderView = findViewById(R.id.view_viewfinder);
		mFlashButton = findViewById(R.id.button_flash);

		mHintView = findViewById(R.id.view_hint);
		mRequestPermissionButton = findViewById(R.id.button_request_permission);
		mHintTextView = findViewById(R.id.textView_hint);

		setUpHint(true);

		mFlashButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				mFlashButton.setActivated(!flashLightOpen);
				toggleFlashLight();
			}
		});
		mRequestPermissionButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View view) {
				if (ActivityCompat.shouldShowRequestPermissionRationale(mActivity,
						Manifest.permission.CAMERA)) {
					requestCameraPermission();
				} else {
					alwaysDeniedCameraPermission();
				}
			}
		});
	}

	public abstract void alwaysDeniedCameraPermission();

	@Override
	public void onDraw(Rect frame) {
		if (mFrame != null && mFrame.equals(frame)) { // skip
			return;
		}
		mFrame = frame;
		if (frame != null) {
			CoordinatorLayout.LayoutParams params =
					((CoordinatorLayout.LayoutParams) mHintView.getLayoutParams());
			params.topMargin = frame.bottom;
			params.leftMargin = frame.left;
			params.rightMargin = frame.right - frame.width();
			mHintView.requestLayout();
		}
	}

	public void setUpHint(boolean firstTime) {
		if (isFinishing()) {
			return;
		}
		if (CameraManager.get().checkCameraPermission() == PackageManager.PERMISSION_GRANTED) {
			mHintTextView.setText(getString(R.string.scan_hint));
			mHintTextView.setVisibility(View.VISIBLE);
			mRequestPermissionButton.setVisibility(View.GONE);
			mFlashButton.setEnabled(true);
		} else {
			// User denied
			if (ActivityCompat
					.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) ||
					firstTime) {
				mHintTextView.setText(getString(R.string.camera_permission_not_granted));
				mHintTextView.setVisibility(firstTime ? View.VISIBLE : View.GONE);
				mRequestPermissionButton.setVisibility(!firstTime ? View.VISIBLE : View.GONE);
			} else { // User choose "Don’t ask again" or Device not support for this permission
				mHintTextView.setVisibility(View.GONE);
				mRequestPermissionButton.setVisibility(View.VISIBLE);
			}
			mFlashButton.setEnabled(false);
		}
	}

	protected void setViewfinderView(ViewfinderView view) {
		viewfinderView = view;
	}

	/**
	 * 切换散光灯状态
	 */
	public void toggleFlashLight() {
		if (flashLightOpen) {
			setFlashLightOpen(false);
		} else {
			setFlashLightOpen(true);
		}
	}

	/**
	 * 设置闪光灯是否打开
	 */
	public void setFlashLightOpen(boolean open) {
		if (flashLightOpen == open) {
			return;
		}

		flashLightOpen = !flashLightOpen;
		CameraManager.get().setFlashLight(open);
	}

	/**
	 * 当前散光灯是否打开
	 */
	public boolean isFlashLightOpen() {
		return flashLightOpen;
	}

	private void initCamera(SurfaceHolder surfaceHolder) {
		try {
			CameraManager.get().openDriver(surfaceHolder);
		} catch (IOException ioe) {
			return;
		} catch (RuntimeException e) {
			return;
		}
		if (handler == null) {
			handler = new CaptureActivityHandler(this, decodeFormats, characterSet);
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;
	}

	public ViewfinderView getViewfinderView() {
		return viewfinderView;
	}

	public Handler getHandler() {
		return handler;
	}

	public void drawViewfinder() {
		viewfinderView.drawViewfinder();
	}

	protected void restartPreview() {
		// 当界面跳转时 handler 可能为null
		if (handler != null) {
			Message restartMessage = Message.obtain();
			restartMessage.what = R.id.restart_preview;
			handler.handleMessage(restartMessage);
		}
	}

	private void initBeepSound() {
		if (playBeep && mediaPlayer == null) {
			// The volume on STREAM_SYSTEM is not adjustable, and users found it
			// too loud,
			// so we now play on the music stream.
			setVolumeControlStream(AudioManager.STREAM_MUSIC);
			mediaPlayer = new MediaPlayer();
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mediaPlayer.setOnCompletionListener(beepListener);

			AssetFileDescriptor file = getResources().openRawResourceFd(R.raw.beep);
			try {
				mediaPlayer.setDataSource(file.getFileDescriptor(), file.getStartOffset(),
						file.getLength());
				file.close();
				mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
				mediaPlayer.prepare();
			} catch (IOException e) {
				mediaPlayer = null;
			}
		}
	}

	private static final long VIBRATE_DURATION = 200L;

	private void playBeepSoundAndVibrate() {
		if (playBeep && mediaPlayer != null) {
			mediaPlayer.start();
		}
		if (vibrate) {
			Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
			if (vibrator != null) {
				vibrator.vibrate(VIBRATE_DURATION);
			}
		}
	}

	/**
	 * When the beep has finished playing, rewind to queue up another one.
	 */
	private final OnCompletionListener beepListener = new OnCompletionListener() {

		public void onCompletion(MediaPlayer mediaPlayer) {
			mediaPlayer.seekTo(0);
		}
	};

}