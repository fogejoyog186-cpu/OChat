package com.changyu.ochat;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.ColorUtils;
import java.util.function.Consumer;

/** Compact power control; changes remain local until Confirm. */
final class ReasoningSheet {
 static PanelDialog show(Context context,String model,ModelCapabilities caps,String initial,Consumer<String> confirm){return show(context,model,caps.efforts,caps.normalize(initial),confirm,caps.description());}
 private static PanelDialog show(Context context,String model,String[] supported,String initial,Consumer<String> confirm,String explanation){
  String safeInitial=java.util.Arrays.asList(supported).contains(initial)?initial:"default";
  ThemePalette p=ThemePalette.resolve(context);
  ReasoningAppearance appearance=new ReasoningAppearance(context);
  String[] filtered=java.util.Arrays.stream(supported).filter(v->!"default".equals(v)).toArray(String[]::new);final String[] values=filtered.length==0?new String[]{"default"}:filtered;
  final String[] levels=values;

  LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);
  LinearLayout card=new LinearLayout(context);card.setOrientation(LinearLayout.VERTICAL);
  card.setPadding(dp(context,18),dp(context,18),dp(context,18),dp(context,14));
  card.setBackground(UiStyle.surface(context,p.page,20,true));

  LinearLayout valueRow=new LinearLayout(context);valueRow.setGravity(Gravity.CENTER_VERTICAL);
  valueRow.setMinimumHeight(dp(context,34));
  TextView current=UiStyle.text(context,"",23,p.ink,true);current.setIncludeFontPadding(false);
  valueRow.addView(current,new LinearLayout.LayoutParams(0,-2,1));
  TextView step=UiStyle.text(context,"",11,p.primary,true);step.setGravity(Gravity.CENTER);
  step.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
  step.setPadding(dp(context,10),dp(context,5),dp(context,10),dp(context,5));
  step.setBackground(UiStyle.surface(context,ColorUtils.blendARGB(p.page,p.primary,.09f),10,false));
  valueRow.addView(step,new LinearLayout.LayoutParams(-2,-2));card.addView(valueRow);
  TextView detail=UiStyle.text(context,"",13,p.muted,false);
  detail.setPadding(0,dp(context,8),0,0);detail.setMinLines(2);detail.setMaxLines(3);
  card.addView(detail,new LinearLayout.LayoutParams(-1,-2));

  ReasoningSlider slider=new ReasoningSlider(context,p);slider.setMax(values.length-1);slider.setFirstStopDisabled("none".equals(values[0])||"default".equals(values[0]));
  slider.setContentDescription("思考强度滑动条");
  int chosen=0;for(int i=0;i<values.length;i++)if(values[i].equals(safeInitial))chosen=i;
  slider.setProgress(chosen);card.addView(slider,new LinearLayout.LayoutParams(-1,dp(context,56)));
  LinearLayout ends=new LinearLayout(context);ends.setGravity(Gravity.CENTER_VERTICAL);
  ends.setPadding(dp(context,4),0,dp(context,4),0);
  boolean toggle=java.util.Arrays.asList(values).contains("enabled");
  TextView faster=UiStyle.text(context,toggle?"关闭思考":"更快",12,p.muted,false);
  TextView deeper=UiStyle.text(context,toggle?"开启思考":"更深入",12,p.muted,false);deeper.setGravity(Gravity.END);
  ends.addView(faster,new LinearLayout.LayoutParams(0,-2,1));
  ends.addView(deeper,new LinearLayout.LayoutParams(0,-2,1));card.addView(ends);
  body.addView(card,new LinearLayout.LayoutParams(-1,-2));

  SwitchCompat defaults=new SwitchCompat(context);defaults.setText("使用模型默认值");
  defaults.setTextColor(p.ink);defaults.setTextSize(14);defaults.setMinHeight(dp(context,52));
  defaults.setPadding(dp(context,4),0,dp(context,4),0);
  defaults.setContentDescription("使用模型默认思考强度");
  int[][] switchStates={new int[]{android.R.attr.state_checked},new int[]{}};
  defaults.setThumbTintList(new ColorStateList(switchStates,new int[]{p.primary,p.dark?0xFFB4C5CD:0xFFFFFFFF}));
  defaults.setTrackTintList(new ColorStateList(switchStates,new int[]{
   ColorUtils.blendARGB(p.page,p.primary,.35f),ColorUtils.blendARGB(p.page,p.ink,.18f)}));
  defaults.setChecked("default".equals(safeInitial));
  LinearLayout.LayoutParams defaultParams=new LinearLayout.LayoutParams(-1,-2);defaultParams.topMargin=dp(context,6);
  body.addView(defaults,defaultParams);

  Runnable render=()->{
   boolean automatic=defaults.isChecked();String value=levels[slider.getProgress()];
   String label=automatic?"Auto":label(value);
   UiStyle.textIfChanged(current,label);
   UiStyle.textIfChanged(step,automatic?"AUTO":(slider.getProgress()+1)+" / "+values.length);
   UiStyle.textIfChanged(detail,automatic?explanation:description(value));
   slider.setAutomatic(automatic);
   if(android.os.Build.VERSION.SDK_INT>=30)slider.setStateDescription(automatic?"模型默认":label+"，第 "+(slider.getProgress()+1)+" 档，共 "+values.length+" 档");
  };
  defaults.setOnCheckedChangeListener((button,checked)->render.run());
  slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   public void onStartTrackingTouch(SeekBar bar){defaults.setChecked(false);}
   public void onStopTrackingTouch(SeekBar bar){}
   public void onProgressChanged(SeekBar bar,int position,boolean user){if(user)defaults.setChecked(false);render.run();}
  });render.run();
  appearance.apply(slider,p);
  ScrollView scroll=new ScrollView(context);scroll.setFillViewport(false);scroll.addView(body);
  PanelDialog dialog=new PanelDialog(context,"思考强度",model,scroll).compact();
  dialog.action(AlertDialog.BUTTON_NEGATIVE,"取消",null)
   .action(AlertDialog.BUTTON_POSITIVE,"确认",()->confirm.accept(defaults.isChecked()?"default":levels[slider.getProgress()]));
  dialog.show();return dialog;
 }
 static String label(String value){return "default".equals(value)?"Auto":value;}
 private static String description(String value){
  switch(value){
   case "none":return "关闭可选思考，优先响应速度。";
   case "enabled":return "开启思考，由模型决定具体推理深度。";
   case "minimal":return "少量思考，适合简单问题。";
   case "low":return "轻量思考，快速梳理问题。";
   case "high":return "深入分析，适合复杂任务。";
   case "xhigh":return "更充分地推敲细节，回复需要更久。";
   case "max":return "使用最高思考强度，适合高难度任务。";
   default:return "兼顾响应速度与思考深度。";
  }
 }
 private static int dp(Context context,int value){return UiStyle.dp(context,value);}
 private ReasoningSheet(){}
}
