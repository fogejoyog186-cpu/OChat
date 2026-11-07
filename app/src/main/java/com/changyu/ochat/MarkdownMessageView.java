package com.changyu.ochat;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import io.noties.markwon.Markwon;
import java.util.*;

/** Reuses each code viewport across streamed snapshots so expansion and scroll positions stay stable. */
final class MarkdownMessageView extends LinearLayout {
 private final Markwon renderer;private final ThemePalette palette;
 private String source="",messageId="";private float size=16,extra=0,multiplier=1.18f;
 MarkdownMessageView(Context context,Markwon renderer){
  super(context);this.renderer=renderer;palette=ThemePalette.resolve(context);setOrientation(VERTICAL);setTag("markdown-message");
 }
 void setMarkdown(String source){
  if(source==null)source="";
  if(this.source.equals(source)&&getChildCount()>0)return;this.source=source;
  int cursor=0,index=0;
  for(MarkdownRenderer.Fence fence:MarkdownRenderer.fences(source)){
   String prose=source.substring(cursor,fence.start);if(!prose.trim().isEmpty())index=prose(index,prose);
   String raw=source.substring(fence.bodyStart,fence.bodyEnd);
   CodeBlockView code;
   if(index<getChildCount()&&getChildAt(index) instanceof CodeBlockView)code=(CodeBlockView)getChildAt(index);else{
    if(index<getChildCount())removeViewAt(index);code=new CodeBlockView(getContext());
    LayoutParams params=new LayoutParams(-1,-2);params.setMargins(0,dp(8),0,dp(8));addView(code,index,params);
   }
   code.setSource(raw,fence.language);index++;cursor=fence.end;
  }
  String tail=source.substring(cursor);if(!tail.trim().isEmpty()||index==0)index=prose(index,tail);
  if(index<getChildCount())removeViews(index,getChildCount()-index);
 }
 private static final class ProseText extends androidx.appcompat.widget.AppCompatTextView{String rendered;ProseText(Context context){super(context);}}
 private int prose(int index,String source){
  ProseText text;
  if(index<getChildCount()&&getChildAt(index) instanceof ProseText)text=(ProseText)getChildAt(index);else{
   if(index<getChildCount())removeViewAt(index);text=new ProseText(getContext());text.setTextColor(palette.ink);text.setLinkTextColor(palette.primary);
   addView(text,index,new LayoutParams(-1,-2));
  }
  if(!source.equals(text.rendered)){text.setTextSize(size);text.setLineSpacing(extra,multiplier);MarkdownRenderer.render(renderer,text,source);text.rendered=source;}return index+1;
 }
 void setTextSize(float size){this.size=size;for(int i=0;i<getChildCount();i++)if(getChildAt(i) instanceof TextView)((TextView)getChildAt(i)).setTextSize(size);}
 void setLineSpacing(float extra,float multiplier){this.extra=extra;this.multiplier=multiplier;for(int i=0;i<getChildCount();i++)if(getChildAt(i) instanceof TextView)((TextView)getChildAt(i)).setLineSpacing(extra,multiplier);}
 void setMessageId(String id){messageId=id;}
 String messageId(){return messageId;}
 Bundle codeState(){
  Bundle bundle=new Bundle();int index=0;for(int i=0;i<getChildCount();i++)if(getChildAt(i) instanceof CodeBlockView)bundle.putBundle(Integer.toString(index++),((CodeBlockView)getChildAt(i)).state());
  return bundle;
 }
 void restoreCodeState(Bundle state){
  if(state==null)return;int index=0;for(int i=0;i<getChildCount();i++)if(getChildAt(i) instanceof CodeBlockView)((CodeBlockView)getChildAt(i)).restore(state.getBundle(Integer.toString(index++)));
 }
 private int dp(int n){return UiStyle.dp(getContext(),n);}
}
