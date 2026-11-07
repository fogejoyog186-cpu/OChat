package com.changyu.ochat;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Collapsed: literal lines in a two-axis viewport. Expanded: static content in the page. */
final class CodeBlockView extends LinearLayout {
 private static final int BACKGROUND=0xff18242e,HEADER=0xff22323f,TEXT=0xffdce6ed,ACCENT=0xff7ddcec;
 private static final int[] TOKEN_COLORS={0xff8fa5b6,0xfff3c585,0xff7ddcec,0xffe1a7db,0xffb4d9a8,0xffa9bcff};
 private final TextView code,title,toggle,hint,preview;
 private final FrameLayout holder;
 private final AxisScroll vertical;
 private final HorizontalScrollView horizontal;private final SeekBar horizontalBar;
 private String raw="",language="";private boolean expanded;private int lineCount=1;
 CodeBlockView(Context context){
  super(context);setOrientation(VERTICAL);setTag("code-block");setClipToOutline(true);
  GradientDrawable outline=new GradientDrawable();outline.setColor(BACKGROUND);outline.setCornerRadius(dp(16));outline.setStroke(dp(1),0xff4b6274);setBackground(outline);
  LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(12),0,dp(8),0);header.setBackgroundColor(HEADER);
  title=label("",12,ACCENT,true);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);header.addView(title,new LayoutParams(0,dp(48),1));
  TextView copy=label("复制",12,TEXT,true);copy.setGravity(Gravity.CENTER);copy.setContentDescription("复制代码");header.addView(copy,new LayoutParams(dp(52),dp(48)));
  copy.setOnClickListener(v->copy());
  preview=label("静态预览",12,ACCENT,true);preview.setGravity(Gravity.CENTER);preview.setContentDescription("静态预览代码，不执行 JavaScript");header.addView(preview,new LayoutParams(dp(72),dp(48)));
  preview.setOnClickListener(v->MarkdownRenderer.showPreview(getContext(),raw,language));addView(header,new LayoutParams(-1,-2));
  code=new TextView(context);code.setTextSize(12);code.setTextColor(TEXT);code.setTypeface(Typeface.MONOSPACE);code.setIncludeFontPadding(false);
  code.setLineSpacing(dp(3),1);code.setPadding(dp(14),dp(14),dp(20),dp(18));code.setGravity(Gravity.TOP|Gravity.LEFT);code.setLayoutDirection(LAYOUT_DIRECTION_LTR);
  code.setTextIsSelectable(false);code.setContentDescription("代码内容");code.setHorizontallyScrolling(true);
  horizontal=new HorizontalScrollView(context);horizontal.setFillViewport(true);horizontal.setHorizontalScrollBarEnabled(false);
  horizontal.setScrollbarFadingEnabled(false);horizontal.setScrollBarStyle(SCROLLBARS_INSIDE_INSET);
  horizontal.addView(code,new HorizontalScrollView.LayoutParams(-2,-2));
  vertical=new AxisScroll(context);vertical.setVerticalScrollBarEnabled(true);vertical.setScrollbarFadingEnabled(false);
  vertical.setScrollBarStyle(SCROLLBARS_INSIDE_INSET);vertical.addView(horizontal,new ScrollView.LayoutParams(-1,-2));
  holder=new FrameLayout(context);holder.addView(vertical,new FrameLayout.LayoutParams(-1,-1));
  horizontalBar=new SeekBar(context);horizontalBar.setTag("code-horizontal-scrollbar");horizontalBar.setContentDescription("水平滚动代码");horizontalBar.setMax(1000);
  horizontalBar.setPadding(dp(12),0,dp(12),0);horizontalBar.setSplitTrack(false);
  horizontalBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff668b9e));horizontalBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff354b5b));
  GradientDrawable thumb=new GradientDrawable();thumb.setColor(ACCENT);thumb.setCornerRadius(dp(3));thumb.setSize(dp(30),dp(5));horizontalBar.setThumb(thumb);
  horizontalBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}public void onProgressChanged(SeekBar bar,int value,boolean user){if(user)horizontal.scrollTo(Math.round(horizontalRange()*value/1000f),0);}});
  horizontal.setOnScrollChangeListener((v,x,y,oldX,oldY)->syncHorizontalBar());
  horizontal.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->syncHorizontalBar());
  code.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->syncHorizontalBar());
  holder.addView(horizontalBar,new FrameLayout.LayoutParams(-1,dp(16),Gravity.BOTTOM));addView(holder,new LayoutParams(-1,-2));
  LinearLayout footer=new LinearLayout(context);footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(dp(12),0,dp(8),0);
  hint=label("",11,0xffa8bdcc,false);hint.setMaxLines(2);footer.addView(hint,new LayoutParams(0,-2,1));
  toggle=label("展开完整代码",12,ACCENT,true);toggle.setGravity(Gravity.CENTER);toggle.setPadding(dp(8),0,dp(8),0);toggle.setContentDescription("展开完整代码");
  toggle.setOnClickListener(v->setExpanded(!expanded));footer.addView(toggle,new LayoutParams(-2,dp(44)));addView(footer,new LayoutParams(-1,-2));
 }
 void setSource(String source,String language){
  boolean languageChanged=!this.language.equals(language);
  if(languageChanged){this.language=language;title.setText(language.toUpperCase(Locale.ROOT));preview.setVisibility(MarkdownRenderer.isPreviewLanguage(language)?VISIBLE:GONE);}
  if(raw.equals(source)){if(languageChanged)updateHeight();return;}
  int start=source.startsWith(raw)?raw.length():0;if(start==0)lineCount=1;
  for(int i=start;i<source.length();i++)if(source.charAt(i)=='\n')lineCount++;
  raw=source;code.setText(highlight(source));updateHeight();
 }
 static CharSequence highlight(String source){SpannableString text=new SpannableString(source);for(CodeSyntax.Token token:CodeSyntax.tokens(source))text.setSpan(new ForegroundColorSpan(TOKEN_COLORS[token.kind]),token.start,token.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return text;}
 String source(){return raw;}
 boolean isExpanded(){return expanded;}
 void setExpanded(boolean value){
  if(expanded==value)return;expanded=value;
  if(code.getParent()!=null)((ViewGroup)code.getParent()).removeView(code);
  holder.removeAllViews();
  if(expanded){
   // No nested scrolling views or touch handler: gestures belong to the conversation.
   code.setHorizontallyScrolling(false);code.setTextIsSelectable(false);
   holder.addView(code,new FrameLayout.LayoutParams(-1,-2));
  }else{
   code.setHorizontallyScrolling(true);horizontal.addView(code,new HorizontalScrollView.LayoutParams(-2,-2));
   holder.addView(vertical,new FrameLayout.LayoutParams(-1,-1));holder.addView(horizontalBar,new FrameLayout.LayoutParams(-1,dp(16),Gravity.BOTTOM));
  }
  updateHeight();
 }
 private int horizontalRange(){return Math.max(0,code.getWidth()-horizontal.getWidth());}
 private void syncHorizontalBar(){int range=horizontalRange();horizontalBar.setVisibility(!expanded&&range>0?VISIBLE:INVISIBLE);horizontalBar.setProgress(range==0?0:Math.round(1000f*horizontal.getScrollX()/range));}
 private void updateHeight(){
  int height=Math.min(dp(220),Math.max(dp(60),lineCount*code.getLineHeight()+dp(32)));
  LayoutParams params=(LayoutParams)holder.getLayoutParams();int desired=expanded?-2:height;if(params.height!=desired){params.height=desired;holder.setLayoutParams(params);}
  UiStyle.textIfChanged(toggle,expanded?"收起代码":"展开完整代码");toggle.setContentDescription(toggle.getText());
  UiStyle.textIfChanged(hint,expanded?"完整代码 · 随页面滚动":lineCount+" 行 · 上下 / 左右拖动");post(this::syncHorizontalBar);
 }
 Bundle state(){Bundle b=new Bundle();b.putBoolean("expanded",expanded);b.putInt("x",horizontal.getScrollX());b.putInt("y",vertical.getScrollY());return b;}
 void restore(Bundle state){if(state==null)return;setExpanded(state.getBoolean("expanded"));if(!expanded)post(()->{horizontal.scrollTo(state.getInt("x"),0);vertical.scrollTo(0,state.getInt("y"));});}
 @Override public boolean dispatchTouchEvent(MotionEvent event){
  boolean inViewport=event.getY()>=holder.getTop()&&event.getY()<holder.getBottom();
  if(getParent()!=null&&!expanded&&inViewport&&event.getActionMasked()==MotionEvent.ACTION_DOWN)getParent().requestDisallowInterceptTouchEvent(true);
  boolean consumed=super.dispatchTouchEvent(event);
  if(getParent()!=null&&!expanded&&(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL))getParent().requestDisallowInterceptTouchEvent(false);
  return consumed;
 }
 private void copy(){android.content.ClipboardManager manager=(android.content.ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE);if(manager!=null){manager.setPrimaryClip(ClipData.newPlainText("代码",raw));Toast.makeText(getContext(),"代码已复制",Toast.LENGTH_SHORT).show();}}
 private TextView label(String value,int size,int color,boolean bold){TextView text=UiStyle.text(getContext(),value,size,color,bold);text.setGravity(Gravity.CENTER_VERTICAL);return text;}
 private int dp(int value){return UiStyle.dp(getContext(),value);}
 private static final class AxisScroll extends ScrollView{
  private float downX,downY;private boolean horizontal;
  AxisScroll(Context context){super(context);}
  @Override public boolean onInterceptTouchEvent(MotionEvent event){
   if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();horizontal=false;return super.onInterceptTouchEvent(event);}
   if(event.getActionMasked()==MotionEvent.ACTION_MOVE){
    float dx=Math.abs(event.getX()-downX),dy=Math.abs(event.getY()-downY);
    if(dx>ViewConfiguration.get(getContext()).getScaledTouchSlop()&&dx>dy)horizontal=true;
    if(horizontal)return false;
   }
   return super.onInterceptTouchEvent(event);
  }
 }
}
