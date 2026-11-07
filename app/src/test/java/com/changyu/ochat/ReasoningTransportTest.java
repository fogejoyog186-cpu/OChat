package com.changyu.ochat;

import org.json.*;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** Real protocol fixtures, without external requests or model credentials. */
public class ReasoningTransportTest {
 static final class Sink implements ChatApiClient.StreamCallback {
  final StringBuilder answer=new StringBuilder(),thought=new StringBuilder();
  GenerationResult result;String error="";
  public void onDelta(String s){answer.append(s);}
  public void onSnapshot(String s){answer.setLength(0);answer.append(s);}
  public void onReasoning(String s){thought.append(s);}
  public void onReasoningSnapshot(String s){thought.setLength(0);thought.append(s);}
  public void onStatus(String s){}public void onDone(){}
  public void onError(String s){error=s;}
  public void onFinished(GenerationResult value){result=value;}
 }
 private String event(String json){return "data: "+json+"\n\n";}
 private Sink parse(String protocol,String events)throws Exception{
  Sink s=new Sink();new ChatApiClient().parseSse(new ByteArrayInputStream(events.getBytes(StandardCharsets.UTF_8)),protocol,s);return s;
 }
 @Test public void chatReasoningAliasesKeepAnswerSeparate()throws Exception{
  Sink s=parse(ChatApiClient.CHAT_COMPLETIONS,
   event("{\"choices\":[{\"delta\":{\"reasoning_content\":\"first \"}}]}")+
   event("{\"choices\":[{\"delta\":{\"reasoning\":\"second \"}}]}")+
   event("{\"choices\":[{\"delta\":{\"reasoning_details\":[{\"type\":\"reasoning.text\",\"text\":\"third\"},{\"type\":\"reasoning.encrypted\",\"data\":\"opaque\"}]}}]}")+
   event("{\"choices\":[{\"delta\":{\"content\":\"answer\"},\"finish_reason\":\"stop\"}]}")+event("[DONE]"));
  assertEquals("first second third",s.thought.toString());assertEquals("answer",s.answer.toString());assertEquals("complete",s.result.state);
 }
 @Test public void leadingThinkTagsSurviveEverySplitPosition()throws Exception{
  String original=" \n<ThInK>先比较两个方案\n再验证边界</ThInK>\n最终答案";
  for(int split=1;split<original.length();split++){
   StringBuilder answer=new StringBuilder(),thought=new StringBuilder();ReasoningText.Tags tags=new ReasoningText.Tags(answer::append,thought::append);
   tags.append(original.substring(0,split));tags.append(original.substring(split));tags.finish();
   assertEquals("split "+split,"先比较两个方案\n再验证边界",thought.toString());assertEquals("\n最终答案",answer.toString());
  }
  StringBuilder answer=new StringBuilder(),thought=new StringBuilder();ReasoningText.Tags tags=new ReasoningText.Tags(answer::append,thought::append);
  for(int i=0;i<original.length();i++)tags.append(original.substring(i,i+1));tags.finish();
  assertEquals("先比较两个方案\n再验证边界",thought.toString());assertEquals("\n最终答案",answer.toString());
 }
 @Test public void codeExamplesStayLiteralAndIncompleteThinkingIsNotAnAnswer(){
  String source="Example:\n```xml\n<think>literal</think>\n```";
  StringBuilder answer=new StringBuilder(),thought=new StringBuilder();ReasoningText.Tags tags=new ReasoningText.Tags(answer::append,thought::append);
  for(int i=0;i<source.length();i++)tags.append(source.substring(i,i+1));tags.finish();assertEquals(source,answer.toString());assertEquals("",thought.toString());
  answer.setLength(0);thought.setLength(0);tags=new ReasoningText.Tags(answer::append,thought::append);tags.append("<think>still working <");tags.finish();
  assertEquals("",answer.toString());assertEquals("still working <",thought.toString());
 }
 @Test public void structuredTextNeverIncludesSignaturesOrEncryptedPayloads()throws Exception{
  assertEquals("summary",ReasoningText.delta(new JSONObject("{\"reasoning_details\":[{\"type\":\"reasoning.summary\",\"summary\":\"summary\"},{\"type\":\"reasoning.encrypted\",\"text\":\"secret\"}]}")));
  assertEquals("",ReasoningText.read(new JSONObject("{\"type\":\"signature_delta\",\"text\":\"signature\"}")));
  assertEquals("",ReasoningText.read(new JSONObject("{\"type\":\"redacted_thinking\",\"data\":\"opaque\"}")));
 }
 @Test public void responsesCanonicalSummaryReplacesDeltasAndFinalSnapshot()throws Exception{
  Sink s=parse(ChatApiClient.RESPONSES,
   event("{\"type\":\"response.reasoning_summary_text.delta\",\"output_index\":0,\"summary_index\":0,\"sequence_number\":1,\"delta\":\"draft\"}")+
   event("{\"type\":\"response.reasoning_summary_text.delta\",\"output_index\":0,\"summary_index\":0,\"sequence_number\":1,\"delta\":\"draft\"}")+
   event("{\"type\":\"response.reasoning_summary_text.done\",\"output_index\":0,\"summary_index\":0,\"text\":\"Correct summary\"}")+
   event("{\"type\":\"response.reasoning_summary_part.done\",\"output_index\":0,\"summary_index\":1,\"part\":{\"type\":\"summary_text\",\"text\":\"Second paragraph\"}}")+
   event("{\"type\":\"response.output_text.delta\",\"output_index\":1,\"delta\":\"Answer\"}")+
   event("{\"type\":\"response.completed\",\"response\":{\"output\":[{\"type\":\"reasoning\",\"encrypted_content\":\"secret\",\"summary\":[{\"type\":\"summary_text\",\"text\":\"Correct summary\"},{\"type\":\"summary_text\",\"text\":\"Second paragraph\"}]},{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Answer\"}]}]}}"));
  assertEquals("Correct summary\n\nSecond paragraph",s.thought.toString());assertEquals("Answer",s.answer.toString());assertEquals("complete",s.result.state);
 }
 @Test public void finalOnlyResponsesSummaryAndThinkingOnlyTruncationAreRetained()throws Exception{
  Sink finalOnly=parse(ChatApiClient.RESPONSES,event("{\"type\":\"response.completed\",\"response\":{\"output\":[{\"type\":\"reasoning\",\"summary\":[{\"type\":\"summary_text\",\"text\":\"Summary\"}]},{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Done\"}]}]}}"));
  assertEquals("Summary",finalOnly.thought.toString());assertEquals("Done",finalOnly.answer.toString());
  Sink truncated=parse(ChatApiClient.RESPONSES,event("{\"type\":\"response.incomplete\",\"response\":{\"incomplete_details\":{\"reason\":\"max_output_tokens\"},\"output\":[{\"type\":\"reasoning\",\"summary\":[{\"type\":\"summary_text\",\"text\":\"Partial summary\"}]}]}}"));
  assertEquals("Partial summary",truncated.thought.toString());assertEquals("",truncated.answer.toString());assertEquals("truncated",truncated.result.state);
 }
 @Test public void claudeInitialThinkingAndDeltasExcludeSignature()throws Exception{
  Sink s=parse(ChatApiClient.ANTHROPIC,
   event("{\"type\":\"content_block_start\",\"content_block\":{\"type\":\"thinking\",\"thinking\":\"Initial \"}}")+
   event("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"thought\"}}")+
   event("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"signature_delta\",\"signature\":\"secret\"}}")+
   event("{\"type\":\"content_block_start\",\"content_block\":{\"type\":\"redacted_thinking\",\"data\":\"opaque\"}}")+
   event("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Answer\"}}")+
   event("{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}")+event("{\"type\":\"message_stop\"}"));
  assertEquals("Initial thought",s.thought.toString());assertEquals("Answer",s.answer.toString());assertEquals("complete",s.result.state);
 }
 @Test public void geminiThoughtSummaryIsSeparateFromAnswer()throws Exception{
  Sink s=parse(ChatApiClient.GEMINI,event("{\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"Summary\"},{\"thoughtSignature\":\"opaque\"},{\"text\":\"Answer\"}]},\"finishReason\":\"STOP\"}]}"));
  assertEquals("Summary",s.thought.toString());assertEquals("Answer",s.answer.toString());assertEquals("complete",s.result.state);
 }
 @Test public void autoRequestsSummariesWithoutForcingEffortAndOffOmitsThem()throws Exception{
  ChatApiClient api=new ChatApiClient();JSONArray h=new JSONArray().put(new JSONObject().put("role","user").put("content","hi"));
  JSONObject gpt=api.requestBody("gpt-6.1-sol",h,.7,4096,.9,"default",ChatApiClient.RESPONSES,SearchOptions.OFF).getJSONObject("reasoning");
  assertEquals("auto",gpt.getString("summary"));assertFalse(gpt.has("effort"));
  JSONObject off=api.requestBody("gpt-5.1",h,.7,4096,.9,"none",ChatApiClient.RESPONSES,SearchOptions.OFF).getJSONObject("reasoning");
  assertEquals("none",off.getString("effort"));assertFalse(off.has("summary"));
  assertFalse(api.requestBody("gpt-4o-mini",h,.7,4096,.9,"default",ChatApiClient.RESPONSES,SearchOptions.OFF).has("reasoning"));
  assertFalse(api.requestBody("o1",h,.7,4096,.9,"default",ChatApiClient.RESPONSES,SearchOptions.OFF).has("reasoning"));
  JSONObject gemini=api.requestBody("gemini-2.5-flash",h,.7,4096,.9,"default",ChatApiClient.GEMINI,SearchOptions.OFF).getJSONObject("generationConfig").getJSONObject("thinkingConfig");
  assertTrue(gemini.getBoolean("includeThoughts"));assertFalse(gemini.has("thinkingBudget"));
  off=api.requestBody("gemini-2.5-flash",h,.7,4096,.9,"none",ChatApiClient.GEMINI,SearchOptions.OFF).getJSONObject("generationConfig").getJSONObject("thinkingConfig");
  assertFalse(off.optBoolean("includeThoughts"));assertEquals(0,off.getInt("thinkingBudget"));
 }
}
