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
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import cat.narezany.margyt.plugin.MargyPlugin;

/** Multitool for MargyT. Profile likes are deliberately separated from feed/comment likes. */
public final class FakeLikes extends MargyPlugin {
    private static final String LIKES="fake_likes";
    private static final String ANIM="animation_mode";
    private static final String SPEED="animation_speed";
    private static final int DEFAULT=125000;
    private static final String TAG="multitool_button";
    private static final String STICKER_TAG="multitool_sticker_button";
    private Activity activity;
    private boolean running;
    private final Map<TextView,Boolean> watched=new WeakHashMap<TextView,Boolean>();
    private final Map<View,Long> iconAnim=new WeakHashMap<View,Long>();

    @Override public void onStart(Context c){
        if(!margyt().prefs().contains(LIKES))margyt().prefs().edit().putInt(LIKES,DEFAULT).apply();
        if(!margyt().prefs().contains(ANIM))margyt().prefs().edit().putString(ANIM,"custom").apply();
        if(!margyt().prefs().contains(SPEED))margyt().prefs().edit().putInt(SPEED,100).apply();
        running=true;
        margyt().log("MULTITOOL_READY author=Zftoz version=0.0.5 target="+likes()+" animation="+animation()+" speed="+speed()+"%");
    }
    @Override public void onActivityCreated(Activity a){attach(a,"created");}
    @Override public void onActivityResumed(Activity a){attach(a,"resumed");}
    @Override public void onActivityPaused(Activity a){if(activity==a)activity=null;}
    @Override public void onStop(){running=false;removeOverlays();margyt().log("MULTITOOL_STOP");}

    private int likes(){return Math.max(0,margyt().prefs().getInt(LIKES,DEFAULT));}
    private String animation(){return margyt().prefs().getString(ANIM,"custom");}
    private boolean custom(){return "custom".equals(animation());}
    private int speed(){return Math.max(50,Math.min(200,margyt().prefs().getInt(SPEED,100)));}
    private long duration(long base){return Math.max(80,base*100L/speed());}

    private void attach(final Activity a,String why){
        activity=a;
        margyt().log("MULTITOOL_ACTIVITY "+why+"="+a.getClass().getName());
        a.getWindow().getDecorView().postDelayed(new Runnable(){public void run(){if(running&&activity==a)scan(a);}},220);
        a.getWindow().getDecorView().postDelayed(new Runnable(){public void run(){if(running&&activity==a)repeat(a);}},700);
    }
    private void repeat(final Activity a){
        if(!running||activity!=a||a.isFinishing())return;
        scan(a);
        a.getWindow().getDecorView().postDelayed(new Runnable(){public void run(){repeat(a);}},700);
    }

    private void scan(Activity a){
        View root=a.getWindow().getDecorView();
        boolean profile=isProfile(a,root);
        int found=profile?findLikes(root):0;
        int icons=profile?findLikeIcons(root):0;
        int stickers=findStickerControls(root);
        margyt().log("MULTITOOL_SCAN profile="+profile+" profile_likes="+found+" like_icons="+icons+" sticker_controls="+stickers);
        if(profile)showButton(a);else removeTagged(root,TAG);
        if(stickers>0)showStickerButton(a);else removeTagged(root,STICKER_TAG);
    }

