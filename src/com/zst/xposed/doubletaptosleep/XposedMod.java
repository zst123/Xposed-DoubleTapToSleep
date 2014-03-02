package com.zst.xposed.doubletaptosleep;

import android.content.Context;
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
	
	static GestureDetector mDoubleTapGesture;
	
	@Override
	public void handleLoadPackage(LoadPackageParam lpp) throws Throwable {
		if (!lpp.packageName.equals("com.android.systemui")) return;
		try {
			final Class<?> classPhoneStatusBarView = XposedHelpers.findClass(
					"com.android.systemui.statusbar.phone.PhoneStatusBarView", lpp.classLoader);
			hook(classPhoneStatusBarView);
			
		} catch (Throwable t) {
			XposedBridge.log(LOG_TAG);
			XposedBridge.log(t);
			// To ensure SystemUI doesn't Force-close on boot
			// we catch the error and manually log
		}
	}
	
	private void hook(Class<?> clazz) {
		XposedBridge.hookAllConstructors(clazz, new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if (!(param.args[0] instanceof Context)) {
					XposedBridge.log(LOG_TAG + "Couldn't find context: "
							+ param.args[0].getClass().getName());
					return;
				}
				
				final Context ctx = (Context) param.args[0];
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
		});
		
		XposedBridge.hookAllMethods(clazz, "onTouchEvent", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				MotionEvent event = (MotionEvent) param.args[0];
				mDoubleTapGesture.onTouchEvent(event);
			}
		});
	}
}
