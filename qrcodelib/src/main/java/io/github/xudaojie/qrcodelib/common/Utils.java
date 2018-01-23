package io.github.xudaojie.qrcodelib.common;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;

public class Utils {

	public static float convertDpToPixel(float dp, Context context) {
		return dp * (getDisplayMetrics(context).densityDpi / 160f);
	}

	public static DisplayMetrics getDisplayMetrics(Context context) {
		Resources resources = context.getResources();
		return resources.getDisplayMetrics();
	}

}
