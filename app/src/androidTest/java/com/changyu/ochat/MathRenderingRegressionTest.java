package com.changyu.ochat;

import android.text.Spanned;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import io.noties.markwon.Markwon;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MathRenderingRegressionTest {
 private static int formulas(Spanned s,String name){int n=0;for(Object span:s.getSpans(0,s.length(),Object.class))if(span.getClass().getSimpleName().equals(name))n++;return n;}
 @Test public void actualInlineAndDisplaySpansAndStreaming(){
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
   Markwon m=MarkdownRenderer.create(InstrumentationRegistry.getInstrumentation().getTargetContext());
   Spanned inline=m.toMarkdown("Euler $e^{i\\pi}+1=0$ and \\(x^2\\)");
   assertEquals(2,formulas(inline,"JLatexInlineAsyncDrawableSpan"));
   Spanned block=m.toMarkdown("Before\n\n$$\\frac{a}{b}$$\n\n\\[x^2+y^2\\]\n\nAfter");
   assertEquals(2,formulas(block,"JLatexAsyncDrawableSpan"));
   TextView v=new TextView(InstrumentationRegistry.getInstrumentation().getTargetContext());
   for(String partial:new String[]{"$","$x^{","$$","$$\n\\frac{a}","\\(x^","\\[x^"}){
    m.setMarkdown(v,partial);Spanned s=(Spanned)v.getText();assertEquals(partial,0,formulas(s,"JLatexInlineAsyncDrawableSpan")+formulas(s,"JLatexAsyncDrawableSpan"));
   }
   m.setMarkdown(v,"$x^2$");assertEquals(1,formulas((Spanned)v.getText(),"JLatexInlineAsyncDrawableSpan"));
   m.setMarkdown(v,"plain");assertEquals("plain",v.getText().toString());
   // Complete but invalid LaTeX remains a safe placeholder if asynchronous rendering fails.
   m.setMarkdown(v,"$\\unknowncommand{$");
  });
 }
 @Test public void codeAndEscapedDollarsRemainLiteral(){
  String code="```latex\n$x$ $$y$$ \\(z\\)\n```\n\n`$x$` and ``\\[y\\]``\n\n    $indented$\n";
  assertEquals(code,MathDelimiters.normalize(code));
  Markwon m=MarkdownRenderer.create(InstrumentationRegistry.getInstrumentation().getTargetContext());
  Spanned text=m.toMarkdown(code);assertEquals(0,formulas(text,"JLatexInlineAsyncDrawableSpan")+formulas(text,"JLatexAsyncDrawableSpan"));assertTrue(text.toString().contains("$x$"));
  assertEquals("\\$price",MathDelimiters.normalize("\\$price"));
  assertEquals("~~~\n$x$",MathDelimiters.normalize("~~~\n$x$"));
 }
}
