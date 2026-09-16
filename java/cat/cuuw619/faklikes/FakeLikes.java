package cat.cuuw619.faklikes;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import cat.narezany.margyt.plugin.MargyPlugin;

public final class FakeLikes extends MargyPlugin {
    private static final String LIKES="fake_likes", ANIM="animation_mode", SPEED="animation_speed";
    private static final int DEFAULT=125000;
    private static final String PROFILE_TAG="multitool_profile_ui", STICKER_TAG="multitool_comment_sticker_ui";
    private Activity activity;
    private boolean running;
    private final Map<TextView,Boolean> watched=new WeakHashMap<TextView,Boolean>();
    private final Map<View,Long> iconAnim=new WeakHashMap<View,Long>();

    @Override public void onStart(Context c){
        if(!margyt().prefs().contains(LIKES))margyt().prefs().edit().putInt(LIKES,DEFAULT).apply();
        if(!margyt().prefs().contains(ANIM))margyt().prefs().edit().putString(ANIM,"custom").apply();
        if(!margyt().prefs().contains(SPEED))margyt().prefs().edit().putInt(SPEED,100).apply();
        running=true; margyt().log("MULTITOOL_READY author=Zftoz version=0.0.6 target="+likes()+" animation="+animation()+" speed="+speed()+"%");
    }
    @Override public void onActivityCreated(Activity a){attach(a,"created");}
    @Override public void onActivityResumed(Activity a){attach(a,"resumed");}
    @Override public void onActivityPaused(Activity a){if(activity==a){removeUi(a);activity=null;}}
    @Override public void onStop(){running=false;if(activity!=null)removeUi(activity);margyt().log("MULTITOOL_STOP");}

    private int likes(){return Math.max(0,margyt().prefs().getInt(LIKES,DEFAULT));}
    private String animation(){return margyt().prefs().getString(ANIM,"custom");}
    private boolean custom(){return "custom".equals(animation());}
    private int speed(){return Math.max(50,Math.min(200,margyt().prefs().getInt(SPEED,100)));}
    private long duration(long b){return Math.max(80,b*100L/speed());}

    private void attach(final Activity a,String why){
        activity=a;margyt().log("MULTITOOL_ACTIVITY "+why+"="+a.getClass().getName());
        a.getWindow().getDecorView().postDelayed(new Runnable(){public void run(){if(running&&activity==a)scan(a);}},250);
        a.getWindow().getDecorView().postDelayed(new Runnable(){public void run(){if(running&&activity==a)repeat(a);}},850);
    }
    private void repeat(final Activity a){
        if(!running||activity!=a||a.isFinishing())return;scan(a);
        a.getWindow().getDecorView().postDelayed(new Runnable(){public void run(){repeat(a);}},850);
    }

    private void scan(Activity a){
        View root=a.getWindow().getDecorView();
        boolean profile=isProfile(a,root);
        int found=profile?findLikes(root):0;
        int icons=profile?findLikeIcons(root):0;
        boolean comment=isCommentScreen(a,root);
        View sticker=findCommentStickerControl(root,comment);
        margyt().log("MULTITOOL_SCAN profile="+profile+" profile_likes="+found+" like_icons="+icons+" comment="+comment+" sticker_control="+(sticker!=null));
        if(profile)ensureProfileButton(a,root);else removeTagged(root,PROFILE_TAG);
        if(sticker!=null)ensureStickerButton(a,sticker);else removeTagged(root,STICKER_TAG);
    }

