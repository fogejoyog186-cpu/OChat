package com.changyu.ochat;
import org.junit.Test;
import static org.junit.Assert.*;
public class MathDelimitersTest {
 @Test public void normalizeCompleteOnly(){assertEquals("a $$x^2$$ b",MathDelimiters.normalize("a $x^2$ b"));assertEquals("$$x$$",MathDelimiters.normalize("\\(x\\)"));assertEquals("\n\n$$\nx\n$$\n\n",MathDelimiters.normalize("$$x$$"));assertEquals("\\$x",MathDelimiters.normalize("$x"));}
 @Test public void unmatchedInlineBackticksDoNotPairAcrossFences(){
  String s="` unmatched $x$\n\n```java\n$literal$\n```\n\n` unmatched $y$";
  assertEquals("` unmatched $$x$$\n\n```java\n$literal$\n```\n\n` unmatched $$y$$",MathDelimiters.normalize(s));
 }
 @Test public void backtickInFenceInfoIsNotAnOpeningFence(){
  assertEquals("```bad`\n$$x$$",MathDelimiters.normalize("```bad`\n$x$"));
 }
 @Test public void protectsCode(){for(String s:new String[]{"```java\n$x$\n```","~~~\n$$x$$","`$x$`","``\\(x\\)``","    $x$","\\$x\\$"})assertEquals(s,MathDelimiters.normalize(s));}
}
