package com.changyu.ochat;

import android.content.Context;
import android.text.Spanned;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import io.noties.markwon.Markwon;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LongMarkdownRegressionTest {
 private String document(){
  StringBuilder s=new StringBuilder("# Long response\n\n");
  for(int i=0;i<240;i++)s.append("Paragraph ").append(i).append(" **bold** [link](https://example.com) with substantial unchanged canonical content.\n\n");
  return s.append("1. first\n2. second\n   - nested\n\n```java\nString literal = \"$notMath$\";\n```\n\nInline $x^2$ and \\(y^2\\).\n\n\\[x+y\\]\n\n| A | B |\n|---|---|\n| one | two |\n\n**FINAL_SENTINEL**").toString();
 }
 private void spans(TextView view){
  Spanned text=(Spanned)view.getText();StringBuilder names=new StringBuilder();
  for(Object span:text.getSpans(0,text.length(),Object.class))names.append(span.getClass().getSimpleName()).append(' ');
  for(String name:new String[]{"HeadingSpan","StrongEmphasisSpan","OrderedListItemSpan","BulletListItemSpan","CodeBlockSpan","LinkSpan","TableRowSpan","JLatexInlineAsyncDrawableSpan","JLatexAsyncDrawableSpan"})assertTrue(name+": "+names,names.toString().contains(name));
  assertTrue(view.isTextSelectable());assertTrue(view.getLinksClickable());
  assertTrue(view.getText().toString().contains("FINAL_SENTINEL"));
  assertTrue(view.getText().toString().contains("$notMath$"));
 }
 @Test public void longSequentialSnapshotsThenCompletionKeepAllFinalSpans(){
  String source=document();assertTrue(source.length()>20000);
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Markwon m=MarkdownRenderer.create(c);TextView view=new TextView(c);
   for(int end=2048;end<source.length();end+=2048)MarkdownRenderer.render(m,view,source.substring(0,end));
   MarkdownRenderer.render(m,view,source);spans(view);
   assertEquals(m.toMarkdown(source).toString(),view.getText().toString());
  });
 }
 @Test public void queuedTokensAreCoalescedAndFinalFlushCannotBeOverwritten()throws Exception{
  String source=document();AtomicInteger renders=new AtomicInteger();TextView[] view=new TextView[1];StreamingMarkdown[] stream=new StreamingMarkdown[1];StringBuffer buffer=new StringBuffer();
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Markwon m=MarkdownRenderer.create(c);view[0]=new TextView(c);
   stream[0]=new StreamingMarkdown(buffer,s->{renders.incrementAndGet();MarkdownRenderer.render(m,view[0],s);});
   // Keep main occupied so thousands of tokens must share a single pending preview.
   for(int i=0;i<source.length();i+=7)stream[0].append(source.substring(i,Math.min(source.length(),i+7)));
   assertEquals(source,buffer.toString());assertEquals(0,renders.get());
   stream[0].finish(buffer.toString());assertEquals(1,renders.get());spans(view[0]);
   stream[0].append("STALE");
  });
  Thread.sleep(250);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{assertEquals(1,renders.get());spans(view[0]);assertEquals(source,buffer.toString());});
 }
}
