package com.changyu.ochat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.HashMap;
import java.util.Map;

/** Rounded, themed panel with an app toolbar and actions instead of platform dialog chrome. */
final class PanelDialog extends AlertDialog {
 private final LinearLayout root,content,actions;
 private final Map<Integer,Button> buttons=new HashMap<>();
 private final Activity owner;
 private boolean compact;
 private final ViewTreeObserver.OnGlobalLayoutListener resize=this::resize;
 PanelDialog(Context context,String title,String subtitle,View body){
  super(EditorDialogs.themedContext(context));
  Context themed=getContext();ThemePalette p=ThemePalette.resolve(themed);
  owner=findActivity(context);
  root=new LinearLayout(themed);root.setOrientation(LinearLayout.VERTICAL);
  root.setBackground(UiStyle.surface(themed,p.surface,28,false));root.setClipToOutline(true);
  LinearLayout header=new LinearLayout(themed);header.setGravity(Gravity.CENTER_VERTICAL);
  header.setPadding(dp(22),dp(18),dp(12),dp(14));
  LinearLayout labels=new LinearLayout(themed);labels.setOrientation(LinearLayout.VERTICAL);
  TextView heading=UiStyle.text(themed,title,20,p.ink,true);heading.setMaxLines(2);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(heading);
  if(subtitle!=null&&!subtitle.isEmpty()){TextView detail=UiStyle.text(themed,subtitle,12,p.muted,false);detail.setPadding(0,dp(4),0,0);detail.setMaxLines(2);detail.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(detail);}
  header.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
  Button close=new Button(themed);close.setText("×");close.setTextSize(26);close.setTextColor(p.muted);
  close.setContentDescription("关闭面板");close.setPadding(0,0,0,0);close.setMinWidth(0);close.setMinimumWidth(0);
  close.setBackgroundTintList(null);UiStyle.clickable(close,p.surface,24,false);close.setOnClickListener(v->cancel());
  header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(header);
  content=new LinearLayout(themed);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),0,dp(16),0);
  content.addView(body,new LinearLayout.LayoutParams(-1,-1));root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
  actions=new LinearLayout(themed);actions.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);actions.setPadding(dp(16),dp(12),dp(16),dp(16));
  root.addView(actions,new LinearLayout.LayoutParams(-1,-2));
  // Install the panel directly in onCreate; framework alert content otherwise wraps its height.
 }
 PanelDialog compact(){compact=true;content.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));content.getChildAt(0).setLayoutParams(new LinearLayout.LayoutParams(-1,-2));content.setPadding(dp(16),0,dp(16),dp(16));return this;}
 PanelDialog action(int which,String label,Runnable onClick){
  Context c=getContext();ThemePalette p=ThemePalette.resolve(c);
  Button button=new Button(c);button.setText(label);button.setTextSize(14);button.setAllCaps(false);
  button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(0);button.setMinimumHeight(0);
  button.setPadding(dp(18),0,dp(18),0);button.setBackgroundTintList(null);
  boolean primary=which==BUTTON_POSITIVE;button.setTextColor(primary?p.onPrimary:p.primary);
  UiStyle.clickable(button,primary?p.primary:p.surface,16);
  button.setOnClickListener(v->{if(onClick!=null)onClick.run();dismiss();});
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,dp(48));params.leftMargin=dp(8);
  actions.addView(button,params);buttons.put(which,button);
  if(actions.getChildCount()>=3)for(int i=0;i<actions.getChildCount();i++){
   Button item=(Button)actions.getChildAt(i);item.setMaxLines(2);item.setPadding(dp(8),0,dp(8),0);
   LinearLayout.LayoutParams equal=new LinearLayout.LayoutParams(0,dp(48),1);if(i>0)equal.leftMargin=dp(8);item.setLayoutParams(equal);
  }
  return this;
 }
 @Override public Button getButton(int which){return buttons==null?null:buttons.get(which);}
 @Override protected void onCreate(Bundle state){super.onCreate(state);
  setContentView(root,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
  Window w=getWindow();if(w!=null){
  w.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
  w.getDecorView().setPadding(0,0,0,0);w.setElevation(0);
  w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
  WindowManager.LayoutParams params=w.getAttributes();params.dimAmount=.32f;w.setAttributes(params);
  w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
  androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w,true);
 }}
 @Override protected void onStart(){super.onStart();actions.setVisibility(buttons.isEmpty()?View.GONE:View.VISIBLE);resize();if(owner!=null)owner.getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(resize);}
 @Override protected void onStop(){if(owner!=null){ViewTreeObserver observer=owner.getWindow().getDecorView().getViewTreeObserver();if(observer.isAlive())observer.removeOnGlobalLayoutListener(resize);}super.onStop();}
 private void resize(){
  Window window=getWindow();if(window==null)return;
  Rect available=new Rect();if(owner!=null)owner.getWindow().getDecorView().getWindowVisibleDisplayFrame(available);
  if(available.isEmpty()){available.set(0,0,getContext().getResources().getDisplayMetrics().widthPixels,getContext().getResources().getDisplayMetrics().heightPixels);}
  int width=Math.min(dp(720),Math.max(dp(240),available.width()-dp(24)));
  // Size from the app window before IME occlusion; the keyboard caps available space only once.
  int screenHeight=available.height();
  if(owner!=null&&android.os.Build.VERSION.SDK_INT>=30){
   Rect bounds=owner.getWindowManager().getCurrentWindowMetrics().getBounds();
   androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(owner.getWindow().getDecorView());
   androidx.core.graphics.Insets bars=insets==null?androidx.core.graphics.Insets.NONE:insets.getInsetsIgnoringVisibility(androidx.core.view.WindowInsetsCompat.Type.systemBars()|androidx.core.view.WindowInsetsCompat.Type.displayCutout());
   screenHeight=bounds.height()-bars.top-bars.bottom;
  }
  int height=compact?ViewGroup.LayoutParams.WRAP_CONTENT:Math.min(available.height(),Math.round(screenHeight*.75f));
  WindowManager.LayoutParams current=window.getAttributes();
  if(current.width!=width||current.height!=height)window.setLayout(width,height);
  ViewGroup.LayoutParams layout=root.getLayoutParams();
  int rootHeight=compact?ViewGroup.LayoutParams.WRAP_CONTENT:ViewGroup.LayoutParams.MATCH_PARENT;
  if(layout.height!=rootHeight){layout.height=rootHeight;root.setLayoutParams(layout);}
 }
 private static Activity findActivity(Context c){while(c instanceof android.content.ContextWrapper){if(c instanceof Activity)return(Activity)c;c=((android.content.ContextWrapper)c).getBaseContext();}return null;}
 private int dp(int value){return UiStyle.dp(getContext(),value);}
}
