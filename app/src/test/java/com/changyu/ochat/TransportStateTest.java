package com.changyu.ochat;

import org.json.*;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class TransportStateTest {
 static final class Sink implements ChatApiClient.StreamCallback {
  StringBuilder text=new StringBuilder();GenerationResult result;String error="";
  public void onDelta(String s){text.append(s);}public void onSnapshot(String s){text.setLength(0);text.append(s);}
  public void onReasoning(String s){}public void onStatus(String s){}public void onDone(){}public void onError(String s){error=s;}
  public void onFinished(GenerationResult r){result=r;}
 }
 private Sink parse(String protocol,String events)throws Exception{Sink s=new Sink();new ChatApiClient().parseSse(new ByteArrayInputStream(events.getBytes(StandardCharsets.UTF_8)),protocol,s);return s;}
 @Test public void eofDoesNotPretendPartialReplyCompleted()throws Exception{
  Sink sink=parse(ChatApiClient.CHAT_COMPLETIONS,"data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n");
  assertEquals("partial",sink.text.toString());assertEquals("interrupted",sink.result.state);
 }
 @Test public void chatLengthAndTrailingUsageAreKept()throws Exception{
  Sink s=parse(ChatApiClient.CHAT_COMPLETIONS,"data: {\"choices\":[{\"delta\":{\"content\":\"text\"},\"finish_reason\":\"length\"}]}\n\ndata: {\"choices\":[],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":3,\"completion_tokens_details\":{\"reasoning_tokens\":2}}}\n\ndata: [DONE]\n\n");
  assertEquals("truncated",s.result.state);assertEquals(12,s.result.usage.getLong("input"));assertEquals(2,s.result.usage.getLong("reasoning"));
 }
 @Test public void responsesIncompletePreservesSnapshotAndReason()throws Exception{
  Sink s=parse(ChatApiClient.RESPONSES,"data: {\"type\":\"response.output_text.delta\",\"delta\":\"part\"}\n\ndata: {\"type\":\"response.incomplete\",\"response\":{\"incomplete_details\":{\"reason\":\"max_output_tokens\"},\"usage\":{\"input_tokens\":8,\"output_tokens\":4}}}\n\n");
  assertEquals("part",s.text.toString());assertEquals("truncated",s.result.state);assertEquals(4,s.result.usage.getLong("output"));
 }
 @Test public void anthropicNativeEventsAndUsageMerge()throws Exception{
  Sink s=parse(ChatApiClient.ANTHROPIC,"event: message_start\ndata: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":9,\"output_tokens\":1}}}\n\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"hello\"}}\n\ndata: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":5}}\n\ndata: {\"type\":\"message_stop\"}\n\n");
  assertEquals("hello",s.text.toString());assertEquals("complete",s.result.state);assertEquals(9,s.result.usage.getLong("input"));assertEquals(5,s.result.usage.getLong("output"));
 }
 @Test public void geminiNativeExcludesThoughtsAndReportsTruncation()throws Exception{
  Sink s=parse(ChatApiClient.GEMINI,"data: {\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"private\"},{\"text\":\"answer\"}]},\"finishReason\":\"MAX_TOKENS\"}],\"usageMetadata\":{\"promptTokenCount\":10,\"candidatesTokenCount\":6}}\n\n");
  assertEquals("answer",s.text.toString());assertEquals("truncated",s.result.state);assertEquals(10,s.result.usage.getLong("input"));
 }
 @Test public void refusalIsFilteredRatherThanCompletedOrInterrupted()throws Exception{
  Sink chat=parse(ChatApiClient.CHAT_COMPLETIONS,"data: {\"choices\":[{\"delta\":{\"refusal\":\"cannot answer\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n");
  assertEquals("filtered",chat.result.state);assertEquals("cannot answer",chat.text.toString());
  Sink responses=parse(ChatApiClient.RESPONSES,"data: {\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}\n\ndata: {\"type\":\"response.refusal.done\",\"refusal\":\"no\"}\n\n");assertEquals("filtered",responses.result.state);assertEquals("partial",responses.text.toString());
 }
 @Test public void nativeRequestHasCorrectRolesAuthAndAttachments()throws Exception{
  JSONArray h=new JSONArray().put(new JSONObject().put("role","system").put("content","system")).put(new JSONObject().put("role","user").put("content",new JSONArray().put(new JSONObject().put("type","text").put("text","hi")).put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url","data:image/jpeg;base64,YQ==")))));
  JSONObject claude=new ChatApiClient().requestBody("claude-sonnet-4-6",h,.7,4096,.9,"high",ChatApiClient.ANTHROPIC,SearchOptions.OFF);
  assertEquals("system\n",claude.getString("system"));assertEquals("adaptive",claude.getJSONObject("thinking").getString("type"));assertEquals("image",claude.getJSONArray("messages").getJSONObject(0).getJSONArray("content").getJSONObject(1).getString("type"));
  JSONObject gemini=new ChatApiClient().requestBody("gemini-3-pro",h,.7,4096,.9,"low",ChatApiClient.GEMINI,SearchOptions.OFF);
  assertTrue(gemini.has("systemInstruction"));assertTrue(gemini.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(1).has("inlineData"));
  okhttp3.Request.Builder request=new okhttp3.Request.Builder().url("https://example.org");NativeProtocols.auth(request,"secret",ChatApiClient.ANTHROPIC);
  assertEquals("secret",request.build().header("x-api-key"));assertNull(request.build().header("Authorization"));
 }
 @Test public void samplingRespectsModelCapabilityAndConfiguredOverride()throws Exception{
  JSONArray h=new JSONArray().put(new JSONObject().put("role","user").put("content","hi"));
  JSONObject b=new ChatApiClient().requestBody("gpt-6.1-sol",h,.7,100,.9,"medium",ChatApiClient.RESPONSES,SearchOptions.OFF);
  assertFalse(b.has("temperature"));assertFalse(b.has("top_p"));
  b=new ChatApiClient().requestBody("gpt-4o-mini",h,.3,100,.8,"default",ChatApiClient.RESPONSES,SearchOptions.OFF);
  assertEquals(.3,b.getDouble("temperature"),0);assertEquals(.8,b.getDouble("top_p"),0);
  ModelCapabilities caps=new ModelCapabilities("unknown",ChatApiClient.CHAT_COMPLETIONS,new JSONObject());assertArrayEquals(new String[]{"default"},caps.efforts);
  assertFalse(new ModelCapabilities("gpt-6.1-sol",ChatApiClient.RESPONSES,new JSONObject()).supports("none"));
  assertTrue(new ModelCapabilities("custom",ChatApiClient.CHAT_COMPLETIONS,new JSONObject().put("reasoningLevels","low,high")).supports("high"));
 }
}
