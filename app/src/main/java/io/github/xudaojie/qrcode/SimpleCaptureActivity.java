package io.github.xudaojie.qrcode;

import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.widget.Toast;

import io.github.xudaojie.qrcodelib.CaptureActivity;

public class SimpleCaptureActivity extends CaptureActivity {

	private static final int PERMISSION_REQUEST_SETTINGS = 201;

	private AlertDialog mDialog;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void handleResult(String resultString) {
		if (TextUtils.isEmpty(resultString)) {
			Toast.makeText(this, io.github.xudaojie.qrcodelib.R.string.scan_failed,
					Toast.LENGTH_SHORT).show();
			restartPreview();
		} else {
			if (mDialog == null) {
				mDialog =
						new AlertDialog.Builder(SimpleCaptureActivity.this).setMessage(resultString)
								.setPositiveButton(android.R.string.ok, null).create();
				mDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {

					@Override
					public void onDismiss(DialogInterface dialog) {
						restartPreview();
					}
				});
			}
			if (!mDialog.isShowing()) {
				mDialog.setMessage(resultString);
				mDialog.show();
			}
		}
	}

	@Override
	public void alwaysDeniedCameraPermission() {
		startSettingsDetailActivity();
	}

	private void startSettingsDetailActivity() {
		try {
			Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
					.setData(Uri.parse("package:" + getPackageName()));
			startActivityForResult(intent, PERMISSION_REQUEST_SETTINGS);
		} catch (ActivityNotFoundException e) {
			Intent intent = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
			startActivityForResult(intent, PERMISSION_REQUEST_SETTINGS);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == PERMISSION_REQUEST_SETTINGS) {
			setUpHint(false);
		}
	}

}
