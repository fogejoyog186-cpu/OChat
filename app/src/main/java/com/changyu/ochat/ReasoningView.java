package com.changyu.ochat;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.noties.markwon.Markwon;
import java.util.function.Consumer;

/** Lazy, collapsible provider reasoning. Collapsed updates don't parse or lay out the full text. */
final class ReasoningView extends LinearLayout {
 private final TextView heading;
 private final MarkdownMessageView content;
 private final Consumer<Boolean> changed;
 private String text="",rendered="",title="思考过程";
 private boolean expanded;
 ReasoningView(Context c,Markwon markdown,Consumer<Boolean> changed){
  super(c);this.changed=changed;setTag("reasoning-view");setOrientation(VERTICAL);
  ThemePalette p=ThemePalette.resolve(c);
  heading=UiStyle.text(c,"",13,p.muted,false);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setMinHeight(UiStyle.dp(c,44));heading.setPadding(UiStyle.dp(c,12),UiStyle.dp(c,4),UiStyle.dp(c,12),UiStyle.dp(c,4));
  UiStyle.clickable(heading,p.page,10,false);addView(heading,new LayoutParams(-1,-2));
  content=new MarkdownMessageView(c,markdown);content.setTag("reasoning-markdown");content.setTextSize(14);content.setLineSpacing(UiStyle.dp(c,3),1.12f);
  content.setPadding(UiStyle.dp(c,12),UiStyle.dp(c,10),UiStyle.dp(c,12),UiStyle.dp(c,12));content.setBackground(UiStyle.surface(c,androidx.core.graphics.ColorUtils.blendARGB(p.page,p.ink,p.dark?.06f:.035f),12,false));content.setVisibility(GONE);LayoutParams body=new LayoutParams(-1,-2);body.bottomMargin=UiStyle.dp(c,12);addView(content,body);
  heading.setOnClickListener(v->{setExpanded(!expanded);changed.accept(expanded);});
 }
 void bind(String text,boolean thinking,boolean interrupted,boolean summary,boolean expanded){
  this.text=text==null?"":text;
  title=thinking?"正在思考":interrupted?"思考已中断":summary?"思考摘要":"思考过程";
  setVisibility(this.text.isEmpty()?GONE:VISIBLE);setExpanded(expanded);
 }
 private void setExpanded(boolean value){
  expanded=value;UiStyle.textIfChanged(heading,title+(expanded?"  ▾":"  ▸"));
  heading.setContentDescription((expanded?"收起":"展开")+"思考内容");
  if(android.os.Build.VERSION.SDK_INT>=30)heading.setStateDescription(expanded?"已展开":"已折叠");
  content.setVisibility(expanded?VISIBLE:GONE);
  if(expanded&&!text.equals(rendered)){content.setMarkdown(text);rendered=text;}
 }
 boolean expanded(){return expanded;}
}