    private int findLikes(View v){
        int n=0;
        if(v instanceof TextView){TextView t=(TextView)v;if(isLikeResource(t)){n++;watch(t);apply(t);}}
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)n+=findLikes(g.getChildAt(i));}
        return n+findLabelLikes(v);
    }
    private int findLabelLikes(View root){
        if(!(root instanceof ViewGroup))return 0;int n=0;ViewGroup g=(ViewGroup)root;
        for(int i=0;i<g.getChildCount();i++){
            View c=g.getChildAt(i);
            if(c instanceof TextView&&isLikesLabel((TextView)c)){
                TextView target=findNearbyNumber(g,i);
                if(target==null)target=findNearbyNumber(g.getParent() instanceof ViewGroup?(ViewGroup)g.getParent():null,-1);
                if(target!=null&&!isLikeResource(target)){watch(target);apply(target);n++;}
            }
            if(c instanceof ViewGroup)n+=findLabelLikes(c);
        }
        return n;
    }
    private TextView findNearbyNumber(ViewGroup g,int idx){
        if(g==null)return null;TextView best=null;int dist=999;
        for(int i=0;i<g.getChildCount();i++){
            if(i==idx)continue;View v=g.getChildAt(i);
            if(v instanceof TextView&&isNumberText((TextView)v)){int d=idx<0?0:Math.abs(i-idx);if(d<dist){dist=d;best=(TextView)v;}}
        }
        return best;
    }
    private boolean isNumberText(TextView t){
        if(!t.isShown()||t.getWidth()<=0||t.getHeight()<=0)return false;
        String s=String.valueOf(t.getText()).trim().replace(",","").replace(" ","");
        return s.matches("\\d+(?:\\.\\d+)?[KkMmBb]?+")||s.matches("\\d+");
    }
    private boolean isLikesLabel(TextView t){
        String s=String.valueOf(t.getText()).trim().toLowerCase(Locale.US);
        return s.equals("likes")||s.equals("like")||s.equals("лайки")||s.equals("лайків")||s.equals("вподобання")||s.equals("нравится")||s.equals("me gusta")||s.contains("likes");
    }
    private boolean isLikeResource(TextView t){
        if(!t.isShown()||t.getWidth()<=0||t.getHeight()<=0||t.getId()==View.NO_ID)return false;
        try{String r=t.getResources().getResourceName(t.getId()).toLowerCase(Locale.US);return r.endsWith("/tv_like_count")||r.endsWith("/like_count")||r.contains("/tv_like_count_")||r.contains("profile_like_count");}catch(Throwable e){return false;}
    }
    private void watch(final TextView t){
        if(watched.containsKey(t))return;watched.put(t,Boolean.TRUE);
        t.addTextChangedListener(new TextWatcher(){private boolean self;public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){if(self||!t.isShown())return;final String target=fmt(likes());if(target.equals(String.valueOf(s)))return;t.post(new Runnable(){public void run(){if(!running||!t.isShown()||target.equals(String.valueOf(t.getText())))return;self=true;try{t.setText(target);}finally{self=false;}}});}public void afterTextChanged(android.text.Editable e){}});
    }
    private void apply(final TextView t){
        String old=String.valueOf(t.getText()),target=fmt(likes());if(target.equals(old))return;
        if(!custom()){set(t,target);return;}long start=parse(old);if(start<0){set(t,target);return;}
        margyt().log("MULTITOOL_LIKE old="+old+" target="+target+" mode=custom");
        ValueAnimator v=ValueAnimator.ofInt((int)Math.min(start,Integer.MAX_VALUE),likes());v.setDuration(duration(520));v.setInterpolator(new DecelerateInterpolator(1.8f));v.addUpdateListener(x->set(t,fmt((Integer)x.getAnimatedValue())));v.start();
    }

    private int findLikeIcons(View v){int n=0;if(v instanceof ImageView&&isLikeIcon((ImageView)v)){n++;if(custom())animateIcon(v);}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)n+=findLikeIcons(g.getChildAt(i));}return n;}
    private boolean isLikeIcon(ImageView v){if(!v.isShown()||v.getWidth()<=0||v.getHeight()<=0||v.getId()==View.NO_ID)return false;try{String r=v.getResources().getResourceName(v.getId()).toLowerCase(Locale.US);return(r.contains("like")||r.contains("digg")||r.contains("heart"))&&!r.contains("count")&&!r.contains("comment");}catch(Throwable e){return false;}}
    private void animateIcon(View v){long now=System.currentTimeMillis(),last=iconAnim.containsKey(v)?iconAnim.get(v):0L;if(now-last<1300)return;iconAnim.put(v,now);v.animate().cancel();v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(duration(130)).withEndAction(()->v.animate().scaleX(1f).scaleY(1f).setDuration(duration(300)).start()).start();}

    private boolean isProfile(Activity a,View root){String n=a.getClass().getName().toLowerCase(Locale.US);if(n.contains("profile")||n.contains("userprofile"))return true;return profileResourceSignal(root)||profileStatsSignal(root);}
    private boolean profileResourceSignal(View v){if(v.getId()!=View.NO_ID)try{String r=v.getResources().getResourceName(v.getId()).toLowerCase(Locale.US);if(r.contains("edit_profile")||r.contains("profile_header")||r.contains("profile_info")||r.contains("profile_stat")||r.contains("user_profile"))return true;}catch(Throwable e){}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(profileResourceSignal(g.getChildAt(i)))return true;}return false;}
    private boolean profileStatsSignal(View root){ArrayList<TextView> labels=new ArrayList<TextView>();collectText(root,labels);int f=0,fl=0,l=0;for(TextView t:labels){String s=String.valueOf(t.getText()).trim().toLowerCase(Locale.US);if(s.equals("followers")||s.equals("подписчики")||s.equals("підписники"))f++;if(s.equals("following")||s.equals("подписки")||s.equals("підписки"))fl++;if(isLikesLabel(t))l++;}return l>0&&(f>0||fl>0);}
    private void collectText(View v,ArrayList<TextView> out){if(!v.isShown())return;if(v instanceof TextView)out.add((TextView)v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collectText(g.getChildAt(i),out);}}

    private boolean isCommentScreen(Activity a,View root){String n=a.getClass().getName().toLowerCase(Locale.US);if(n.contains("detail")||n.contains("comment"))return true;return hasCommentComposer(root);}
    private boolean hasCommentComposer(View root){ArrayList<TextView> ts=new ArrayList<TextView>();collectText(root,ts);for(TextView t:ts){String s=String.valueOf(t.getHint())+" "+String.valueOf(t.getContentDescription())+" "+String.valueOf(t.getText());s=s.toLowerCase(Locale.US);if(s.contains("add comment")||s.contains("comment")||s.contains("комментар")||s.contains("коментар"))return true;}return false;}
    private View findCommentStickerControl(View v,boolean comment){if(!comment)return null;if(v.isShown()&&v.isClickable()){String r="",c="";if(v.getId()!=View.NO_ID)try{r=v.getResources().getResourceName(v.getId()).toLowerCase(Locale.US);}catch(Throwable e){}if(v.getContentDescription()!=null)c=v.getContentDescription().toString().toLowerCase(Locale.US);if(r.contains("sticker")||r.contains("emoji")||r.contains("gif")||c.contains("sticker")||c.contains("emoji")||c.contains("gif")||c.contains("стикер")||c.contains("емодзи"))return v;}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=findCommentStickerControl(g.getChildAt(i),true);if(x!=null)return x;}}return null;}

    private void ensureProfileButton(Activity a,View root){if(findTag(root,PROFILE_TAG)!=null)return;View anchor=findProfileAnchor(root);if(anchor==null)return;ViewGroup parent=anchor.getParent() instanceof ViewGroup?(ViewGroup)anchor.getParent():null;if(parent==null)return;TextView b=interfaceButton(a,PROFILE_TAG,"Multitool");b.setOnClickListener(v->dialog(a));int at=parent.indexOfChild(anchor)+1;parent.addView(b,Math.min(at,parent.getChildCount()));margyt().log("MULTITOOL_PROFILE_BUTTON added=interface");}
    private View findProfileAnchor(View root){ArrayList<TextView> ts=new ArrayList<TextView>();collectText(root,ts);for(TextView t:ts)if(isLikesLabel(t))return t;return null;}
    private void ensureStickerButton(Activity a,View nativeControl){if(findTag(a.getWindow().getDecorView(),STICKER_TAG)!=null)return;ViewGroup p=nativeControl.getParent() instanceof ViewGroup?(ViewGroup)nativeControl.getParent():null;if(p==null)return;TextView b=interfaceButton(a,STICKER_TAG,"Стикер");b.setOnClickListener(v->{nativeControl.performClick();margyt().log("MULTITOOL_STICKER_OPEN native_control=true");});int at=p.indexOfChild(nativeControl);p.addView(b,Math.min(at,p.getChildCount()));margyt().log("MULTITOOL_STICKER_BUTTON added=interface");}
    private TextView interfaceButton(Activity a,String tag,String text){TextView b=new TextView(a);b.setTag(tag);b.setText(text);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setGravity(Gravity.CENTER);b.setPadding(18,10,18,10);b.setMinHeight(42);GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(35,35,40));bg.setCornerRadius(16);b.setBackground(bg);if(custom()){b.setAlpha(0f);b.animate().alpha(1f).setDuration(duration(240)).start();}return b;}
    private View findTag(View v,String tag){if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=g.getChildAt(i);if(tag.equals(x.getTag()))return x;View y=findTag(x,tag);if(y!=null)return y;}}return null;}
    private void removeTagged(View root,String tag){if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=g.getChildCount()-1;i>=0;i--){View v=g.getChildAt(i);if(tag.equals(v.getTag()))g.removeViewAt(i);else removeTagged(v,tag);}}}
    private void removeUi(Activity a){removeTagged(a.getWindow().getDecorView(),PROFILE_TAG);removeTagged(a.getWindow().getDecorView(),STICKER_TAG);}

    private void dialog(final Activity a){
        LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(8,0,8,0);
        final EditText e=new EditText(a);e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setSingleLine(true);e.setText(String.valueOf(likes()));e.setSelectAllOnFocus(true);box.addView(e,new LinearLayout.LayoutParams(-1,-2));
        final TextView sl=new TextView(a);sl.setText("Скорость анимаций: "+speed()+"%");sl.setPadding(0,12,0,4);box.addView(sl,new LinearLayout.LayoutParams(-1,-2));
        SeekBar sb=new SeekBar(a);sb.setMax(150);sb.setProgress(speed()-50);box.addView(sb,new LinearLayout.LayoutParams(-1,-2));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean f){sl.setText("Скорость анимаций: "+(p+50)+"%");}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){margyt().prefs().edit().putInt(SPEED,b.getProgress()+50).apply();}});
        AlertDialog d=new AlertDialog.Builder(a).setTitle("Multitool").setView(box).setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{int value=Integer.parseInt(e.getText().toString().trim());margyt().prefs().edit().putInt(LIKES,Math.max(0,value)).putInt(SPEED,sb.getProgress()+50).apply();Toast.makeText(a,"Multitool сохранён",Toast.LENGTH_SHORT).show();d.dismiss();}catch(Throwable z){e.setError("Введите число");}}));d.show();
    }
    private long parse(String s){try{return Long.parseLong(s.replace(",","").replace(" ",""));}catch(Throwable e){return -1;}}
    private String fmt(int n){if(n>=1000000000)return String.format(Locale.US,"%.1fB",n/1000000000f);if(n>=1000000)return String.format(Locale.US,"%.1fM",n/1000000f);if(n>=1000)return String.format(Locale.US,"%.1fK",n/1000f);return String.valueOf(n);}
}
