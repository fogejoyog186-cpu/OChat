package com.changyu.ochat;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

public class MarkdownRendererCodeTest {
 
 @Test public void extractsLiteralFencedSource(){
  String raw="```svg\n  <svg>**x**</svg>\n  <animate attributeName=\"x\"/>\n```";
  MarkdownRenderer.FencedSource source=MarkdownRenderer.extractFencedSources(raw).get(0);
  assertEquals("svg",source.language);
  // Extracted content does not include final newline (per CommonMark spec)
  assertEquals("  <svg>**x**</svg>\n  <animate attributeName=\"x\"/>",source.raw);
 }
 
 @Test public void partialFenceAtEnd(){
  String partial="some text\n```html\n<p>hi";
  List<MarkdownRenderer.FencedSource> blocks=MarkdownRenderer.extractFencedSources(partial);
  // Partial fence is not a complete block
  assertEquals(0,blocks.size());
 }
 
 @Test public void codeSourcePreservesLiterals(){
  String source="```js\n<svg onclick=\"bad\">\n**bold**\n$$math$$\n```";
  String processed=MarkdownRenderer.processMarkdown(source);
  // Code content should be preserved exactly
  assertTrue(processed.contains("<svg onclick=\"bad\">"));
  assertTrue(processed.contains("**bold**"));
  assertTrue(processed.contains("$$math$$"));
 }
 
 @Test public void proseBoldAndMathProcessed(){
  String source="**bold** $x^2$ text ```code\n**literal**\n``` more";
  String processed=MarkdownRenderer.processMarkdown(source);
  // Outside code: bold normalized (note: ** 文本 ** becomes **文本**, but **bold** is already valid),
  // math converted to $$
  assertTrue(processed.contains("$$x^2$$")); // Math normalized
  // Inside code: literals preserved
  assertTrue(processed.contains("**literal**"));
 }
 
 @Test public void htmlSanitizedOutsideCode(){
  String source="<script>bad</script> text ```html\n<script>literal</script>\n```";
  String processed=MarkdownRenderer.processMarkdown(source);
  // Outside code: script removed
  assertFalse(processed.contains("<script>bad"));
  // Inside code: script preserved
  assertTrue(processed.contains("<script>literal</script>"));
 }
 
 @Test public void unicodePunctuationPreserved(){
  // Unicode left/right double quotes in code blocks
  String source="```html\n<svg>text\u201cquote\u201d</svg>\n```";
  String processed=MarkdownRenderer.processMarkdown(source);
  assertTrue(processed.contains("text\u201cquote\u201d"));
  assertTrue(processed.contains("<svg>"));
 }
 
 @Test public void lessThanAndAsteriskLiteral(){
  String source="```xml\n< **\n```";
  String processed=MarkdownRenderer.processMarkdown(source);
  assertTrue(processed.contains("< **"));
 }
 
 @Test public void noTokenLeakage(){
  String source="```js\ncode\n``` text";
  String processed=MarkdownRenderer.processMarkdown(source);
  // Should not contain raw marker characters or escape sequences
  assertFalse(processed.contains("\u0001"));
  assertFalse(processed.contains("\\u0001"));
  assertFalse(processed.contains("\\u0002"));
  assertFalse(processed.contains("OCHAT_CODE"));
  assertFalse(processed.contains("nn")); // The corruption artifact
 }
 
 @Test public void multipleFences(){
  String source="```a\nfirst\n``` text ```b\nsecond\n``` end";
  String processed=MarkdownRenderer.processMarkdown(source);
  assertTrue(processed.contains("```a"));
  assertTrue(processed.contains("first"));
  assertTrue(processed.contains("```b"));
  assertTrue(processed.contains("second"));
 }
 
 @Test public void emptyFence(){
  String source="```\n```";
  List<MarkdownRenderer.FencedSource> blocks=MarkdownRenderer.extractFencedSources(source);
  assertEquals(1,blocks.size());
  assertEquals("代码",blocks.get(0).language);
  assertEquals("",blocks.get(0).raw);
 }
 
 @Test public void tildeFence(){
  String source="~~~python\ncode\n~~~";
  List<MarkdownRenderer.FencedSource> blocks=MarkdownRenderer.extractFencedSources(source);
  assertEquals(1,blocks.size());
  assertEquals("python",blocks.get(0).language);
  // Content without final newline
  assertEquals("code",blocks.get(0).raw);
 }
}
