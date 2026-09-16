package cat.cuuw619.faklikes;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import cat.narezany.margyt.plugin.MargyPlugin;

/**
 * MargyT Fake Likes.
 *
 * Development build: discovers numeric TextViews on the currently resumed
 * Activity and records candidates in the MargyT diary. It does not blindly
 * modify every number because TikTok's view hierarchy varies between builds.
 */
public final class FakeLikes extends MargyPlugin {

    private static final String PREF_ENABLED = "enabled";
    private static final String PREF_LIKES = "fake_likes";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Activity currentActivity;
    private boolean scanPending;

    @Override
    public void onStart(Context context) {
        if (!margyt().prefs().contains(PREF_ENABLED)) {
            margyt().prefs().edit()
                    .putBoolean(PREF_ENABLED, true)
                    .putInt(PREF_LIKES, 125000)
                    .apply();
        }
        margyt().log("Fake Likes started; fake_likes=" + getFakeLikes());
    }

    @Override
    public void onActivityResumed(final Activity activity) {
        currentActivity = activity;

        if (!isEnabled() || scanPending) {
            return;
        }

        scanPending = true;
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (currentActivity == activity && activity != null
                            && !activity.isFinishing()) {
                        scanActivity(activity);
                    }
                } catch (Throwable t) {
                    margyt().log("Fake Likes scan error: "
                            + t.getClass().getSimpleName());
                } finally {
                    scanPending = false;
                }
            }
        }, 300L);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (currentActivity == activity) {
            currentActivity = null;
        }
    }

    private boolean isEnabled() {
        return margyt().prefs().getBoolean(PREF_ENABLED, true);
    }

    private int getFakeLikes() {
        return margyt().prefs().getInt(PREF_LIKES, 125000);
    }

    private void scanActivity(Activity activity) {
        View root = activity.getWindow().getDecorView();
        scanView(root, 0);
    }

    private void scanView(View view, int depth) {
        if (view instanceof TextView) {
            inspectTextView((TextView) view, depth);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                scanView(group.getChildAt(i), depth + 1);
            }
        }
    }

    private void inspectTextView(TextView textView, int depth) {
        CharSequence cs = textView.getText();
        if (cs == null) {
            return;
        }

        String text = cs.toString().trim();
        if (!looksLikeCounter(text)) {
            return;
        }

        int id = textView.getId();
        String resourceName = "-";
        if (id != View.NO_ID) {
            try {
                resourceName = textView.getResources().getResourceName(id);
            } catch (Throwable ignored) {
                // Some dynamically created IDs have no resource name.
            }
        }

        margyt().log("Fake Likes candidate: text=\"" + text
                + "\" class=" + textView.getClass().getName()
                + " id=" + id
                + " res=" + resourceName
                + " x=" + textView.getX()
                + " y=" + textView.getY()
                + " w=" + textView.getWidth()
                + " h=" + textView.getHeight()
                + " depth=" + depth);
    }

    private boolean looksLikeCounter(String text) {
        String normalized = text.replace(",", "")
                .replace(".", "")
                .replace(" ", "");

        if (normalized.matches("\\d+")) {
            return true;
        }

        return normalized.matches("\\d+[KkMmBb]");
    }
}