    private int findLikes(View v){
        int n=0;
        if(v instanceof TextView){
            TextView t=(TextView)v;
            if(isLikeResource(t)){n++;watch(t);apply(t);}
        }
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)n+=findLikes(g.getChildAt(i));}
        return n+findLabelLikes(v);
    }

    /* TikTok has changed the profile statistics ids more than once. The label is much more stable. */
    private int findLabelLikes(View root){
        int n=0;
        if(root instanceof ViewGroup){
            ViewGroup g=(ViewGroup)root;
            for(int i=0;i<g.getChildCount();i++){
                View c=g.getChildAt(i);
                if(c instanceof TextView && isLikesLabel((TextView)c)){
                    TextView target=nearestNumber(g,i);
                    if(target!=null&&!isLikeResource(target)){watch(target);apply(target);n++;}
                }
                if(c instanceof ViewGroup)n+=findLabelLikes(c);
            }
        }
        return n;
    }
    private TextView nearestNumber(ViewGroup parent,int labelIndex){
        TextView best=null;int bestDistance=99;
        for(int i=0;i<parent.getChildCount();i++){
            if(i==labelIndex)continue;
            View v=parent.getChildAt(i);
            if(v instanceof TextView&&isNumberText((TextView)v)){
                int d=Math.abs(i-labelIndex);
                if(d<bestDistance){best=(TextView)v;bestDistance=d;}
            }
        }
        return best;
    }
    private boolean isNumberText(TextView t){
        if(!t.isShown()||t.getWidth()<=0||t.getHeight()<=0)return false;
        String s=String.valueOf(t.getText()).trim().replace(",","").replace(" ","");
        return s.matches("\\d+(?:\\.\\d+)?[KkMmBb]?")||s.matches("\\d+");
    }
    private boolean isLikesLabel(TextView t){
        String s=String.valueOf(t.getText()).trim().toLowerCase(Locale.US);
        return s.equals("likes")||s.equals("like")||s.equals("лайки")||s.equals("лайків")||s.equals("вподобання")||s.equals("нравится")||s.equals("me gusta");
    }
    private boolean isLikeResource(TextView t){
        if(!t.isShown()||t.getWidth()<=0||t.getHeight()<=0||t.getId()==View.NO_ID)return false;
        try{String r=t.getResources().getResourceName(t.getId()).toLowerCase(Locale.US);return r.endsWith("/tv_like_count")||r.endsWith("/like_count")||r.contains("/tv_like_count_");}catch(Throwable e){return false;}
    }
    private void watch(final TextView t){
        if(watched.containsKey(t))return;
        watched.put(t,Boolean.TRUE);
        t.addTextChangedListener(new android.text.TextWatcher(){
            private boolean self;
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){
                if(self||!t.isShown())return;
                final String target=fmt(likes());
                if(target.equals(String.valueOf(s)))return;
                t.post(new Runnable(){public void run(){if(!running||!t.isShown())return;if(target.equals(String.valueOf(t.getText())))return;self=true;try{t.setText(target);}finally{self=false;}}});
            }
            public void afterTextChanged(android.text.Editable e){}
        });
    }
    private void apply(final TextView t){
        String old=String.valueOf(t.getText());
        final String target=fmt(likes());
        if(target.equals(old))return;
        if(!custom()){set(t,target);return;}
        long start=parse(old);
        margyt().log("MULTITOOL_LIKE old="+old+" target="+target+" mode=custom");
        if(start<0){set(t,target);return;}
        ValueAnimator v=ValueAnimator.ofInt((int)Math.min(start,Integer.MAX_VALUE),likes());
        v.setDuration(duration(520));v.setInterpolator(new DecelerateInterpolator(1.8f));
        v.addUpdateListener(x->set(t,fmt((Integer)x.getAnimatedValue())));v.start();
    }

    private int findLikeIcons(View v){
        int n=0;
        if(v instanceof ImageView&&isLikeIcon((ImageView)v)){n++;if(custom())animateIcon(v);}
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)n+=findLikeIcons(g.getChildAt(i));}
        return n;
    }
    private boolean isLikeIcon(ImageView v){
        if(!v.isShown()||v.getWidth()<=0||v.getHeight()<=0||v.getId()==View.NO_ID)return false;
        try{String r=v.getResources().getResourceName(v.getId()).toLowerCase(Locale.US);return (r.contains("like")||r.contains("digg")||r.contains("heart"))&&!r.contains("count")&&!r.contains("comment");}catch(Throwable e){return false;}
    }
    private void animateIcon(View v){
        long now=System.currentTimeMillis(),last=iconAnim.containsKey(v)?iconAnim.get(v):0L;if(now-last<1300)return;iconAnim.put(v,now);
        v.animate().cancel();v.setScaleX(1f);v.setScaleY(1f);v.animate().scaleX(1.16f).scaleY(1.16f).setDuration(duration(150)).withEndAction(()->v.animate().scaleX(1f).scaleY(1f).setDuration(duration(280)).start()).start();
    }

    private boolean isProfile(Activity a,View root){
        String name=a.getClass().getName().toLowerCase(Locale.US);
        if(name.contains("profile")||name.contains("userprofile"))return true;
        if(profileResourceSignal(root))return true;
        return profileStatsSignal(root);
    }
    private boolean profileResourceSignal(View v){
        if(v.getId()!=View.NO_ID)try{String r=v.getResources().getResourceName(v.getId()).toLowerCase(Locale.US);if(r.contains("edit_profile")||r.contains("profile_header")||r.contains("profile_info")||r.contains("profile_stat"))return true;}catch(Throwable e){}
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(profileResourceSignal(g.getChildAt(i)))return true;}
        return false;
    }
    private boolean profileStatsSignal(View root){
        int followers=0,following=0,likesLabels=0;
        java.util.ArrayList<TextView> labels=new java.util.ArrayList<TextView>();
        collectText(root,labels);
        for(TextView t:labels){String s=String.valueOf(t.getText()).trim().toLowerCase(Locale.US);if(s.equals("followers")||s.equals("подписчики")||s.equals("підписники"))followers++;if(s.equals("following")||s.equals("подписки")||s.equals("підписки"))following++;if(isLikesLabel(t))likesLabels++;}
        return likesLabels>0&&(followers>0||following>0);
    }
    private void collectText(View v,java.util.ArrayList<TextView> out){
        if(!v.isShown())return;
        if(v instanceof TextView)out.add((TextView)v);
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collectText(g.getChildAt(i),out);}
    }

    private int findStickerControls(View v){
        int n=0;
        if(v.isShown()&&v.isClickable()&&v.getId()!=View.NO_ID){
            try{String r=v.getResources().getResourceName(v.getId()).toLowerCase(Locale.US);if((r.contains("sticker")||r.contains("emoji")||r.contains("gif"))&&(r.contains("comment")||r.contains("input")||r.contains("send")))n++;}catch(Throwable e){}
        }
        CharSequence cd=v.getContentDescription();
        if(v.isShown()&&v.isClickable()&&cd!=null){String s=cd.toString().toLowerCase(Locale.US);if(s.contains("sticker")||s.contains("emoji")||s.contains("gif")||s.contains("стикер")||s.contains("емодзи"))n++;}
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)n+=findStickerControls(g.getChildAt(i));}
        return n;
    }
    private View findStickerControl(View v){
        if(v.isShown()&&v.isClickable()){
            String r="";if(v.getId()!=View.NO_ID)try{r=v.getResources().getResourceName(v.getId()).toLowerCase(Locale.US);}catch(Throwable e){}
            CharSequence cd=v.getContentDescription();String c=cd==null?"":cd.toString().toLowerCase(Locale.US);
            if(r.contains("sticker")||r.contains("emoji")||r.contains("gif")||c.contains("sticker")||c.contains("emoji")||c.contains("gif")||c.contains("стикер")||c.contains("емодзи"))return v;
        }
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=findStickerControl(g.getChildAt(i));if(x!=null)return x;}}
        return null;
    }

    private void showButton(final Activity a){
        View root=a.getWindow().getDecorView();if(!(root instanceof ViewGroup))return;final ViewGroup g=(ViewGroup)root;if(findTag(g,TAG)!=null)return;
        TextView b=button(a,TAG,"Multitool\nЛайки / анимации");b.setOnClickListener(v->dialog(a));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);p.setMargins(0,56,10,0);g.addView(b,p);margyt().log("MULTITOOL_PROFILE_BUTTON added=true");
    }
    private void showStickerButton(final Activity a){
        View root=a.getWindow().getDecorView();if(!(root instanceof ViewGroup))return;final ViewGroup g=(ViewGroup)root;if(findTag(g,STICKER_TAG)!=null)return;
        TextView b=button(a,STICKER_TAG,"＋ Стикер");b.setOnClickListener(v->{View target=findStickerControl(g);if(target!=null){target.performClick();margyt().log("MULTITOOL_STICKER_OPEN native_control=true");}else Toast.makeText(a,"Нативная кнопка стикеров не найдена",Toast.LENGTH_SHORT).show();});FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);p.setMargins(0,0,16,96);g.addView(b,p);margyt().log("MULTITOOL_STICKER_BUTTON added=true");
    }
    private TextView button(Activity a,String tag,String text){TextView b=new TextView(a);b.setTag(tag);b.setText(text);b.setTextColor(Color.WHITE);b.setTextSize(12);b.setGravity(Gravity.CENTER);b.setPadding(14,10,14,10);b.setElevation(8);GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(30,30,34));bg.setCornerRadius(18);b.setBackground(bg);if(custom()){b.setAlpha(0f);b.animate().alpha(1f).setDuration(duration(260)).start();}return b;}
    private View findTag(ViewGroup g,String tag){for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(tag.equals(v.getTag()))return v;if(v instanceof ViewGroup){View x=findTag((ViewGroup)v,tag);if(x!=null)return x;}}return null;}
    private void removeTagged(View root,String tag){if(root instanceof ViewGroup)removeTaggedGroup((ViewGroup)root,tag);}
    private void removeTaggedGroup(ViewGroup g,String tag){for(int i=g.getChildCount()-1;i>=0;i--){View v=g.getChildAt(i);if(tag.equals(v.getTag()))g.removeViewAt(i);else if(v instanceof ViewGroup)removeTaggedGroup((ViewGroup)v,tag);}}
    private void removeOverlays(){if(activity==null)return;View r=activity.getWindow().getDecorView();removeTagged(r,TAG);removeTagged(r,STICKER_TAG);}

    private void dialog(final Activity a){
        final EditText e=new EditText(a);e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setSingleLine(true);e.setText(String.valueOf(likes()));e.setSelectAllOnFocus(true);
        final String[] modes={"custom","stock"};int checked=custom()?0:1;
        final String[] speeds={"50","75","100","125","150","200"};
        int current=0;for(int i=0;i<speeds.length;i++)if(Integer.parseInt(speeds[i])==speed())current=i;
        AlertDialog d=new AlertDialog.Builder(a).setTitle("Multitool 0.0.5")
            .setMessage("Лайки меняются только на распознанном профиле.\nCustom — плавные анимации Multitool, Stock — без кастомной анимации.")
            .setView(e)
            .setSingleChoiceItems(new String[]{"Custom animations","Stock animations"},checked,(di,w)->{margyt().prefs().edit().putString(ANIM,modes[w]).apply();margyt().log("MULTITOOL_ANIMATION mode="+modes[w]);})
            .setNegativeButton("Закрыть",null)
            .setNeutralButton("Сбросить 125K",(di,w)->{margyt().prefs().edit().putInt(LIKES,DEFAULT).apply();e.setText(String.valueOf(DEFAULT));scan(a);})
            .setPositiveButton("Применить",null).create();
        d.setOnShowListener(x->{d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{long n=Long.parseLong(e.getText().toString().trim());if(n<0||n>2147483647)throw new Exception();margyt().prefs().edit().putInt(LIKES,(int)n).apply();margyt().log("MULTITOOL_LIKE_SETTING value="+n);scan(a);d.dismiss();}catch(Throwable z){e.setError("0..2147483647");}});});
        d.show();
        d.getListView().postDelayed(()->{},1);
        margyt().log("MULTITOOL_SETTINGS speed="+speeds[current]+"% current_animation="+animation());
    }
    private void set(TextView t,String s){try{t.setText(s);}catch(Throwable ignored){}}
    private long parse(String s){try{s=s.trim().replace(",","").replace(" ","");if(s.matches("\\d+"))return Long.parseLong(s);char c=Character.toLowerCase(s.charAt(s.length()-1));if(c=='k'||c=='m'||c=='b'){double n=Double.parseDouble(s.substring(0,s.length()-1));return(long)(n*(c=='k'?1000:c=='m'?1000000:1000000000));}}catch(Throwable e){}return -1;}
    private String fmt(int n){if(n<1000)return String.valueOf(n);if(n<1000000)return compact(n,1000,"K");if(n<1000000000)return compact(n,1000000,"M");return compact(n,1000000000,"B");}
    private String compact(int n,int d,String s){double x=n/(double)d;return String.format(Locale.US,x>=100?"%.0f%s":"%.1f%s",x,s).replace(".0K","K").replace(".0M","M").replace(".0B","B");}
}
