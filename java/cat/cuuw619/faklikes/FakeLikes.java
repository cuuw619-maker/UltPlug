package cat.cuuw619.faklikes;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
    private Activity activity; private boolean running;
    private final Map<TextView,Boolean> watched=new WeakHashMap<TextView,Boolean>();
    private final Map<TextView,Boolean> internalText=new WeakHashMap<TextView,Boolean>();
    private final Map<TextView,ValueAnimator> textAnim=new WeakHashMap<TextView,ValueAnimator>();
    private final Map<TextView,String> visualState=new WeakHashMap<TextView,String>();
    private final Map<View,Long> iconAnim=new WeakHashMap<View,Long>();

    @Override public void onStart(Context c){
        if(!margyt().prefs().contains(LIKES))margyt().prefs().edit().putInt(LIKES,DEFAULT).apply();
        if(!margyt().prefs().contains(ANIM))margyt().prefs().edit().putString(ANIM,"smooth").apply();
        if(!margyt().prefs().contains(SPEED))margyt().prefs().edit().putInt(SPEED,100).apply();
        running=true;margyt().log("MULTITOOL_READY author=Zftoz version=0.0.8 target="+likes()+" animation="+animation()+" speed="+speed()+"%");
    }
    @Override public void onActivityCreated(Activity a){attach(a,"created");}
    @Override public void onActivityResumed(Activity a){attach(a,"resumed");}
    @Override public void onActivityPaused(Activity a){if(activity==a){removeUi(a);activity=null;}}
    @Override public void onStop(){running=false;if(activity!=null)removeUi(activity);margyt().log("MULTITOOL_STOP");}

    private int likes(){return Math.max(0,margyt().prefs().getInt(LIKES,DEFAULT));}
    private String animation(){return margyt().prefs().getString(ANIM,"smooth");}
    private int speed(){return Math.max(50,Math.min(200,margyt().prefs().getInt(SPEED,100)));}
    private long duration(long base){return Math.max(90,base*100L/speed());}

    private void attach(final Activity a,String why){activity=a;margyt().log("MULTITOOL_ACTIVITY "+why+"="+a.getClass().getName());a.getWindow().getDecorView().postDelayed(new Runnable(){public void run(){if(running&&activity==a)scan(a);}},250);a.getWindow().getDecorView().postDelayed(new Runnable(){public void run(){if(running&&activity==a)repeat(a);}},800);}
    private void repeat(final Activity a){if(!running||activity!=a||a.isFinishing())return;scan(a);a.getWindow().getDecorView().postDelayed(new Runnable(){public void run(){repeat(a);}},900);}

    private void scan(Activity a){
        View root=a.getWindow().getDecorView();boolean profile=isProfile(a,root);int found=profile?findLikes(root):0;int icons=profile?findLikeIcons(root):0;boolean comment=isCommentScreen(a,root);View sticker=findCommentStickerControl(root,comment);
        margyt().log("MULTITOOL_SCAN profile="+profile+" profile_likes="+found+" like_icons="+icons+" comment="+comment+" sticker_control="+(sticker!=null));
        if(profile)ensureProfileButton(a,root);else removeTagged(root,PROFILE_TAG);
        if(comment)ensureStickerButton(a,root,sticker);else removeTagged(root,STICKER_TAG);
    }

    private int findLikes(View root){
        ArrayList<TextView> all=new ArrayList<TextView>();collectText(root,all);int n=0;
        for(TextView t:all){if(isLikeResource(t)){n++;watch(t);animateToTarget(t,false);}}
        for(TextView label:all){if(!isLikesLabel(label))continue;TextView target=nearestNumber(all,label);if(target!=null&&!isLikeResource(target)){watch(target);animateToTarget(target,false);n++;}}
        return n;
    }
    private TextView nearestNumber(ArrayList<TextView> all,TextView label){Rect lr=new Rect();label.getGlobalVisibleRect(lr);float lx=lr.centerX(),ly=lr.centerY();TextView best=null;double bestD=Double.MAX_VALUE;for(TextView t:all){if(t==label||!isNumberText(t)||isLikesLabel(t))continue;Rect tr=new Rect();t.getGlobalVisibleRect(tr);double dx=tr.centerX()-lx,dy=tr.centerY()-ly,d=Math.sqrt(dx*dx+dy*dy);if(d<bestD){bestD=d;best=t;}}return bestD<=320?best:null;}
    private boolean isNumberText(TextView t){if(!t.isShown()||t.getWidth()<=0||t.getHeight()<=0)return false;String s=String.valueOf(t.getText()).trim().replace(",","").replace(" ","");return s.matches("\\d+(?:\\.\\d+)?[KkMmBb]?+")||s.matches("\\d+");}
    private boolean isLikesLabel(TextView t){String s=String.valueOf(t.getText()).trim().toLowerCase(Locale.US);return s.equals("likes")||s.equals("like")||s.equals("лайки")||s.equals("лайків")||s.equals("вподобання")||s.equals("нравится")||s.equals("me gusta")||s.contains("likes");}
    private boolean isLikeResource(TextView t){if(t.getId()==View.NO_ID)return false;try{String r=t.getResources().getResourceName(t.getId()).toLowerCase(Locale.US);return r.endsWith("/tv_like_count")||r.endsWith("/like_count")||r.contains("/tv_like_count_")||r.contains("profile_like_count");}catch(Throwable e){return false;}}

    private void watch(final TextView t){
        if(watched.containsKey(t))return;watched.put(t,Boolean.TRUE);
        t.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){if(Boolean.TRUE.equals(internalText.get(t))||!t.isShown()||!running)return;if(formatCount(likes()).equals(String.valueOf(s)))return;t.post(new Runnable(){public void run(){if(running)animateToTarget(t,true);}});}public void afterTextChanged(android.text.Editable e){}});
    }
    private void setInternal(TextView t,String text){internalText.put(t,Boolean.TRUE);try{t.setText(text);}finally{internalText.remove(t);}}
    private void animateToTarget(final TextView t,boolean force){
        if(t==null||!t.isShown())return;final String target=formatCount(likes());final String mode=animation();String state=mode+":"+speed()+":"+target;String oldState=visualState.get(t);String current=String.valueOf(t.getText());
        if(!force&&state.equals(oldState)&&target.equals(current))return;visualState.put(t,state);
        ValueAnimator old=textAnim.get(t);if(old!=null)old.cancel();long end=likes(),start=parseCount(current);if(start<0)start=end;
        if("stock".equals(mode)){setInternal(t,target);return;}if(start==end)start=Math.max(0,Math.round(end*0.72f));
        ValueAnimator v=ValueAnimator.ofFloat(start,end);v.setDuration(duration("bounce".equals(mode)?760:560));
        if("spring".equals(mode))v.setInterpolator(new OvershootInterpolator(2.3f));else if("bounce".equals(mode))v.setInterpolator(new BounceInterpolator());else if("ease".equals(mode))v.setInterpolator(new AccelerateDecelerateInterpolator());else v.setInterpolator(new DecelerateInterpolator(1.6f));
        v.addUpdateListener(x->{if(!running||!t.isShown())return;setInternal(t,formatCount(Math.round((Float)x.getAnimatedValue())));});v.start();textAnim.put(t,v);margyt().log("MULTITOOL_ANIM mode="+mode+" speed="+speed()+" from="+current+" to="+target);
        if("pulse".equals(mode))pulse(t);else if("spring".equals(mode))springVisual(t);else if("bounce".equals(mode))bounceVisual(t);else if("ease".equals(mode))fadeVisual(t);
    }
    private long parseCount(String s){try{String x=s.trim().replace(",","").replace(" ","").toUpperCase(Locale.US);double m=1;if(x.endsWith("K")){m=1000;x=x.substring(0,x.length()-1);}else if(x.endsWith("M")){m=1000000;x=x.substring(0,x.length()-1);}else if(x.endsWith("B")){m=1000000000L;x=x.substring(0,x.length()-1);}return Math.round(Double.parseDouble(x)*m);}catch(Throwable e){return -1;}}
    private String formatCount(long n){if(n>=1000000000L)return String.format(Locale.US,"%.1fB",n/1000000000.0).replace(".0B","B");if(n>=1000000L)return String.format(Locale.US,"%.1fM",n/1000000.0).replace(".0M","M");if(n>=1000L)return String.format(Locale.US,"%.1fK",n/1000.0).replace(".0K","K");return String.valueOf(n);}
    private void pulse(final View v){v.animate().cancel();v.setScaleX(1f);v.setScaleY(1f);v.animate().scaleX(1.09f).scaleY(1.09f).setDuration(duration(130)).withEndAction(new Runnable(){public void run(){v.animate().scaleX(1f).scaleY(1f).setDuration(duration(250)).start();}}).start();}
    private void springVisual(final View v){v.animate().cancel();v.setScaleX(.94f);v.setScaleY(.94f);v.animate().scaleX(1f).scaleY(1f).setInterpolator(new OvershootInterpolator(2.2f)).setDuration(duration(360)).start();}
    private void bounceVisual(final View v){v.animate().cancel();v.animate().translationY(-5f).setInterpolator(new BounceInterpolator()).setDuration(duration(420)).withEndAction(new Runnable(){public void run(){v.animate().translationY(0f).setDuration(duration(160)).start();}}).start();}
    private void fadeVisual(final View v){v.setAlpha(0.45f);v.animate().alpha(1f).setInterpolator(new AccelerateDecelerateInterpolator()).setDuration(duration(300)).start();}

    private int findLikeIcons(View v){int n=0;if(v instanceof ImageView&&isLikeIcon((ImageView)v)){n++;if(!"stock".equals(animation()))animateIcon(v);}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)n+=findLikeIcons(g.getChildAt(i));}return n;}
    private boolean isLikeIcon(ImageView v){if(v.getId()==View.NO_ID)return false;try{String r=v.getResources().getResourceName(v.getId()).toLowerCase(Locale.US);return(r.contains("like")||r.contains("digg")||r.contains("heart"))&&!r.contains("count")&&!r.contains("comment");}catch(Throwable e){return false;}}
    private void animateIcon(View v){long now=System.currentTimeMillis(),last=iconAnim.containsKey(v)?iconAnim.get(v):0L;if(now-last<1400)return;iconAnim.put(v,now);String mode=animation();v.animate().cancel();v.setTranslationY(0);if("spring".equals(mode))v.animate().scaleX(1.18f).scaleY(1.18f).setInterpolator(new OvershootInterpolator(2.5f)).setDuration(duration(260)).withEndAction(new Runnable(){public void run(){v.animate().scaleX(1f).scaleY(1f).setDuration(duration(300)).start();}}).start();else if("bounce".equals(mode))v.animate().translationY(-5f).setInterpolator(new BounceInterpolator()).setDuration(duration(500)).withEndAction(new Runnable(){public void run(){v.animate().translationY(0f).setDuration(duration(180)).start();}}).start();else if("pulse".equals(mode))pulse(v);else if("ease".equals(mode))fadeVisual(v);else v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(duration(180)).withEndAction(new Runnable(){public void run(){v.animate().scaleX(1f).scaleY(1f).setDuration(duration(260)).start();}}).start();}

    private boolean isProfile(Activity a,View root){String n=a.getClass().getName().toLowerCase(Locale.US);if(n.contains("profile")||n.contains("userprofile"))return true;return profileResourceSignal(root)||profileStatsSignal(root);}
    private boolean profileResourceSignal(View v){if(v.getId()!=View.NO_ID)try{String r=v.getResources().getResourceName(v.getId()).toLowerCase(Locale.US);if(r.contains("edit_profile")||r.contains("profile_header")||r.contains("profile_info")||r.contains("profile_stat")||r.contains("user_profile"))return true;}catch(Throwable e){}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(profileResourceSignal(g.getChildAt(i)))return true;}return false;}
    private boolean profileStatsSignal(View root){ArrayList<TextView> ts=new ArrayList<TextView>();collectText(root,ts);int f=0,fl=0,l=0;for(TextView t:ts){String s=String.valueOf(t.getText()).trim().toLowerCase(Locale.US);if(s.equals("followers")||s.equals("подписчики")||s.equals("підписники"))f++;if(s.equals("following")||s.equals("подписки")||s.equals("підписки"))fl++;if(isLikesLabel(t))l++;}return l>0&&(f>0||fl>0);}
    private void collectText(View v,ArrayList<TextView> out){if(!v.isShown())return;if(v instanceof TextView)out.add((TextView)v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collectText(g.getChildAt(i),out);}}

    private boolean isCommentScreen(Activity a,View root){String n=a.getClass().getName().toLowerCase(Locale.US);if(n.contains("detail")||n.contains("comment"))return true;return findCommentComposer(root)!=null;}
    private EditText findCommentComposer(View root){ArrayList<EditText> edits=new ArrayList<EditText>();collectEdits(root,edits);for(EditText e:edits){String s=(String.valueOf(e.getHint())+" "+String.valueOf(e.getContentDescription())).toLowerCase(Locale.US);if(s.contains("comment")||s.contains("комментар")||s.contains("коментар"))return e;}return null;}
    private void collectEdits(View v,ArrayList<EditText> out){if(!v.isShown())return;if(v instanceof EditText)out.add((EditText)v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collectEdits(g.getChildAt(i),out);}}
    private View findCommentStickerControl(View root,boolean comment){if(!comment)return null;EditText composer=findCommentComposer(root);if(composer!=null){ViewGroup p=composer.getParent() instanceof ViewGroup?(ViewGroup)composer.getParent():null;for(int depth=0;depth<5&&p!=null;depth++,p=p.getParent() instanceof ViewGroup?(ViewGroup)p.getParent():null){View x=findStickerInGroup(p);if(x!=null)return x;}}return findStickerInTree(root);}
    private View findStickerInGroup(ViewGroup g){for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(isStickerView(v))return v;}return null;}
    private View findStickerInTree(View v){if(isStickerView(v))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=findStickerInTree(g.getChildAt(i));if(x!=null)return x;}}return null;}
    private boolean isStickerView(View v){if(v==null||!v.isShown())return false;String r="",c="",cls=v.getClass().getName().toLowerCase(Locale.US);if(v.getId()!=View.NO_ID)try{r=v.getResources().getResourceName(v.getId()).toLowerCase(Locale.US);}catch(Throwable e){}if(v.getContentDescription()!=null)c=v.getContentDescription().toString().toLowerCase(Locale.US);boolean word=r.contains("sticker")||r.contains("emoji")||r.contains("emoticon")||r.contains("gif")||c.contains("sticker")||c.contains("emoji")||c.contains("emoticon")||c.contains("gif")||c.contains("стикер")||c.contains("емодзи");boolean type=cls.contains("sticker")||cls.contains("emoji")||cls.contains("emoticon");return(word||type)&&!(r.contains("count")||r.contains("comment_count"));}

    private void ensureProfileButton(Activity a,View root){if(findTag(root,PROFILE_TAG)!=null)return;View anchor=findProfileAnchor(root);if(anchor==null)return;ViewGroup parent=anchor.getParent() instanceof ViewGroup?(ViewGroup)anchor.getParent():null;if(parent==null)return;TextView b=interfaceButton(a,PROFILE_TAG,"Multitool");b.setOnClickListener(v->dialog(a));int at=parent.indexOfChild(anchor)+1;parent.addView(b,Math.min(at,parent.getChildCount()));margyt().log("MULTITOOL_PROFILE_BUTTON added=interface");}
    private View findProfileAnchor(View root){ArrayList<TextView> ts=new ArrayList<TextView>();collectText(root,ts);for(TextView t:ts)if(isLikesLabel(t))return t;return null;}

    private void ensureStickerButton(Activity a,View root,View nativeControl){
        if(nativeControl!=null){nativeControl.setTag(STICKER_TAG);if(nativeControl.getContentDescription()==null)nativeControl.setContentDescription("Стикеры");margyt().log("MULTITOOL_STICKER_BUTTON added=native class="+nativeControl.getClass().getName()+" clickable="+nativeControl.isClickable());return;}
        if(findTag(root,STICKER_TAG)!=null)return;EditText composer=findCommentComposer(root);ViewGroup parent=composer!=null&&composer.getParent() instanceof ViewGroup?(ViewGroup)composer.getParent():null;if(parent==null)return;
        TextView b=interfaceButton(a,STICKER_TAG,"Стикер");b.setOnClickListener(v->{View target=findCommentStickerControl(root,true);boolean ok=target!=null&&target.performClick();margyt().log("MULTITOOL_STICKER_OPEN found="+(target!=null)+" opened="+ok);});parent.addView(b,parent.getChildCount());margyt().log("MULTITOOL_STICKER_BUTTON added=fallback");
    }
    private TextView interfaceButton(Activity a,String tag,String text){TextView b=new TextView(a);b.setTag(tag);b.setText(text);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setGravity(Gravity.CENTER);b.setPadding(18,10,18,10);b.setMinHeight(42);GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(35,35,40));bg.setCornerRadius(16);b.setBackground(bg);if(!"stock".equals(animation()))b.setAlpha(0f);b.postDelayed(new Runnable(){public void run(){b.animate().alpha(1f).setDuration(duration(260)).start();}},30);return b;}
    private View findTag(View v,String tag){if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=g.getChildAt(i);if(tag.equals(x.getTag()))return x;View y=findTag(x,tag);if(y!=null)return y;}}return null;}
    private void removeTagged(View root,String tag){if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=g.getChildCount()-1;i>=0;i--){View v=g.getChildAt(i);if(tag.equals(v.getTag()))g.removeViewAt(i);else removeTagged(v,tag);}}}
    private void removeUi(Activity a){removeTagged(a.getWindow().getDecorView(),PROFILE_TAG);removeTagged(a.getWindow().getDecorView(),STICKER_TAG);}

    private void dialog(final Activity a){
        LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(8,0,8,0);
        final EditText e=new EditText(a);e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setSingleLine(true);e.setText(String.valueOf(likes()));e.setSelectAllOnFocus(true);box.addView(e,new LinearLayout.LayoutParams(-1,-2));
        TextView title=new TextView(a);title.setText("Анимация");title.setTextSize(15);title.setPadding(0,14,0,6);box.addView(title,new LinearLayout.LayoutParams(-1,-2));
        final RadioGroup modes=new RadioGroup(a);modes.setOrientation(RadioGroup.VERTICAL);String[] names={"Плавная","Пружина","Bounce","Ease","Пульс","Стандартная"};String[] values={"smooth","spring","bounce","ease","pulse","stock"};
        for(int i=0;i<names.length;i++){RadioButton r=new RadioButton(a);r.setText(names[i]);r.setTag(values[i]);r.setId(1000+i);modes.addView(r,new RadioGroup.LayoutParams(-1,-2));if(values[i].equals(animation()))r.setChecked(true);}box.addView(modes,new LinearLayout.LayoutParams(-1,-2));
        final TextView sl=new TextView(a);sl.setText("Скорость: "+speed()+"%");sl.setPadding(0,12,0,4);box.addView(sl,new LinearLayout.LayoutParams(-1,-2));
        final SeekBar sb=new SeekBar(a);sb.setMax(150);sb.setProgress(speed()-50);box.addView(sb,new LinearLayout.LayoutParams(-1,-2));sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean f){sl.setText("Скорость: "+(p+50)+"%");}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        AlertDialog d=new AlertDialog.Builder(a).setTitle("Multitool").setView(box).setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{int value=Integer.parseInt(e.getText().toString().trim());String mode=animation();int id=modes.getCheckedRadioButtonId();if(id>=1000&&id<1000+values.length)mode=values[id-1000];margyt().prefs().edit().putInt(LIKES,Math.max(0,value)).putInt(SPEED,sb.getProgress()+50).putString(ANIM,mode).apply();visualState.clear();if(activity==a)scan(a);Toast.makeText(a,"Сохранено",Toast.LENGTH_SHORT).show();d.dismiss();}catch(Throwable ex){Toast.makeText(a,"Введите число",Toast.LENGTH_SHORT).show();}}));d.show();
    }
}
