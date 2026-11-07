package com.changyu.ochat;

import android.content.Context;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CodeViewportRegressionTest {
 private <T extends View> T find(View root,Class<T> type){
  if(type.isInstance(root))return type.cast(root);
  if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){T match=find(((ViewGroup)root).getChildAt(i),type);if(match!=null)return match;}
  return null;
 }
 private void layout(View view,Context context){
  int width=UiStyle.dp(context,360),height=UiStyle.dp(context,640);
  view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);
 }
 private void drag(View target,Context context,int x1,int y1,int x2,int y2){
  long time=android.os.SystemClock.uptimeMillis();
  for(int i=0;i<=8;i++){int action=i==0?MotionEvent.ACTION_DOWN:MotionEvent.ACTION_MOVE;
   MotionEvent event=MotionEvent.obtain(time,time+i*30,action,UiStyle.dp(context,x1+(x2-x1)*i/8),UiStyle.dp(context,y1+(y2-y1)*i/8),0);target.dispatchTouchEvent(event);event.recycle();}
  MotionEvent up=MotionEvent.obtain(time,time+330,MotionEvent.ACTION_UP,UiStyle.dp(context,x2),UiStyle.dp(context,y2),0);target.dispatchTouchEvent(up);up.recycle();
 }
 @Test public void collapsedDragsBothAxesAndExpandedDragsOnlyPage(){
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
   ScrollView page=new ScrollView(context);LinearLayout content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);page.addView(content);
   CodeBlockView block=new CodeBlockView(context);StringBuilder source=new StringBuilder();
   for(int i=0;i<80;i++)source.append("<path id=\"long-line-").append(i).append("\" d=\"M0 0 L100 20 L200 40 L300 60 L400 80 L500 100 L600 120\"/>\n");
   block.setSource(source.toString(),"svg");content.addView(block,new LinearLayout.LayoutParams(-1,-2));
   View tail=new View(context);content.addView(tail,new LinearLayout.LayoutParams(-1,UiStyle.dp(context,800)));layout(page,context);
   HorizontalScrollView horizontal=find(block,HorizontalScrollView.class);ScrollView vertical=find(block,ScrollView.class);
   assertNotNull(horizontal);assertNotNull(vertical);assertNotNull(block.findViewWithTag("code-horizontal-scrollbar"));assertTrue(vertical.isVerticalScrollBarEnabled());
   assertTrue(vertical.getHeight()<=UiStyle.dp(context,220));
   drag(page,context,300,160,40,160);assertTrue(horizontal.getScrollX()>0);assertEquals(0,page.getScrollY());
   drag(page,context,140,220,140,80);assertTrue(vertical.getScrollY()>0);assertEquals(0,page.getScrollY());
   block.setExpanded(true);layout(page,context);page.scrollTo(0,0);
   assertNull(find(block,ScrollView.class));assertNull(find(block,HorizontalScrollView.class));
   drag(page,context,140,220,140,80);assertTrue(page.getScrollY()>0);
   assertEquals(source.toString(),block.source());
  });
 }
 @Test public void streamingKeepsExpansionAndSourceSyntaxColors(){
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
   MarkdownMessageView message=new MarkdownMessageView(context,MarkdownRenderer.create(context));
   message.setMarkdown("说明\n\n```svg\n<svg width=\"10\">");
   CodeBlockView block=find(message,CodeBlockView.class);assertNotNull(block);block.setExpanded(true);
   message.setMarkdown("说明\n\n```svg\n<svg width=\"10\"><path d=\"M0 0\"/></svg>\n```\n结束");
   assertSame(block,find(message,CodeBlockView.class));assertTrue(block.isExpanded());
   Spanned highlighted=(Spanned)CodeBlockView.highlight(block.source());assertTrue(highlighted.getSpans(0,highlighted.length(),ForegroundColorSpan.class).length>=4);
   assertEquals(block.source(),highlighted.toString());
  });
 }
}
