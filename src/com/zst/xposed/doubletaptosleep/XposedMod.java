package com.zst.xposed.doubletaptosleep;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.PowerManager;
import android.view.GestureDetector;
import android.view.MotionEvent;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedMod implements IXposedHookLoadPackage {
	
	public final static String LOG_TAG = "DoubleTapToSleep " +
			"(SDK:" + Build.VERSION.SDK_INT +
			"/PHONE:" + Build.PRODUCT +
			"/ROM:" + Build.DISPLAY + "):";

	static final String SYSTEMUI_PACKAGE = "com.android.systemui";

	static GestureDetector mDoubleTapGesture;
	static boolean isIcsAndNewer;
	static boolean isLollipopAndNewer;
	static int mStatusBarHeaderHeight;

	@Override
	public void handleLoadPackage(LoadPackageParam lpp) throws Throwable {
		if (!lpp.packageName.equals(SYSTEMUI_PACKAGE)) return;
		isIcsAndNewer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
		isLollipopAndNewer = Build.VERSION.SDK_INT >= 21; //Build.VERSION_CODES.LOLLIPOP
		try {
			String hookClass;
			if (isLollipopAndNewer)
				hookClass = "com.android.systemui.statusbar.phone.StatusBarWindowView";
			else if (isIcsAndNewer)
				hookClass = "com.android.systemui.statusbar.phone.PhoneStatusBarView";
			else
				hookClass = "com.android.systemui.statusbar.StatusBarView";
			hook(XposedHelpers.findClass(hookClass, lpp.classLoader));
		} catch (Throwable t) {
			XposedBridge.log(LOG_TAG);
			XposedBridge.log(t);
			// To ensure SystemUI doesn't Force-close on boot
			// we catch the error and manually log
		}
	}

	private final XC_MethodHook statusBarViewHook = new XC_MethodHook() {
		@Override
		protected void afterHookedMethod(MethodHookParam param) throws Throwable {
			final Context ctx;
			if (isLollipopAndNewer) {
				ctx = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
				final Resources res = ctx.getResources();
				mStatusBarHeaderHeight = res.getDimensionPixelSize(
						res.getIdentifier("status_bar_header_height", "dimen", SYSTEMUI_PACKAGE));
			} else {
				if (!(param.args[0] instanceof Context)) {
					XposedBridge.log(LOG_TAG + "Couldn't find context: "
											 + param.args[0].getClass().getName());
					return;
				}
				ctx = (Context) param.args[0];
			}
			mDoubleTapGesture = new GestureDetector(ctx,
				new GestureDetector.SimpleOnGestureListener() {
					@Override
					public boolean onDoubleTap(MotionEvent e) {
						PowerManager pm = (PowerManager) ctx
								.getSystemService(Context.POWER_SERVICE);
						if (pm != null) {
							pm.goToSleep(e.getEventTime());
						} else {
							XposedBridge.log(LOG_TAG
									+ " getSystemService returned null PowerManager");
						}
						return true;
					}
				});
		}
	};

	private final XC_MethodHook touchEventHook = new XC_MethodHook() {
		@Override
		protected void afterHookedMethod(MethodHookParam param) throws Throwable {
			MotionEvent event = (MotionEvent) param.args[0];
			if (!isLollipopAndNewer || event.getY() < mStatusBarHeaderHeight)
				mDoubleTapGesture.onTouchEvent(event);
		}
	};

	private void hook(Class<?> clazz) {

		if (isLollipopAndNewer) {
			XposedHelpers.findAndHookMethod(clazz, "onAttachedToWindow", statusBarViewHook);
			XposedBridge.hookAllMethods(clazz, "onInterceptTouchEvent", touchEventHook);
		} else {
			XposedBridge.hookAllConstructors(clazz, statusBarViewHook);
			XposedBridge.hookAllMethods(clazz, "onTouchEvent", touchEventHook);
		}
	}
}
