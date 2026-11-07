package com.changyu.ochat;

import org.junit.Test;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleRegexTest {
 @Test public void testSimpleFence(){
  // Simplest possible pattern
  Pattern p=Pattern.compile("```([^\\n]*)\\n(.*?)\\n```",Pattern.DOTALL);
  String source="<script>bad</script> text ```html\n<script>literal</script>\n```";
  
  System.out.println("Source: [" + source + "]");
  Matcher m=p.matcher(source);
  int count=0;
  while(m.find()) {
   count++;
   System.out.println("Match " + count);
   System.out.println("  Lang: [" + m.group(1) + "]");
   System.out.println("  Body: [" + m.group(2) + "]");
  }
  System.out.println("Total: " + count);
 }
}
