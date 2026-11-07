package com.changyu.ochat;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.Gravity;
import android.widget.*;
import java.util.Locale;

/** Theme-local color draft, persisted only by the theme page save action. */
final class ReasoningAppearance {
 static final String MODE="reasoningColorMode",CUSTOM="reasoningCustomColor";
 static final String[] MODES={"cyan","blue","purple","custom"};
 static final String[] NAMES={"青色","GPT 蓝色","GPT 紫色","自定义"};
 private final SharedPreferences prefs;
 String mode;int custom;
 ReasoningAppearance(Context c){
  prefs=c.getSharedPreferences("chat",Context.MODE_PRIVATE);mode=prefs.getString(MODE,"cyan");
  if("gpt".equals(mode))mode="blue";
  if(!java.util.Arrays.asList(MODES).contains(mode))mode="cyan";
  try{custom=Color.parseColor(prefs.getString(CUSTOM,"#4089F6"))|0xff000000;}catch(Exception invalid){custom=0xff4089f6;}
 }
 int color(ThemePalette p){
  switch(mode){
   case "blue":return 0xff0285ff;
   case "purple":return 0xff9859ef;
   case "custom":return custom;
   default:return p.primary;
  }
 }
 void writeTo(SharedPreferences.Editor editor){editor.putString(MODE,mode).putString(CUSTOM,hex(custom));}
 void restore(android.os.Bundle state){if(state==null)return;String saved=state.getString(MODE,mode);if("gpt".equals(saved))saved="blue";if(java.util.Arrays.asList(MODES).contains(saved))mode=saved;custom=state.getInt(CUSTOM,custom);}
 void saveDraft(android.os.Bundle state){state.putString(MODE,mode);state.putInt(CUSTOM,custom);}
 void apply(ReasoningSlider slider,ThemePalette p){slider.setColor(color(p));}
 void controls(Context c,LinearLayout body){
  ThemePalette p=ThemePalette.resolve(c);
  LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(c,14),0,dp(c,14),0);
  row.setContentDescription("思考强度动画颜色");UiStyle.clickable(row,p.page,16);
  android.view.View swatch=new android.view.View(c);LinearLayout.LayoutParams swatchParams=new LinearLayout.LayoutParams(dp(c,24),dp(c,24));swatchParams.rightMargin=dp(c,12);row.addView(swatch,swatchParams);
  TextView name=UiStyle.text(c,"",14,p.ink,false);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
  row.addView(UiStyle.text(c,"›",22,p.muted,false));
  Runnable render=()->{
   int index=java.util.Arrays.asList(MODES).indexOf(mode);name.setText("动画配色 · "+NAMES[Math.max(0,index)]+("custom".equals(mode)?"  "+hex(custom):""));
   GradientDrawable fill=new GradientDrawable();fill.setColor(color(p));fill.setCornerRadius(dp(c,7));swatch.setBackground(fill);
  };
  row.setOnClickListener(v->ChoiceDialogs.single(c,"动画配色",NAMES,java.util.Arrays.asList(MODES).indexOf(mode),which->{
   if("custom".equals(MODES[which]))custom(c,color->{custom=color;mode="custom";render.run();});
   else{mode=MODES[which];render.run();}
  }));
  LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(c,54));params.topMargin=dp(c,8);body.addView(row,params);render.run();
 }
 private void custom(Context c,java.util.function.IntConsumer accept){
  ThemePalette p=ThemePalette.resolve(c);LinearLayout body=new LinearLayout(c);body.setOrientation(LinearLayout.VERTICAL);
  android.view.View preview=new android.view.View(c);preview.setContentDescription("自定义颜色预览");body.addView(preview,new LinearLayout.LayoutParams(-1,dp(c,44)));
  EditText hex=new EditText(c);hex.setSingleLine(true);hex.setTextSize(16);hex.setTextColor(p.ink);hex.setHint("#RRGGBB");hex.setContentDescription("颜色 HEX");hex.setPadding(dp(c,14),0,dp(c,14),0);hex.setBackground(UiStyle.surface(c,p.page,14,true));
  LinearLayout.LayoutParams input=new LinearLayout.LayoutParams(-1,dp(c,50));input.topMargin=dp(c,12);body.addView(hex,input);
  SeekBar[] channels=new SeekBar[3];int[] value={custom};boolean[] updating={false};
  Runnable paint=()->{GradientDrawable bg=new GradientDrawable();bg.setColor(value[0]);bg.setCornerRadius(dp(c,12));preview.setBackground(bg);};
  for(int i=0;i<3;i++){
   TextView caption=UiStyle.text(c,new String[]{"R · 红","G · 绿","B · 蓝"}[i],12,p.muted,false);body.addView(caption);
   SeekBar bar=new SeekBar(c);channels[i]=bar;bar.setMax(255);bar.setContentDescription(caption.getText());bar.setProgress((custom>>(16-i*8))&255);body.addView(bar,new LinearLayout.LayoutParams(-1,dp(c,42)));
   bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
    public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}
    public void onProgressChanged(SeekBar s,int progress,boolean user){if(updating[0]||!user)return;value[0]=Color.rgb(channels[0].getProgress(),channels[1].getProgress(),channels[2].getProgress());updating[0]=true;hex.setText(hex(value[0]));hex.setSelection(hex.length());updating[0]=false;paint.run();}
   });
  }
  hex.setText(hex(custom));hex.addTextChangedListener(new TextWatcher(){
   public void beforeTextChanged(CharSequence s,int a,int count,int after){}public void onTextChanged(CharSequence s,int a,int before,int count){
    if(updating[0])return;try{value[0]=parse(s.toString());updating[0]=true;for(int i=0;i<3;i++)channels[i].setProgress((value[0]>>(16-i*8))&255);updating[0]=false;hex.setError(null);paint.run();}catch(IllegalArgumentException ignored){}
   }public void afterTextChanged(Editable e){}
  });paint.run();
  PanelDialog dialog=new PanelDialog(c,"自定义动画颜色","拖动 RGB 滑动条，或输入 HEX 色值",body).compact().action(AlertDialog.BUTTON_NEGATIVE,"取消",null).action(AlertDialog.BUTTON_POSITIVE,"应用",null);
  dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{accept.accept(parse(hex.getText().toString()));dialog.dismiss();}catch(IllegalArgumentException invalid){hex.setError("请输入 6 位色值，例如 #4089F6");}});
 }
 static int parse(String raw){String s=raw.trim();if(!s.startsWith("#"))s="#"+s;if(!s.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("无效颜色");return Color.parseColor(s);}
 private static String hex(int color){return String.format(Locale.ROOT,"#%06X",color&0xffffff);}
 private static int dp(Context c,int n){return UiStyle.dp(c,n);}
}
