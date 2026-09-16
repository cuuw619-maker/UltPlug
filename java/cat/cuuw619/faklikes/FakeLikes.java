package cat.cuuw619.faklikes;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import cat.narezany.margyt.plugin.MargyPlugin;

/**
 * Local-only fake like counter for TikTok/Musically.
 *
 * We only accept TikTok's explicit tv_like_count resource. The server value is
 * never changed; only the visible TextView is replaced in the current process.
 */
public final class FakeLikes extends MargyPlugin {

    private static final String PREF_ENABLED = "enabled";
    private static final String PREF_LIKES = "fake_likes";
    private static final int DEFAULT_LIKES = 125000;
    private static final long ANIMATION_MS = 420L;

    private Activity currentActivity;
    private boolean scanPending;

    @Override
    public void onStart(Context context) {
        if (!margyt().prefs().contains(PREF_ENABLED)) {
            margyt().prefs().edit()
                    .putBoolean(PREF_ENABLED, true)
                    .putInt(PREF_LIKES, DEFAULT_LIKES)
                    .apply();
        }
        margyt().log("Fake Likes active; target=" + getFakeLikes());
    }

    @Override
    public void onActivityResumed(final Activity activity) {
        currentActivity = activity;
        scheduleScan(activity, 250L);
    }

    @Override
    public void onActivityCreated(final Activity activity) {
        scheduleScan(activity, 500L);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (currentActivity == activity) {
            currentActivity = null;
        }
    }

    private void scheduleScan(final Activity activity, long delay) {
        if (!isEnabled() || scanPending || activity == null) {
            return;
        }

        scanPending = true;
        activity.getWindow().getDecorView().postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (currentActivity == activity && !activity.isFinishing()) {
                        scanActivity(activity);
                    }
                } catch (Throwable t) {
                    margyt().log("Fake Likes error: " + t.getClass().getSimpleName());
                } finally {
                    scanPending = false;
                }
            }
        }, delay);
    }

    private boolean isEnabled() {
        return margyt().prefs().getBoolean(PREF_ENABLED, true);
    }

    private int getFakeLikes() {
        return Math.max(0, margyt().prefs().getInt(PREF_LIKES, DEFAULT_LIKES));
    }

    private void scanActivity(Activity activity) {
        scanView(activity.getWindow().getDecorView());
    }

    private void scanView(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (isLikeCounter(text)) {
                replaceCounter(text, getFakeLikes());
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                scanView(group.getChildAt(i));
            }
        }
    }

    private boolean isLikeCounter(TextView view) {
        int id = view.getId();
        if (id == View.NO_ID) {
            return false;
        }

        try {
            String resource = view.getResources().getResourceName(id);
            return resource.endsWith(":id/tv_like_count")
                    || resource.endsWith("/tv_like_count");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void replaceCounter(final TextView view, final int target) {
        if (!view.isAttachedToWindow() || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return;
        }

        Object oldTag = view.getTag();
        if (oldTag instanceof Integer && ((Integer) oldTag) == target) {
            return;
        }
        view.setTag(target);

        final int oldValue = parseDisplayedNumber(view.getText());
        if (oldValue < 0 || oldValue == target) {
            view.setText(formatCount(target));
            pulseLikeIcon(view);
            return;
        }

        ValueAnimator animator = ValueAnimator.ofInt(oldValue, target);
        animator.setDuration(ANIMATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            int value = (Integer) animation.getAnimatedValue();
            view.setText(formatCount(value));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (view.isAttachedToWindow()) {
                    view.setText(formatCount(target));
                }
                pulseLikeIcon(view);
            }
        });
        animator.start();
    }

    private int parseDisplayedNumber(CharSequence value) {
        if (value == null) {
            return -1;
        }
        String text = value.toString().trim().replace(" ", "").replace(",", "");
        try {
            if (text.matches("\\d+")) {
                long number = Long.parseLong(text);
                return number > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) number;
            }
            if (text.matches("[0-9]+[KkMmBb]")) {
                char suffix = Character.toLowerCase(text.charAt(text.length() - 1));
                double number = Double.parseDouble(text.substring(0, text.length() - 1));
                double multiplier = suffix == 'k' ? 1_000d
                        : suffix == 'm' ? 1_000_000d : 1_000_000_000d;
                double result = number * multiplier;
                return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private String formatCount(int value) {
        if (value < 1000) {
            return String.valueOf(value);
        }
        if (value < 1_000_000) {
            return compact(value, 1000d, "K");
        }
        if (value < 1_000_000_000) {
            return compact(value, 1_000_000d, "M");
        }
        return compact(value, 1_000_000_000d, "B");
    }

    private String compact(int value, double divisor, String suffix) {
        double n = value / divisor;
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat format = new DecimalFormat(n >= 100 ? "0" : "0.0", symbols);
        String result = format.format(n);
        if (result.endsWith(".0")) {
            result = result.substring(0, result.length() - 2);
        }
        return result + suffix;
    }

    private void pulseLikeIcon(TextView counter) {
        ViewGroup parent = parentOf(counter);
        if (parent == null) {
            return;
        }

        ImageView candidate = null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ImageView && child.getVisibility() == View.VISIBLE) {
                candidate = (ImageView) child;
                break;
            }
        }

        if (candidate == null) {
            return;
        }

        final ImageView icon = candidate;
        icon.animate()
                .scaleX(1.10f)
                .scaleY(1.10f)
                .setDuration(90L)
                .withEndAction(() -> icon.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(150L)
                        .start())
                .start();
    }

    private ViewGroup parentOf(View view) {
        Object parent = view.getParent();
        return parent instanceof ViewGroup ? (ViewGroup) parent : null;
    }
}
