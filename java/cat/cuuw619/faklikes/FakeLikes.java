package cat.cuuw619.faklikes;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.WeakHashMap;

import cat.narezany.margyt.plugin.MargyPlugin;

/**
 * Zftoz MultiTool for MargyT.
 * Local-only fake like counter UI. No server value is changed.
 */
public final class FakeLikes extends MargyPlugin {

    private static final String PREF_ENABLED = "enabled";
    private static final String PREF_LIKES = "fake_likes";
    private static final int DEFAULT_LIKES = 125000;
    private static final long ANIMATION_MS = 420L;
    private static final String BUTTON_TAG = "zftoz_profile_button";

    private Activity currentActivity;
    private boolean scanPending;
    private final WeakHashMap<TextView, TextWatcher> watchers = new WeakHashMap<>();
    private final WeakHashMap<TextView, ValueAnimator> animators = new WeakHashMap<>();

    @Override
    public void onStart(Context context) {
        if (!margyt().prefs().contains(PREF_ENABLED)) {
            margyt().prefs().edit()
                    .putBoolean(PREF_ENABLED, true)
                    .putInt(PREF_LIKES, DEFAULT_LIKES)
                    .apply();
        }
        margyt().log("Zftoz_READY version=2.1.0 target=" + getFakeLikes());
    }

    @Override
    public void onActivityCreated(final Activity activity) {
        currentActivity = activity;
        margyt().log("Zftoz_ACTIVITY created=" + activity.getClass().getName());
        scheduleScan(activity, 450L);
    }

    @Override
    public void onActivityResumed(final Activity activity) {
        currentActivity = activity;
        margyt().log("Zftoz_ACTIVITY resumed=" + activity.getClass().getName());
        scheduleScan(activity, 180L);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (currentActivity == activity) {
            currentActivity = null;
        }
    }

    @Override
    public void onStop() {
        for (ValueAnimator animator : animators.values()) {
            if (animator != null) {
                animator.cancel();
            }
        }
        animators.clear();
        removeProfileButtons();
        currentActivity = null;
        margyt().log("Zftoz_STOP");
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
                    margyt().log("Zftoz_ERROR scan=" + t.getClass().getSimpleName());
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
        View decor = activity.getWindow().getDecorView();
        scanView(decor);
        boolean profile = isProfileActivity(activity, decor);
        margyt().log("Zftoz_PROFILE_CHECK activity=" + activity.getClass().getName()
                + " result=" + profile);
        if (profile) {
            ensureProfileButton(activity);
        } else {
            removeProfileButton(activity);
        }
    }

    private void scanView(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (isLikeCounter(text)) {
                attachLikeWatcher(text);
                applyCounter(text, getFakeLikes(), true);
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
            return resource.endsWith(":id/tv_like_count") || resource.endsWith("/tv_like_count");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void attachLikeWatcher(final TextView view) {
        if (watchers.containsKey(view)) {
            return;
        }
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                Object guard = view.getTag();
                if (Boolean.TRUE.equals(guard)) {
                    return;
                }
                String observed = s == null ? "" : s.toString();
                if (!observed.equals(formatCount(getFakeLikes()))) {
                    margyt().log("Zftoz_LIKE_REWRITE observed=" + observed + " restoring=" + getFakeLikes());
                    applyCounter(view, getFakeLikes(), false);
                }
            }
        };
        view.addTextChangedListener(watcher);
        watchers.put(view, watcher);
        margyt().log("Zftoz_LIKE_WATCH attached=true");
    }

