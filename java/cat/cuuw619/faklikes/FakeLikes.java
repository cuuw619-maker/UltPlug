package cat.cuuw619.faklikes;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
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
    private static final long DEFAULT=125000L;
    private static final String PROFILE_TAG="multitool_profile_ui", STICKER_TAG="multitool_comment_sticker_ui";
    private Activity activity; private boolean running;
    private final Map<TextView,Boolean> watched=new WeakHashMap<TextView,Boolean>();
    private final Map<TextView,Boolean> internal=new WeakHashMap<TextView,Boolean>();
    private final Map<TextView,String> lastSource=new WeakHashMap<TextView,String>();
    private final Map<TextView,ValueAnimator> animators=new WeakHashMap<TextView,ValueAnimator>();

    @Override public void onStart(Context c){
        if(!margyt().prefs().contains(LIKES))margyt().prefs().edit().putLong(LIKES,DEFAULT).apply();
        if(!margyt().prefs().contains(ANIM))margyt().prefs().edit().putString(ANIM,"smooth").apply();
        if(!margyt().prefs().contains(SPEED))margyt().prefs().edit().putInt(SPEED,100).apply();
        running=true;margyt().log("MULTITOOL_READY version=0.0.9 target="+likes()+" animation="+animation()+" speed="+speed()+"%");
    }
    @Override public void onActivityCreated(Activity a){attach(a);}
    @Override public void onActivityResumed(Activity a){attach(a);}
    @Override public void onActivityPaused(Activity a){if(activity==a){removeUi(a);activity=null;}}
    @Override public void onStop(){running=false;if(activity!=null)removeUi(activity);}

    private long likes(){return Math.max(0L,margyt().prefs().getLong(LIKES,DEFAULT));}
    private String animation(){return margyt().prefs().getString(ANIM,"smooth");}
    private int speed(){return Math.max(50,Math.min(200,margyt().prefs().getInt(SPEED,100)));}
    private long duration(long base){return Math.max(100,base*100L/speed());}

    private void attach(final Activity a){activity=a;margyt().log("MULTITOOL_ACTIVITY resumed="+a.getClass().getName());a.getWindow().getDecorView().postDelayed(new Runnable(){public void run(){if(running&&activity==a)scan(a);}},350);}
    private void scan(final Activity a){
        View root=a.getWindow().getDecorView();boolean own=isOwnProfile(a,root);int found=own?applyProfileLikes(root):0;boolean comment=isComment(a,root);View nativeSticker=comment?findSticker(root):null;
        margyt().log("MULTITOOL_SCAN own_profile="+own+" profile_likes="+found+" comment="+comment+" sticker_control="+(nativeSticker!=null));
        if(own)ensureProfileButton(a,root);else removeTagged(root,PROFILE_TAG);if(comment)ensureStickerInterface(a,root);else removeTagged(root,STICKER_TAG);
        a.getWindow().getDecorView().postDelayed(new Runnable(){public void run(){if(running&&activity==a)scan(a);}},1200);
    }
    private boolean isOwnProfile(Activity a,View root){String n=a.getClass().getName().toLowerCase(Locale.US);if(n.contains("followrelation")||n.contains("following")||n.contains("follower"))return false;return hasOwnMarker(root);}
    private boolean hasOwnMarker(View root){ArrayList<TextView> ts=new ArrayList<TextView>();collectText(root,ts);for(TextView t:ts){String s=String.valueOf(t.getText()).trim().toLowerCase(Locale.US);if(s.equals("edit profile")||s.equals("редактировать профиль")||s.equals("редагувати профіль"))return true;}return hasResource(root,"edit_profile")||hasResource(root,"profile_edit");}
    private boolean hasResource(View v,String needle){if(v.getId()!=View.NO_ID)try{if(v.getResources().getResourceName(v.getId()).toLowerCase(Locale.US).contains(needle))return true;}catch(Throwable ignored){}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(hasResource(g.getChildAt(i),needle))return true;}return false;}

    private int applyProfileLikes(View root){ArrayList<TextView> ts=new ArrayList<TextView>();collectText(root,ts);int count=0;for(TextView t:ts)if(isExactLikeCount(t)){count++;watch(t);String source=String.valueOf(t.getText());String state=animation()+":"+speed()+":"+likes();String previous=lastSource.get(t);if(previous==null||(!source.equals(previous)&&!source.equals(formatCount(likes())))||!previous.startsWith(state+"|"))animate(t,source,state);}return count;}
    private boolean isExactLikeCount(TextView t){if(t.getId()==View.NO_ID)return false;try{String r=t.getResources().getResourceName(t.getId()).toLowerCase(Locale.US);return r.endsWith("/tv_like_count")||r.endsWith("/profile_like_count")||r.endsWith("/like_count_profile");}catch(Throwable e){return false;}}
    private void watch(final TextView t){if(watched.containsKey(t))return;watched.put(t,Boolean.TRUE);t.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){if(Boolean.TRUE.equals(internal.get(t))||!running||!t.isShown())return;String value=String.valueOf(s);if(value.equals(formatCount(likes())))return;t.post(new Runnable(){public void run(){if(running&&t.isShown())animate(t,String.valueOf(t.getText()),animation()+":"+speed()+":"+likes());}});}public void afterTextChanged(android.text.Editable e){}});}
    private void setInternal(TextView t,String s){internal.put(t,Boolean.TRUE);try{t.setText(s);}finally{internal.remove(t);}}
    private void animate(final TextView t,String source,String state){String old=lastSource.get(t);if(old!=null&&old.equals(state+"|"+source))return;lastSource.put(t,state+"|"+source);ValueAnimator oldAnimator=animators.get(t);if(oldAnimator!=null)oldAnimator.cancel();final long end=likes();long start=parse(source);if(start<0)start=end;if(start==end)start=Math.max(0L,(long)(end*.72));String mode=animation();if("stock".equals(mode)){setInternal(t,formatCount(end));return;}ValueAnimator v=ValueAnimator.ofFloat(start,end);v.setDuration(duration("bounce".equals(mode)?760:620));if("spring".equals(mode))v.setInterpolator(new OvershootInterpolator(2.2f));else if("bounce".equals(mode))v.setInterpolator(new BounceInterpolator());else if("ease".equals(mode))v.setInterpolator(new AccelerateDecelerateInterpolator());else v.setInterpolator(new DecelerateInterpolator(1.5f));v.addUpdateListener(x->{if(running&&t.isShown())setInternal(t,formatCount(Math.round((Float)x.getAnimatedValue())));});v.start();animators.put(t,v);if("pulse".equals(mode))pulse(t);else if("spring".equals(mode))spring(t);else if("bounce".equals(mode))bounce(t);else if("ease".equals(mode))fade(t);margyt().log("MULTITOOL_ANIM mode="+mode+" speed="+speed()+" from="+source+" to="+formatCount(end));}
    private long parse(String s){try{String x=s.trim().replace(",","").replace(" ","").toUpperCase(Locale.US);double m=1;if(x.endsWith("K")){m=1000;x=x.substring(0,x.length()-1);}else if(x.endsWith("M")){m=1000000;x=x.substring(0,x.length()-1);}else if(x.endsWith("B")){m=1000000000L;x=x.substring(0,x.length()-1);}return Math.round(Double.parseDouble(x)*m);}catch(Throwable e){return -1;}}
    private String formatCount(long n){if(n>=1000000000L)return String.format(Locale.US,"%.1fB",n/1000000000d).replace(".0B","B");if(n>=1000000L)return String.format(Locale.US,"%.1fM",n/1000000d).replace(".0M","M");if(n>=1000L)return String.format(Locale.US,"%.1fK",n/1000d).replace(".0K","K");return String.valueOf(n);}
    private void pulse(View v){v.animate().cancel();v.setScaleX(1);v.setScaleY(1);v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(duration(150)).withEndAction(()->v.animate().scaleX(1).scaleY(1).setDuration(duration(250)).start()).start();}
    private void spring(View v){v.animate().cancel();v.setScaleX(.9f);v.setScaleY(.9f);v.animate().scaleX(1).scaleY(1).setInterpolator(new OvershootInterpolator(2.4f)).setDuration(duration(420)).start();}
    private void bounce(View v){v.animate().cancel();v.setTranslationY(0);v.animate().translationY(-7).setInterpolator(new BounceInterpolator()).setDuration(duration(520)).withEndAction(()->v.animate().translationY(0).setDuration(duration(160)).start()).start();}
    private void fade(View v){v.setAlpha(.25f);v.animate().alpha(1).setInterpolator(new AccelerateDecelerateInterpolator()).setDuration(duration(360)).start();}

    private boolean isComment(Activity a,View root){String n=a.getClass().getName().toLowerCase(Locale.US);return n.contains("detail")||n.contains("comment")||findComposer(root)!=null;}
    private EditText findComposer(View root){ArrayList<EditText> es=new ArrayList<EditText>();collectEdits(root,es);for(EditText e:es){String s=(String.valueOf(e.getHint())+" "+String.valueOf(e.getContentDescription())).toLowerCase(Locale.US);if(s.contains("comment")||s.contains("комментар")||s.contains("коментар"))return e;}return null;}
    private void collectEdits(View v,ArrayList<EditText> out){if(!v.isShown())return;if(v instanceof EditText)out.add((EditText)v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collectEdits(g.getChildAt(i),out);}}
    private View findSticker(View root){EditText c=findComposer(root);if(c!=null){ViewGroup p=c.getParent() instanceof ViewGroup?(ViewGroup)c.getParent():null;for(int d=0;d<7&&p!=null;d++,p=p.getParent() instanceof ViewGroup?(ViewGroup)p.getParent():null){View x=findStickerChild(p);if(x!=null)return x;}}return findStickerTree(root);}
    private View findStickerChild(ViewGroup g){for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(isSticker(v))return v;}return null;}
    private View findStickerTree(View v){if(isSticker(v))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=findStickerTree(g.getChildAt(i));if(x!=null)return x;}}return null;}
    private boolean isSticker(View v){if(v==null||!v.isShown())return false;String r="",c="",k=v.getClass().getName().toLowerCase(Locale.US);if(v.getId()!=View.NO_ID)try{r=v.getResources().getResourceName(v.getId()).toLowerCase(Locale.US);}catch(Throwable ignored){}if(v.getContentDescription()!=null)c=v.getContentDescription().toString().toLowerCase(Locale.US);return r.contains("sticker")||r.contains("emoji")||r.contains("emoticon")||r.contains("gif")||c.contains("sticker")||c.contains("emoji")||c.contains("emoticon")||c.contains("gif")||c.contains("стикер")||k.contains("sticker")||k.contains("emoji");}
    private View findClickableDescendant(View v){if(v.isClickable()||v.isLongClickable())return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=findClickableDescendant(g.getChildAt(i));if(x!=null)return x;}}return null;}
    private boolean activateSticker(View target){if(target==null)return false;View clickable=findClickableDescendant(target);if(clickable!=null){try{if(clickable.performClick())return true;}catch(Throwable ignored){}try{if(clickable.callOnClick())return true;}catch(Throwable ignored){}}View p=target;for(int i=0;i<5&&p!=null;i++,p=p.getParent() instanceof View?p.getParent():null)if(p!=target&&(p.isClickable()||p.isLongClickable()))try{if(p.performClick())return true;}catch(Throwable ignored){}try{long now=SystemClock.uptimeMillis();float x=Math.max(1,target.getWidth()/2f),y=Math.max(1,target.getHeight()/2f);MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0);MotionEvent up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,x,y,0);boolean a=target.dispatchTouchEvent(down);boolean b=target.dispatchTouchEvent(up);down.recycle();up.recycle();return a||b;}catch(Throwable ignored){return false;}}
    private void ensureStickerInterface(Activity a,View root){if(findTag(root,STICKER_TAG)!=null)return;EditText c=findComposer(root);if(c==null)return;ViewGroup p=c.getParent() instanceof ViewGroup?(ViewGroup)c.getParent():null;if(p==null)return;TextView b=button(a,STICKER_TAG,"Стикеры");b.setOnClickListener(v->{View target=findSticker(root);boolean ok=activateSticker(target);margyt().log("MULTITOOL_STICKER_OPEN target="+(target==null?"null":target.getClass().getName())+" clickable="+(target!=null&&target.isClickable())+" opened="+ok);});int at=p.indexOfChild(c)+1;p.addView(b,Math.min(at,p.getChildCount()));margyt().log("MULTITOOL_STICKER_BUTTON added=interface");}
    private TextView button(Activity a,String tag,String text){TextView b=new TextView(a);b.setTag(tag);b.setText(text);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setGravity(Gravity.CENTER);b.setPadding(16,8,16,8);b.setMinHeight(40);GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(35,35,40));bg.setCornerRadius(18);b.setBackground(bg);b.setAlpha(0f);b.postDelayed(()->b.animate().alpha(1).setDuration(duration(260)).start(),40);return b;}
    private void ensureProfileButton(Activity a,View root){if(findTag(root,PROFILE_TAG)!=null)return;ArrayList<TextView> ts=new ArrayList<TextView>();collectText(root,ts);for(TextView t:ts){String s=String.valueOf(t.getText()).toLowerCase(Locale.US);if(s.contains("likes")||s.contains("лайк")||s.contains("вподоб")){ViewGroup p=t.getParent() instanceof ViewGroup?(ViewGroup)t.getParent():null;if(p!=null){TextView b=button(a,PROFILE_TAG,"Multitool");b.setOnClickListener(v->dialog(a));p.addView(b);margyt().log("MULTITOOL_PROFILE_BUTTON added=interface");}return;}}}
    private void dialog(final Activity a){LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(8,0,8,0);EditText e=new EditText(a);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);e.setSingleLine(true);e.setText(String.valueOf(likes()));box.addView(e);TextView title=new TextView(a);title.setText("Анимация");title.setPadding(0,14,0,6);box.addView(title);RadioGroup rg=new RadioGroup(a);String[] names={"Плавная","Пружина","Bounce","Ease","Пульс","Стандартная"};String[] vals={"smooth","spring","bounce","ease","pulse","stock"};for(int i=0;i<vals.length;i++){RadioButton r=new RadioButton(a);r.setText(names[i]);r.setTag(vals[i]);r.setId(2000+i);r.setChecked(vals[i].equals(animation()));rg.addView(r);}box.addView(rg);TextView sl=new TextView(a);sl.setText("Скорость: "+speed()+"%");box.addView(sl);SeekBar sb=new SeekBar(a);sb.setMax(150);sb.setProgress(speed()-50);box.addView(sb);sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean f){sl.setText("Скорость: "+(p+50)+"%");}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});AlertDialog d=new AlertDialog.Builder(a).setTitle("Multitool").setView(box).setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).create();d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{long value=parse(e.getText().toString());if(value<0)throw new Exception();String mode=animation();int id=rg.getCheckedRadioButtonId();if(id>=2000&&id<2000+vals.length)mode=vals[id-2000];margyt().prefs().edit().putLong(LIKES,value).putInt(SPEED,sb.getProgress()+50).putString(ANIM,mode).apply();lastSource.clear();scan(a);Toast.makeText(a,"Сохранено",Toast.LENGTH_SHORT).show();d.dismiss();}catch(Throwable ex){Toast.makeText(a,"Введите число",Toast.LENGTH_SHORT).show();}}));d.show();}
    private void collectText(View v,ArrayList<TextView> out){if(!v.isShown())return;if(v instanceof TextView)out.add((TextView)v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collectText(g.getChildAt(i),out);}}
    private View findTag(View v,String tag){if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=g.getChildAt(i);if(tag.equals(x.getTag()))return x;View y=findTag(x,tag);if(y!=null)return y;}}return null;}
    private void removeTagged(View v,String tag){if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=g.getChildCount()-1;i>=0;i--){View x=g.getChildAt(i);if(tag.equals(x.getTag()))g.removeViewAt(i);else removeTagged(x,tag);}}}
    private void removeUi(Activity a){removeTagged(a.getWindow().getDecorView(),PROFILE_TAG);removeTagged(a.getWindow().getDecorView(),STICKER_TAG);}
}
