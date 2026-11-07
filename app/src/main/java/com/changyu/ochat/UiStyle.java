package com.changyu.ochat;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;

/** Shared spacing, surfaces and readable two-line rows for native screens. */
final class UiStyle {
 static int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
 static GradientDrawable surface(Context c,int color,int radius,boolean outline){
  ThemePalette p=ThemePalette.resolve(c);
  GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));
  if(outline)d.setStroke(dp(c,1),ColorUtils.blendARGB(color,p.ink,p.dark?.16f:.08f));
  return d;
 }
 static void textIfChanged(TextView view,CharSequence value){if(!android.text.TextUtils.equals(view.getText(),value))view.setText(value);}
 static void clickable(View view,int color,int radius){clickable(view,color,radius,true);}
 static void clickable(View view,int color,int radius,boolean outline){
  Context c=view.getContext();ThemePalette p=ThemePalette.resolve(c);
  view.setStateListAnimator(null);view.setElevation(0);view.setTranslationZ(0);
  view.setBackgroundTintList(null);
  view.setBackground(new RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(p.primary,32)),
   surface(c,color,radius,outline),null));
 }
 static TextView text(Context c,String value,int size,int color,boolean bold){
  TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);
  t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
  t.setLineSpacing(dp(c,2),1.08f);return t;
 }
 static View preference(Context c,String title,String summary,boolean active,View.OnClickListener action){
  ThemePalette p=ThemePalette.resolve(c);
  LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
  row.setPadding(dp(c,16),dp(c,15),dp(c,12),dp(c,15));row.setMinimumHeight(dp(c,76));
  clickable(row,active?ColorUtils.blendARGB(p.surface,p.primary,.08f):p.surface,20);
  LinearLayout copy=new LinearLayout(c);copy.setOrientation(LinearLayout.VERTICAL);
  copy.addView(text(c,title,16,active?p.primary:p.ink,true));
  TextView detail=text(c,summary,13,p.muted,false);detail.setPadding(0,dp(c,5),0,0);
  copy.addView(detail);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
  TextView arrow=text(c,"›",25,p.muted,false);arrow.setGravity(Gravity.CENTER);
  row.addView(arrow,new LinearLayout.LayoutParams(dp(c,28),-2));
  row.setOnClickListener(action);return row;
 }
 private UiStyle(){}
}
