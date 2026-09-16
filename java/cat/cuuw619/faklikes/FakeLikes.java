package cat.cuuw619.faklikes;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;
import cat.narezany.margyt.plugin.MargyPlugin;

public final class FakeLikes extends MargyPlugin {
    private static final String LIKES="fake_likes";
    private static final int DEFAULT=125000;
    private static final String TAG="zftoz_button";
    private Activity activity;
    private boolean running;

    @Override public void onStart(Context c) {
        if (!margyt().prefs().contains(LIKES)) margyt().prefs().edit().putInt(LIKES,DEFAULT).apply();
        running=true;
        margyt().log("Zftoz_READY version=2.2.0 target="+likes());
    }
    @Override public void onActivityCreated(Activity a){ attach(a,"created"); }
    @Override public void onActivityResumed(Activity a){ attach(a,"resumed"); }
    @Override public void onActivityPaused(Activity a){ if(activity==a) activity=null; }
    @Override public void onStop(){ running=false; removeButton(); margyt().log("Zftoz_STOP"); }

    private int likes(){ return Math.max(0,margyt().prefs().getInt(LIKES,DEFAULT)); }
    private void attach(final Activity a,String why){
        activity=a;
        margyt().log("Zftoz_ACTIVITY "+why+"="+a.getClass().getName());
        a.getWindow().getDecorView().postDelayed(new Runnable(){public void run(){ if(running && activity==a) scan(a); }},250);
        a.getWindow().getDecorView().postDelayed(new Runnable(){public void run(){ if(running && activity==a) repeat(a); }},900);
    }
    private void repeat(final Activity a){
        if(!running || activity!=a || a.isFinishing()) return;
        scan(a);
        a.getWindow().getDecorView().postDelayed(new Runnable(){public void run(){repeat(a);}},900);
    }
    private void scan(Activity a){
        View root=a.getWindow().getDecorView();
        int found=findLikes(root);
        boolean profile=isProfile(a,root);
        margyt().log("Zftoz_SCAN likes_views="+found+" profile="+profile);
        if(profile) button(a); else removeButton();
    }
    private int findLikes(View v){
        int n=0;
        if(v instanceof TextView){
            TextView t=(TextView)v;
            if(isLike(t)){ n++; apply(t); }
        }
        if(v instanceof ViewGroup){
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++) n+=findLikes(g.getChildAt(i));
        }
        return n;
    }
    private boolean isLike(TextView t){
        int id=t.getId(); if(id==View.NO_ID)return false;
        try{return t.getResources().getResourceName(id).endsWith("/tv_like_count");}catch(Throwable e){return false;}
    }
    private void apply(final TextView t){
        if(!t.isShown())return;
        final String target=fmt(likes());
        String old=t.getText()==null?"":t.getText().toString();
        if(target.equals(old))return;
        margyt().log("Zftoz_LIKE_FOUND old="+old+" target="+target);
        t.animate().cancel();
        long start=parse(old);
        if(start<0){ set(t,target); return; }
        ValueAnimator v=ValueAnimator.ofInt((int)Math.min(start,Integer.MAX_VALUE),likes());
        v.setDuration(480); v.addUpdateListener(x->set(t,fmt((Integer)x.getAnimatedValue())));
        v.start();
        t.animate().scaleX(1.06f).scaleY(1.06f).setDuration(100).withEndAction(()->t.animate().scaleX(1f).scaleY(1f).setDuration(180).start()).start();
        margyt().log("Zftoz_ANIM start="+start+" end="+likes());
    }
    private void set(TextView t,String s){t.setTag(Boolean.TRUE);try{t.setText(s);}finally{t.setTag(Boolean.FALSE);}}
    private long parse(String s){try{ s=s.trim().replace(",","").replace(" ",""); if(s.matches("\\d+"))return Long.parseLong(s); char c=Character.toLowerCase(s.charAt(s.length()-1)); if(c=='k'||c=='m'||c=='b'){double n=Double.parseDouble(s.substring(0,s.length()-1));return (long)(n*(c=='k'?1000:c=='m'?1000000:1000000000));}}catch(Throwable e){}return -1;}
    private String fmt(int n){if(n<1000)return String.valueOf(n);if(n<1000000)return compact(n,1000,"K");if(n<1000000000)return compact(n,1000000,"M");return compact(n,1000000000,"B");}
    private String compact(int n,int d,String s){double x=n/(double)d;return String.format(Locale.US,x>=100?"%.0f%s":"%.1f%s",x,s).replace(".0K","K").replace(".0M","M").replace(".0B","B");}

    private boolean isProfile(Activity a,View root){
        String name=a.getClass().getName().toLowerCase(Locale.US);
        if(name.contains("profile"))return true;
        return profileText(root,0,20);
    }
    private boolean profileText(View v,int depth,int max){
        if(depth>max)return false;
        if(v instanceof TextView){String s=String.valueOf(((TextView)v).getText()).toLowerCase(Locale.US);if(s.contains("профиль")||s.equals("profile")||s.contains("edit profile"))return true;}
        if(v.getId()!=View.NO_ID)try{String r=v.getResources().getResourceName(v.getId()).toLowerCase(Locale.US);if(r.contains("profile")||r.contains("user_profile"))return true;}catch(Throwable e){}
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(profileText(g.getChildAt(i),depth+1,max))return true;}
        return false;
    }
    private void button(final Activity a){
        View root=a.getWindow().getDecorView(); if(!(root instanceof ViewGroup))return; final ViewGroup g=(ViewGroup)root;
        if(findTag(g)!=null)return;
        TextView b=new TextView(a);b.setTag(TAG);b.setText("Редактировать\nлайки фейк");b.setTextColor(Color.WHITE);b.setTextSize(12);b.setGravity(Gravity.CENTER);b.setPadding(14,10,14,10);b.setElevation(8);
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(30,30,34));bg.setCornerRadius(18);b.setBackground(bg);b.setOnClickListener(v->dialog(a));
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);p.setMargins(0,56,10,0);g.addView(b,p);margyt().log("Zftoz_PROFILE_BUTTON added=true");
    }
    private View findTag(ViewGroup g){for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(TAG.equals(v.getTag()))return v;if(v instanceof ViewGroup){View x=findTag((ViewGroup)v);if(x!=null)return x;}}return null;}
    private void removeButton(){if(activity==null)return;View r=activity.getWindow().getDecorView();if(r instanceof ViewGroup)remove((ViewGroup)r);}
    private void remove(ViewGroup g){for(int i=g.getChildCount()-1;i>=0;i--){View v=g.getChildAt(i);if(TAG.equals(v.getTag()))g.removeViewAt(i);else if(v instanceof ViewGroup)remove((ViewGroup)v);}}
    private void dialog(final Activity a){
        final EditText e=new EditText(a);e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setSingleLine(true);e.setText(String.valueOf(likes()));e.setSelectAllOnFocus(true);
        final AlertDialog d=new AlertDialog.Builder(a).setTitle("Zftoz — фейковые лайки").setMessage("Меняется только отображение.").setView(e).setNegativeButton("Отмена",null).setPositiveButton("Применить",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{long n=Long.parseLong(e.getText().toString().trim());if(n<0||n>2147483647)throw new Exception();margyt().prefs().edit().putInt(LIKES,(int)n).apply();margyt().log("Zftoz_LIKE_SETTING value="+n);Toast.makeText(a,"Фейковые лайки: "+n,Toast.LENGTH_SHORT).show();scan(a);d.dismiss();}catch(Throwable z){e.setError("0..2147483647");}}));d.show();
    }
}
