package com.limelight.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Compact, hidden-by-default floating productivity pill dock providing quick one-tap access
 * to Mic toggle, soft keyboard, clipboard paste, Esc, GameMenu, and hide.
 */
public class WorkDockController {

    public interface Actions {
        void onToggleMic();
        void onToggleKeyboard();
        void onPasteClipboard();
        void onSendEsc();
        void onOpenMenu();
        void onVisibilityChanged(boolean visible);
    }

    private final Context context;
    private final FrameLayout parentContainer;
    private final Actions actions;

    private LinearLayout dockBar;
    private TextView micButton;
    private boolean visible;

    public WorkDockController(Context context, FrameLayout parentContainer, Actions actions) {
        this.context = context;
        this.parentContainer = parentContainer;
        this.actions = actions;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean show, boolean micActive) {
        if (parentContainer == null) {
            return;
        }
        ensureCreated();
        visible = show;
        dockBar.setVisibility(show ? View.VISIBLE : View.GONE);
        updateMicState(micActive);
        if (actions != null) {
            actions.onVisibilityChanged(show);
        }
    }

    public void toggle(boolean micActive) {
        setVisible(!visible, micActive);
    }

    public void updateMicState(boolean micActive) {
        if (micButton == null) {
            return;
        }
        micButton.setText(micActive ? "Mic ON" : "Mic");
        micButton.setTextColor(micActive ? Color.parseColor("#86EFAC") : Color.WHITE);
    }

    public void destroy() {
        if (dockBar != null && dockBar.getParent() instanceof ViewGroup) {
            ((ViewGroup) dockBar.getParent()).removeView(dockBar);
        }
        dockBar = null;
        micButton = null;
        visible = false;
    }

    private void ensureCreated() {
        if (dockBar != null) {
            return;
        }

        dockBar = new LinearLayout(context);
        dockBar.setOrientation(LinearLayout.HORIZONTAL);
        dockBar.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dp(10);
        int padV = dp(4);
        dockBar.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(205, 22, 24, 30));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.argb(80, 255, 255, 255));
        dockBar.setBackground(bg);
        dockBar.setVisibility(View.GONE);

        micButton = addPillButton("Mic", () -> {
            if (actions != null) {
                actions.onToggleMic();
            }
        });
        addPillButton("KB", () -> {
            if (actions != null) {
                actions.onToggleKeyboard();
            }
        });
        addPillButton("Paste", () -> {
            if (actions != null) {
                actions.onPasteClipboard();
            }
        });
        addPillButton("Esc", () -> {
            if (actions != null) {
                actions.onSendEsc();
            }
        });
        addPillButton("Menu", () -> {
            if (actions != null) {
                actions.onOpenMenu();
            }
        });
        addPillButton("×", () -> setVisible(false, false));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = dp(10);
        parentContainer.addView(dockBar, lp);
    }

    private TextView addPillButton(String label, Runnable onClick) {
        TextView btn = new TextView(context);
        btn.setText(label);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setPadding(dp(9), dp(4), dp(9), dp(4));
        btn.setOnClickListener(v -> onClick.run());
        dockBar.addView(btn);
        return btn;
    }

    private int dp(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics()));
    }
}
