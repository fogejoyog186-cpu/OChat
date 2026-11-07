package com.changyu.ochat;

import org.junit.Test;
import static org.junit.Assert.*;

public class MarkdownRendererSecurityTest {
 @Test public void extractsLiteralFencedSource(){
  String raw="```svg\n  <svg>**x**</svg>\n  <animate attributeName=\"x\"/>\n```";
  MarkdownRenderer.FencedSource source=MarkdownRenderer.extractFencedSources(raw).get(0);
  assertEquals("svg",source.language);
  assertEquals("  <svg>**x**</svg>\n  <animate attributeName=\"x\"/>",source.raw);
 }
 @Test public void previewPreservesSvgReferencesAndExternalResources(){
  String svg="<svg><defs><linearGradient id=\"g\"/></defs><rect fill=\"url('#g')\" filter=\"url(#shadow)\"/><use href=\"#shape\"/><image href=\"https://example.com/image.svg\"/></svg>";
  String document=invoke(svg);
  assertTrue(document.contains(svg));
  assertTrue(document.contains("name=\"viewport\""));
 }
 @Test public void previewPreservesFontsStylesAndImages(){
  String html="<link rel=\"stylesheet\" href=\"https://example.com/style.css\"><style>@font-face{src:url(https://example.com/font.woff2)}</style><img src=\"data:image/png;base64,abc\">";
  assertTrue(invoke(html).contains(html));
 }
 @Test public void fullHtmlDocumentIsNotNested(){
  String html="<!DOCTYPE html><html><head><title>Example</title></head><body>Test</body></html>";
  assertEquals(html,invoke(html));
 }
 private String invoke(String source){try{java.lang.reflect.Method m=MarkdownRenderer.class.getDeclaredMethod("sanitizePreviewDocument",String.class);m.setAccessible(true);return (String)m.invoke(null,source);}catch(Exception e){throw new AssertionError(e);}}
}
