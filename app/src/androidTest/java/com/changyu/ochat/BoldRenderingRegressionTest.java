package com.changyu.ochat;
import android.graphics.Typeface;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import io.noties.markwon.Markwon;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BoldRenderingRegressionTest {
 private static void bold(Spanned s,String target){int start=s.toString().indexOf(target);assertTrue(s.toString(),start>=0);for(int i=start;i<start+target.length();i++){boolean found=false;for(StyleSpan span:s.getSpans(i,i+1,StyleSpan.class))if((span.getStyle()&Typeface.BOLD)!=0&&s.getSpanStart(span)<=i&&s.getSpanEnd(span)>i)found=true;assertTrue("Missing BOLD at "+i+": "+s,found);}assertFalse(s.toString().contains("\uE000"));}
 @Test public void actualBoldRangesChineseQuotesSpacesNestedAdjacent(){
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   Markwon m=MarkdownRenderer.create(InstrumentationRegistry.getInstrumentation().getTargetContext());
   String[][] cases={{"这是**重点**内容","重点"},{"这是**“重点”**内容","“重点”"},{"这是**（重点）**内容","（重点）"},{"** 文本 **","文本"},{"**外层 *内层* 文字**","外层 内层 文字"},{"***嵌套***","嵌套"},{"**甲**、**乙**","甲","乙"},{"** 甲 **，**“乙”**内容","甲","“乙”"}};
   for(String[] row:cases){Spanned s=m.toMarkdown(row[0]);for(int i=1;i<row.length;i++)bold(s,row[i]);assertFalse(s.toString(),s.toString().contains("**"));}
  });
 }
 @Test public void streamingFinalIsCanonicalAndCannotLoseBold()throws Exception{
  TextView[] view=new TextView[1];StreamingMarkdown[] stream=new StreamingMarkdown[1];
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   Markwon m=MarkdownRenderer.create(InstrumentationRegistry.getInstrumentation().getTargetContext());view[0]=new TextView(InstrumentationRegistry.getInstrumentation().getTargetContext());
   MarkdownRenderer.render(m,view[0],"这是**“重");assertEquals("这是**“重",view[0].getText().toString());
   stream[0]=new StreamingMarkdown(new StringBuffer(),s->MarkdownRenderer.render(m,view[0],s));stream[0].append("** 文");
   stream[0].finish("这是**“重点”**内容 ** 文本 **");bold((Spanned)view[0].getText(),"“重点”");bold((Spanned)view[0].getText(),"文本");stream[0].append("stale");
  });
  Thread.sleep(250);InstrumentationRegistry.getInstrumentation().waitForIdleSync();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{bold((Spanned)view[0].getText(),"重点");assertFalse(view[0].getText().toString().contains("stale"));});
 }
 @Test public void codeMathTableLinksAndCopyRemain(){
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   Markwon m=MarkdownRenderer.create(InstrumentationRegistry.getInstrumentation().getTargetContext());
   String raw="** 文本 **\n$x$\n";
   Spanned s=m.toMarkdown("\\** literal\\**\n\n`** 文本 **`\n\n```latex\n"+raw+"```\n\n这是**“重点”**内容 $x^2$\n\n\\[x+y\\]\n\n[safe](https://example.com/**path**)\n\n| A | B |\n|---|---|\n| ** 文本 ** | value |");
   bold(s,"“重点”");assertTrue(s.toString().contains("** literal**"));assertTrue(s.toString().contains("** 文本 **"));
   MarkdownRenderer.CodeCopySpan[] copy=s.getSpans(0,s.length(),MarkdownRenderer.CodeCopySpan.class);assertEquals(1,copy.length);assertEquals(raw,copy[0].raw);
   StringBuilder names=new StringBuilder();for(Object span:s.getSpans(0,s.length(),Object.class))names.append(span.getClass().getSimpleName()).append(' ');
   for(String name:new String[]{"CodeSpan","CodeBlockSpan","TableRowSpan","LinkSpan","JLatexInlineAsyncDrawableSpan","JLatexAsyncDrawableSpan"})assertTrue(names.toString(),names.toString().contains(name));
  });
 }
 @Test public void longChineseTextUsesRealBoldSpans(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{Markwon m=MarkdownRenderer.create(InstrumentationRegistry.getInstrumentation().getTargetContext());StringBuilder source=new StringBuilder();for(int i=0;i<500;i++)source.append("这是**“重点").append(i).append("”**内容 ** 文本 **\n\n");Spanned s=m.toMarkdown(source.toString());assertTrue(s.getSpans(0,s.length(),StyleSpan.class).length>=1000);bold(s,"“重点499”");});}
}
