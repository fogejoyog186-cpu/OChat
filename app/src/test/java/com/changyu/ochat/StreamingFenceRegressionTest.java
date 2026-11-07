package com.changyu.ochat;

import org.junit.Test;
import static org.junit.Assert.*;

public class StreamingFenceRegressionTest {
 @Test public void everyStreamingSvgSnapshotRetainsExactTagsAndAttributes(){
  String source="说明\n\n```svg\n<svg viewBox=\"0 0 800 600\"><title>骑车的鹅</title>\n<desc>白色的鹅</desc><style>.a{fill:#fff}</style>\n<path d=\"M0 0 L80 60\"/><text>**literal** $x$</text></svg>\n```";
  for(int end=source.indexOf("<svg");end<=source.length();end++){
   String snapshot=source.substring(0,end);
   String processed=MarkdownRenderer.processMarkdown(snapshot);
   assertEquals("snapshot at "+end,snapshot,processed);
   StringBuilder literal=new StringBuilder();
   org.commonmark.parser.Parser.builder().build().parse(processed).accept(new org.commonmark.node.AbstractVisitor(){
    @Override public void visit(org.commonmark.node.FencedCodeBlock block){literal.append(block.getLiteral());}
   });
   int bodyStart=source.indexOf("<svg"),closeStart=source.lastIndexOf("\n```");
   assertTrue("Actual Markdown parser must retain SVG source at "+end,literal.toString().startsWith(source.substring(bodyStart,Math.min(end,closeStart))));
  }
 }
 @Test public void longerFenceProtectsShorterFenceInsideSource(){
  String source="````html\n<svg>\n```\n<script>literal</script>\n</svg>\n````";
  assertEquals(source,MarkdownRenderer.processMarkdown(source));
  assertEquals(1,MarkdownRenderer.extractFencedSources(source).size());
 }
 @Test public void crlfAndLongerClosingFenceRemainUnchanged(){
  String source="~~~xml\r\n<svg>\r\n<path d=\"M0 0\"/>\r\n</svg>\r\n~~~~\r\n";
  assertEquals(source,MarkdownRenderer.processMarkdown(source));
  assertEquals("<svg>\r\n<path d=\"M0 0\"/>\r\n</svg>",MarkdownRenderer.extractFencedSources(source).get(0).raw);
 }
 @Test public void proseHtmlUsesRealLineBreaks(){
  String result=MarkdownRenderer.processMarkdown("<p>first</p><p>second</p>");
  assertTrue(result.contains("\n\n"));
  assertFalse(result.contains("nn"));
 }
 @Test public void svgCanvasFitsViewportAndKeepsArtwork(){
  String svg="<svg viewBox=\"0 0 800 600\"><path d=\"M0 0 L80 60\"/></svg>";
  String html=MarkdownRenderer.sanitizePreviewDocument(svg);
  assertTrue(html.contains(svg));assertTrue(html.contains("max-height:calc(100vh - 32px)"));
 }
}
