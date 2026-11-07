package com.changyu.ochat;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.SpinnerAdapter;

/** Spinner-compatible field backed by the app's confirmed selection panel. */
final class ChoiceSpinner extends androidx.appcompat.widget.AppCompatSpinner {
 private final String title;private final Paint arrow=new Paint(Paint.ANTI_ALIAS_FLAG);private final int edge,tip;
 ChoiceSpinner(Context context,String title){
  super(context);this.title=title;ThemePalette palette=ThemePalette.resolve(context);
  edge=UiStyle.dp(context,18);tip=UiStyle.dp(context,4);arrow.setColor(palette.muted);arrow.setStrokeWidth(UiStyle.dp(context,2));arrow.setStrokeCap(Paint.Cap.ROUND);
  setContentDescription(title);setMinimumHeight(UiStyle.dp(context,56));setPadding(UiStyle.dp(context,12),UiStyle.dp(context,4),UiStyle.dp(context,32),UiStyle.dp(context,4));
  setBackgroundTintList(null);setBackground(UiStyle.surface(context,palette.surface,16,true));setStateListAnimator(null);setElevation(0);
 }
 @Override public boolean performClick(){
  SpinnerAdapter adapter=getAdapter();if(adapter==null||adapter.getCount()==0)return false;
  String[] labels=new String[adapter.getCount()];for(int i=0;i<labels.length;i++)labels[i]=String.valueOf(adapter.getItem(i));
  ChoiceDialogs.single(getContext(),title,labels,getSelectedItemPosition(),this::setSelection);return true;
 }
 @Override public boolean onTouchEvent(MotionEvent event){
  if(!isEnabled())return false;
  if(event.getActionMasked()==MotionEvent.ACTION_DOWN){setPressed(true);return true;}
  if(event.getActionMasked()==MotionEvent.ACTION_UP){setPressed(false);performClick();return true;}
  if(event.getActionMasked()==MotionEvent.ACTION_CANCEL)setPressed(false);
  return true;
 }
 @Override protected void dispatchDraw(Canvas canvas){
  super.dispatchDraw(canvas);
  float cx=getWidth()-edge,cy=getHeight()/2f,s=tip;
  canvas.drawLine(cx-s,cy-s/2,cx,cy+s/2,arrow);canvas.drawLine(cx,cy+s/2,cx+s,cy-s/2,arrow);
 }
}
