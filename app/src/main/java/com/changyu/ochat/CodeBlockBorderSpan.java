package com.changyu.ochat;

import android.graphics.*;
import android.text.*;
import android.text.style.LeadingMarginSpan;

/** Adds a continuous outline to native Markdown code without replacing selectable source text. */
final class CodeBlockBorderSpan implements LeadingMarginSpan {
 private final float width;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 CodeBlockBorderSpan(int color,float width){this.width=width;paint.setColor(color);paint.setStrokeWidth(width);paint.setStyle(Paint.Style.STROKE);}
 @Override public int getLeadingMargin(boolean first){return 0;}
 @Override public void drawLeadingMargin(Canvas canvas,Paint original,int x,int direction,int top,int baseline,int bottom,CharSequence text,int start,int end,boolean first,Layout layout){
  if(!(text instanceof Spanned))return;
  Spanned spans=(Spanned)text;int from=spans.getSpanStart(this),to=spans.getSpanEnd(this);if(from<0||to<=from)return;
  float left=width/2f;
  float right=layout.getWidth()-width/2f;
  canvas.drawLine(left,top,left,bottom,paint);canvas.drawLine(right,top,right,bottom,paint);
  if(layout.getLineForOffset(start)==layout.getLineForOffset(from))canvas.drawLine(left,top+width/2f,right,top+width/2f,paint);
  if(layout.getLineForOffset(start)==layout.getLineForOffset(Math.max(from,to-1)))canvas.drawLine(left,bottom-width/2f,right,bottom-width/2f,paint);
 }
}
