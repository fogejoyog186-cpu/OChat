package com.changyu.ochat;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.assertEquals;

public class MarkdownRendererDebugTest {
 @Test public void responseFinalEmptyDoesNotEraseDeltas() throws Exception {
  ResponseStream stream=new ResponseStream();
  stream.accept("response.output_text.delta",new org.json.JSONObject().put("type","response.output_text.delta").put("output_index",0).put("content_index",0).put("delta","partial "));
  stream.accept("response.completed",new org.json.JSONObject().put("type","response.completed").put("response",new org.json.JSONObject().put("output",new org.json.JSONArray().put(new org.json.JSONObject().put("type","message").put("content",new org.json.JSONArray().put(new org.json.JSONObject().put("type","output_text").put("text","")))))));
  assertEquals("partial ",stream.text());
 }
 @Test public void responseFinalNonEmptyReplacesOnlyItsPart() throws Exception {
  ResponseStream stream=new ResponseStream();
  stream.accept("response.output_text.delta",new org.json.JSONObject().put("type","response.output_text.delta").put("output_index",0).put("content_index",0).put("delta","old"));
  stream.accept("response.completed",new org.json.JSONObject().put("type","response.completed").put("response",new org.json.JSONObject().put("output",new org.json.JSONArray().put(new org.json.JSONObject().put("type","message").put("content",new org.json.JSONArray().put(new org.json.JSONObject().put("type","output_text").put("text","canonical")))))));
  assertEquals("canonical",stream.text());
 }
 @Test public void debugHtmlSanitize(){
  String source="<script>bad</script> text ```html\n<script>literal</script>\n```";
  System.out.println("=== Input ===");
  System.out.println(source);
  System.out.println();
  
  List<MarkdownRenderer.FencedSource> fences=MarkdownRenderer.extractFencedSources(source);
  System.out.println("=== Extracted Fences (" + fences.size() + ") ===");
  for(int i=0;i<fences.size();i++) {
   MarkdownRenderer.FencedSource f=fences.get(i);
   System.out.println("Fence " + i + ": lang=" + f.language);
   System.out.println("Raw: [" + f.raw + "]");
  }
  System.out.println();
  
  String processed=MarkdownRenderer.processMarkdown(source);
  System.out.println("=== Processed ===");
  System.out.println(processed);
 }
}
