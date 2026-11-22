package app.bridgelink.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;

import app.bridgelink.android.protocol.StageSix;

/** Turns mirror clicks into phone taps. It never unlocks the phone. */
public final class TouchGate extends AccessibilityService {
    private static volatile TouchGate live;

    public static boolean on() {
        return live != null;
    }

    public static String perform(android.content.Context context, StageSix.Tap tap) {
        TouchGate gate = live;
        if (context == null || gate == null || tap == null) return StageSix.CONTROL_STEPS;
        int[] size = screen(gate);
        int[] start = StageSix.phonePixel(size[0], size[1], tap.x, tap.y);
        int[] end = StageSix.phonePixel(size[0], size[1], tap.x2, tap.y2);
        boolean ok = switch (tap.kind) {
            case "back" -> gate.performGlobalAction(GLOBAL_ACTION_BACK);
            case "home" -> gate.performGlobalAction(GLOBAL_ACTION_HOME);
            case "recents" -> gate.performGlobalAction(GLOBAL_ACTION_RECENTS);
            case "text" -> gate.type(tap.text);
            case "drag", "scroll" -> gate.stroke(start[0], start[1], end[0], end[1], 180);
            default -> gate.stroke(start[0], start[1], start[0], start[1], 40);
        };
        if ("text".equals(tap.kind) && !ok) return "Tap a text field on the phone, then type again.";
        return ok ? "Sent" : "That touch did not land.";
    }

    /** The real screen, including the system bars. Older phones use the real metrics. */
    @SuppressWarnings("deprecation")
    static int[] screen(Context context) {
        WindowManager window = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (window != null && Build.VERSION.SDK_INT >= 30) {
            Rect bounds = window.getMaximumWindowMetrics().getBounds();
            return new int[] {bounds.width(), bounds.height()};
        }
        if (window != null) {
            DisplayMetrics metrics = new DisplayMetrics();
            window.getDefaultDisplay().getRealMetrics(metrics);
            return new int[] {metrics.widthPixels, metrics.heightPixels};
        }
        DisplayMetrics fallback = context.getResources().getDisplayMetrics();
        return new int[] {fallback.widthPixels, fallback.heightPixels};
    }

    @Override
    protected void onServiceConnected() {
        live = this;
    }

    @Override
    public void onDestroy() {
        if (live == this) live = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    private boolean stroke(int x1, int y1, int x2, int y2, long ms) {
        Path path = new Path();
        path.moveTo(x1, y1);
        if (x1 != x2 || y1 != y2) path.lineTo(Math.max(0, x2), Math.max(0, y2));
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, Math.max(1, ms));
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        return dispatchGesture(gesture, null, null);
    }

    private boolean type(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focus == null) focus = findEdit(root);
        if (focus == null) return false;
        CharSequence existing = focus.getText();
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                (existing == null ? "" : existing.toString()) + text);
        return focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private static AccessibilityNodeInfo findEdit(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findEdit(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }
}
