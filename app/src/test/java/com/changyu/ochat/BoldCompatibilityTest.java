package com.changyu.ochat;
import org.junit.Test;
import static org.junit.Assert.*;
public class BoldCompatibilityTest {
 @Test public void reproduceCommonMarkDelimiterFailure(){
  org.commonmark.parser.Parser parser=org.commonmark.parser.Parser.builder().build();
  for(String source:new String[]{"** 文本 **","这是**“重点”**内容"}){
   final int[] strong={0};parser.parse(source).accept(new org.commonmark.node.AbstractVisitor(){@Override public void visit(org.commonmark.node.StrongEmphasis node){strong[0]++;}});assertEquals(source,0,strong[0]);
   parser.parse(BoldCompatibility.normalize(source)).accept(new org.commonmark.node.AbstractVisitor(){@Override public void visit(org.commonmark.node.StrongEmphasis node){strong[0]++;}});assertEquals(source,1,strong[0]);
  }
 }
 @Test public void narrowExtension(){
  assertEquals("**文本**",BoldCompatibility.normalize("** 文本 **"));
  assertEquals("这是**\uE000“重点”\uE000**内容",BoldCompatibility.normalize("这是**“重点”**内容"));
  assertEquals("**甲** **乙**",BoldCompatibility.normalize("** 甲 ** ** 乙 **"));
  assertEquals("** 文本",BoldCompatibility.normalize("** 文本"));
 }
 @Test public void commonMarkAndProtectedContentUnchanged(){
  for(String s:new String[]{"**bold**","***nested***","****","* * *","** **","**\ntext\n**","\\** 文本 **","\\** literal ** next ** tail","`** 文本 **`","```md\n** 文本 **\n```","~~~\n** 文本 **","    ** 文本 **","$\\text{** 文本 **}$","\\[\n** 文本 **\n\\]","[label](https://host/** 文本 **) ","[id]: https://host/** 文本 **","<https://host/** 文本 **>","\uE000 ** 文本 **"})assertEquals(s,s,BoldCompatibility.normalize(s));
 }
 @Test public void longCompleteAndPartial(){StringBuilder s=new StringBuilder();for(int i=0;i<1000;i++)s.append("这是**“重点”**内容 ** 文本 **\n");String result=BoldCompatibility.normalize(s.toString());assertFalse(result.contains("** 文本 **"));assertTrue(result.contains("**文本**"));}
}
