package com.lxj.xpopup.util;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;

import com.lxj.xpopup.core.BasePopupView;

import java.util.List;

/**
 * 键盘工具类，适配 Android 15 Edge-to-Edge
 */
public final class KeyboardUtils {
    public static int sDecorViewInvisibleHeightPre;
    private static final SparseArray<Object> listenerArray = new SparseArray<>();

    private KeyboardUtils() {
        throw new UnsupportedOperationException("u can't instantiate me...");
    }

    private static int sDecorViewDelta = 0;

    private static int getDecorViewInvisibleHeight(final Window window) {
        final View decorView = window.getDecorView();
        final Rect outRect = new Rect();
        decorView.getWindowVisibleDisplayFrame(outRect);
        int delta = Math.abs(decorView.getBottom() - outRect.bottom);
        if (delta <= XPopupUtils.getNavBarHeight(window) + XPopupUtils.getStatusBarHeight(window)) {
            sDecorViewDelta = delta;
            return 0;
        }
        return delta - sDecorViewDelta;
    }

    /**
     * 注册键盘高度变化监听。
     * API 30+ 使用 WindowInsetsAnimation 精确追踪键盘动画。
     * API 30 以下使用 GlobalLayoutListener 方案。
     */
    public static void registerSoftInputChangedListener(final Window window, final BasePopupView popupView, final OnSoftInputChangedListener listener) {
        if (popupView == null || window == null) return;

        if (Build.VERSION.SDK_INT >= 30) {
            // Android 11+ 使用 WindowInsetsAnimation 精确追踪键盘
            View decorView = window.getDecorView();
            WindowInsetsAnimationCompat.Callback animCallback = new WindowInsetsAnimationCompat.Callback(
                    WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP) {

                @NonNull
                @Override
                public WindowInsetsCompat onProgress(@NonNull WindowInsetsCompat insets,
                                                     @NonNull List<WindowInsetsAnimationCompat> runningAnimations) {
                    Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
                    Insets navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                    int keyboardHeight = Math.max(0, imeInsets.bottom - navInsets.bottom);
                    listener.onSoftInputChanged(keyboardHeight);
                    return insets;
                }
            };
            ViewCompat.setWindowInsetsAnimationCallback(decorView, animCallback);
            listenerArray.append(popupView.getId(), animCallback);

            // 同时设置一个 OnApplyWindowInsetsListener 来处理初始状态
            ViewCompat.setOnApplyWindowInsetsListener(decorView, (v, insets) -> {
                Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
                Insets navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                int keyboardHeight = Math.max(0, imeInsets.bottom - navInsets.bottom);
                if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
                    listener.onSoftInputChanged(keyboardHeight);
                } else {
                    listener.onSoftInputChanged(0);
                }
                return insets;
            });
        } else {
            // Android 10 及以下，保留原有 GlobalLayoutListener 方案
            final int flags = window.getAttributes().flags;
            if ((flags & WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            }
            final FrameLayout contentView = window.findViewById(android.R.id.content);
            final int[] decorViewInvisibleHeightPre = {getDecorViewInvisibleHeight(window)};
            ViewTreeObserver.OnGlobalLayoutListener onGlobalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    int height = getDecorViewInvisibleHeight(window);
                    if (decorViewInvisibleHeightPre[0] != height) {
                        listener.onSoftInputChanged(height);
                        decorViewInvisibleHeightPre[0] = height;
                    }
                }
            };
            contentView.getViewTreeObserver().removeOnGlobalLayoutListener(onGlobalLayoutListener);
            contentView.getViewTreeObserver().addOnGlobalLayoutListener(onGlobalLayoutListener);
            listenerArray.append(popupView.getId(), onGlobalLayoutListener);
        }
    }

    public static void removeLayoutChangeListener(Window window, BasePopupView popupView) {
        if (popupView == null || window == null) return;

        if (Build.VERSION.SDK_INT >= 30) {
            View decorView = window.getDecorView();
            ViewCompat.setWindowInsetsAnimationCallback(decorView, null);
            ViewCompat.setOnApplyWindowInsetsListener(decorView, null);
            listenerArray.remove(popupView.getId());
        } else {
            final View contentView = window.findViewById(android.R.id.content);
            if (contentView == null) return;
            Object tag = listenerArray.get(popupView.getId());
            if (tag instanceof ViewTreeObserver.OnGlobalLayoutListener) {
                contentView.getViewTreeObserver().removeOnGlobalLayoutListener(
                        (ViewTreeObserver.OnGlobalLayoutListener) tag);
                listenerArray.remove(popupView.getId());
            }
        }
    }

    public static void showSoftInput(final View view) {
        if (view == null) return;
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.requestFocus();
        imm.showSoftInput(view, 0, new SoftInputReceiver(view.getContext()));
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
    }

    private static class SoftInputReceiver extends ResultReceiver {
        private Context context;

        public SoftInputReceiver(Context context) {
            super(new Handler());
            this.context = context;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);
            if (resultCode == InputMethodManager.RESULT_UNCHANGED_HIDDEN
                    || resultCode == InputMethodManager.RESULT_HIDDEN) {
                toggleSoftInput(context);
            }
            context = null;
        }
    }

    public static void toggleSoftInput(Context context) {
        if (context == null) return;
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        imm.toggleSoftInput(0, 0);
    }

    public static void hideSoftInput(View view) {
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void hideSoftInput(@NonNull final Window window) {
        View view = window.getCurrentFocus();
        if (view == null) {
            View decorView = window.getDecorView();
            View focusView = decorView.findViewWithTag("keyboardTagView");
            if (focusView == null) {
                view = new EditText(window.getContext());
                view.setTag("keyboardTagView");
                ((ViewGroup) decorView).addView(view, 0, 0);
            } else {
                view = focusView;
            }
            view.requestFocus();
        }
        hideSoftInput(view);
    }

    public interface OnSoftInputChangedListener {
        void onSoftInputChanged(int height);
    }
}