    private void applyCounter(final TextView view, final int target, boolean animate) {
        if (!view.isAttachedToWindow() || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return;
        }

        String targetText = formatCount(target);
        String currentText = view.getText() == null ? "" : view.getText().toString();
        if (currentText.equals(targetText)) {
            return;
        }

        ValueAnimator old = animators.get(view);
        if (old != null) {
            old.cancel();
        }

        final int start = parseDisplayedNumber(view.getText());
        margyt().log("Zftoz_LIKE_FOUND text=" + currentText + " target=" + target);

        if (!animate || start < 0 || start == target) {
            setCounterText(view, targetText);
            pulseLikeIcon(view);
            margyt().log("Zftoz_LIKE_APPLY old=" + currentText + " new=" + targetText + " animated=false");
            return;
        }

        ValueAnimator animator = ValueAnimator.ofInt(start, target);
        animators.put(view, animator);
        animator.setDuration(ANIMATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int value = (Integer) animation.getAnimatedValue();
                setCounterText(view, formatCount(value));
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setCounterText(view, formatCount(target));
                animators.remove(view);
                pulseLikeIcon(view);
                margyt().log("Zftoz_ANIM end=" + target);
            }
            @Override
            public void onAnimationStart(Animator animation) {
                margyt().log("Zftoz_ANIM start=" + start + " end=" + target);
            }
        });
        animator.start();
    }

    private void setCounterText(TextView view, String value) {
        view.setTag(Boolean.TRUE);
        try {
            view.setText(value);
        } finally {
            view.setTag(Boolean.FALSE);
        }
    }

    private int parseDisplayedNumber(CharSequence value) {
        if (value == null) return -1;
        String text = value.toString().trim().replace(" ", "").replace(",", "");
        try {
            if (text.matches("\\d+")) {
                long n = Long.parseLong(text);
                return n > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) n;
            }
            if (text.matches("[0-9]+(?:\\.[0-9]+)?[KkMmBb]")) {
                char suffix = Character.toLowerCase(text.charAt(text.length() - 1));
                double n = Double.parseDouble(text.substring(0, text.length() - 1));
                double multiplier = suffix == 'k' ? 1000d : suffix == 'm' ? 1000000d : 1000000000d;
                double result = n * multiplier;
                return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
            }
        } catch (Throwable ignored) { }
        return -1;
    }

    private String formatCount(int value) {
        if (value < 1000) return String.valueOf(value);
        if (value < 1000000) return compact(value, 1000d, "K");
        if (value < 1000000000) return compact(value, 1000000d, "M");
        return compact(value, 1000000000d, "B");
    }

    private String compact(int value, double divisor, String suffix) {
        double n = value / divisor;
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat format = new DecimalFormat(n >= 100 ? "0" : "0.0", symbols);
        String result = format.format(n);
        if (result.endsWith(".0")) result = result.substring(0, result.length() - 2);
        return result + suffix;
    }

    private boolean isProfileActivity(Activity activity, View root) {
        String name = activity.getClass().getName().toLowerCase(Locale.US);
        if (name.contains("profile")) return true;
        return hierarchyContainsProfile(root, 0, 9);
    }

    private boolean hierarchyContainsProfile(View view, int depth, int maxDepth) {
        if (depth > maxDepth) return false;
        if (view.getId() != View.NO_ID) {
            try {
                String resource = view.getResources().getResourceName(view.getId()).toLowerCase(Locale.US);
                if (resource.contains("profile") || resource.contains("user_profile")) return true;
            } catch (Throwable ignored) { }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hierarchyContainsProfile(group.getChildAt(i), depth + 1, maxDepth)) return true;
            }
        }
        return false;
    }

    private void ensureProfileButton(final Activity activity) {
        View root = activity.getWindow().getDecorView();
        if (!(root instanceof ViewGroup)) return;
        ViewGroup decor = (ViewGroup) root;
        if (findTaggedView(decor) != null) return;

        TextView button = new TextView(activity);
        button.setTag(BUTTON_TAG);
        button.setText("Редактировать\nлайки фейк");
        button.setTextColor(Color.WHITE);
        button.setTextSize(12f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
        button.setElevation(dp(activity, 6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(35, 35, 40));
        bg.setCornerRadius(dp(activity, 14));
        button.setBackground(bg);
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showLikesDialog(activity); }
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        lp.setMargins(0, dp(activity, 52), dp(activity, 10), 0);
        try {
            decor.addView(button, lp);
            margyt().log("Zftoz_PROFILE_BUTTON added=true");
        } catch (Throwable t) {
            margyt().log("Zftoz_PROFILE_BUTTON added=false error=" + t.getClass().getSimpleName());
        }
    }

    private View findTaggedView(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (BUTTON_TAG.equals(child.getTag())) return child;
            if (child instanceof ViewGroup) {
                View found = findTaggedView((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void removeProfileButton(Activity activity) {
        View root = activity.getWindow().getDecorView();
        if (root instanceof ViewGroup) removeTagged((ViewGroup) root);
    }

    private void removeProfileButtons() {
        if (currentActivity != null) removeProfileButton(currentActivity);
    }

    private boolean removeTagged(ViewGroup group) {
        boolean removed = false;
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            View child = group.getChildAt(i);
            if (BUTTON_TAG.equals(child.getTag())) {
                group.removeViewAt(i);
                removed = true;
            } else if (child instanceof ViewGroup) {
                removed |= removeTagged((ViewGroup) child);
            }
        }
        return removed;
    }

    private void showLikesDialog(final Activity activity) {
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setHint("Например: 125000");
        input.setText(String.valueOf(getFakeLikes()));
        input.setSelectAllOnFocus(true);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Zftoz — фейковые лайки")
                .setMessage("Значение меняется только визуально в TikTok.")
                .setView(input)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Применить", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                long parsed = Long.parseLong(input.getText().toString().trim());
                if (parsed < 0 || parsed > Integer.MAX_VALUE) throw new NumberFormatException();
                margyt().prefs().edit().putInt(PREF_LIKES, (int) parsed).apply();
                margyt().log("Zftoz_LIKE_SETTING value=" + parsed);
                Toast.makeText(activity, "Фейковые лайки: " + parsed, Toast.LENGTH_SHORT).show();
                scanActivity(activity);
                dialog.dismiss();
            } catch (Throwable ignored) {
                input.setError("Введите число от 0 до 2147483647");
            }
        }));
        dialog.show();
    }

    private void pulseLikeIcon(TextView counter) {
        ViewGroup parent = parentOf(counter);
        if (parent == null) return;
        ImageView candidate = null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ImageView && child.getVisibility() == View.VISIBLE) {
                candidate = (ImageView) child;
                break;
            }
        }
        if (candidate == null) return;
        final ImageView icon = candidate;
        icon.animate().cancel();
        icon.animate().scaleX(1.12f).scaleY(1.12f).setDuration(90L).withEndAction(new Runnable() {
            @Override public void run() {
                icon.animate().scaleX(1f).scaleY(1f).setDuration(170L).start();
            }
        }).start();
        margyt().log("Zftoz_LIKE_PULSE");
    }

    private ViewGroup parentOf(View view) {
        Object parent = view.getParent();
        return parent instanceof ViewGroup ? (ViewGroup) parent : null;
    }

    private int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
