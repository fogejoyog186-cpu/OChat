package com.changyu.ochat;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.WindowManager;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** One inset owner per content root; padding is based on the original values, never accumulated. */
final class ScreenInsets {
    static final int TOP_BAR_DP = 72;
    static void apply(Activity activity, View root) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
        activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
        boolean light = androidx.core.graphics.ColorUtils.calculateLuminance(ThemePalette.resolve(activity).page) > .5;
        WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView()).setAppearanceLightStatusBars(light);
        WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView()).setAppearanceLightNavigationBars(light);
        final int left=root.getPaddingLeft(), top=root.getPaddingTop(), right=root.getPaddingRight(), bottom=root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime=insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(left+bars.left, top+bars.top, right+bars.right, bottom+Math.max(bars.bottom, ime.bottom));
            return insets;
        });
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) { ViewCompat.requestApplyInsets(v); }
            public void onViewDetachedFromWindow(View v) { }
        });
        if (ViewCompat.isAttachedToWindow(root)) ViewCompat.requestApplyInsets(root);
    }
}
