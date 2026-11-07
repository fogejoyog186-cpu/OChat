package com.changyu.ochat;
import org.json.*;import org.junit.Test;import static org.junit.Assert.*;
public class ContextBudgetTest {
 private JSONObject message(String role,String text)throws Exception{return new JSONObject().put("role",role).put("content",text);}
 @Test public void pruningKeepsSystemLatestTurnAndOriginalHistory()throws Exception{
  JSONArray h=new JSONArray().put(message("system","system"));for(int i=0;i<20;i++){h.put(message("user","user "+i+" "+new String(new char[150]).replace('\0','中')));h.put(message("assistant","answer "+i));}h.put(message("user","latest"));
  ContextBudget.Result r=ContextBudget.trim(h,1000,200);assertTrue(r.omitted>0);assertEquals("system",r.messages.getJSONObject(0).getString("role"));assertEquals("user",r.messages.getJSONObject(1).getString("role"));assertEquals("latest",r.messages.getJSONObject(r.messages.length()-1).getString("content"));assertEquals(42,h.length());assertTrue(r.estimatedTokens<800);
 }
 @Test public void searchDataStaysWithLatestUserAfterEarlierPruning()throws Exception{
  JSONArray h=new JSONArray().put(message("system","system"));for(int i=0;i<10;i++){h.put(message("user",new String(new char[150]).replace('\0','中')));h.put(message("assistant","old answer"));}
  h.put(message("user","latest"));ContextBudget.Result pruned=ContextBudget.trim(h,1000,200);JSONArray sources=new JSONArray().put(new JSONObject().put("url","https://example.org").put("snippet","reference fact"));String data=SearchGrounding.data(sources,SearchGrounding.characterBudget(pruned.messages,2400,200));JSONArray grounded=SearchGrounding.messages(pruned.messages,data);ContextBudget.Result result=ContextBudget.trim(grounded,2400,200);assertTrue(pruned.omitted>0);assertEquals(0,result.omitted);assertFalse(result.messages.getJSONObject(0).getString("content").contains("reference fact"));assertTrue(result.messages.toString().contains("latest"));assertTrue(result.messages.toString().contains("reference fact"));
 }
 @Test public void oversizedSingleTurnIsRejectedWithoutSilentlyTruncating()throws Exception{
  JSONArray h=new JSONArray().put(message("user",new String(new char[3000]).replace('\0','中')));
  try{ContextBudget.trim(h,1000,200);fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("预算"));}
 }
 @Test public void requestedMillionTokenOutputIsBoundedByRemainingContext()throws Exception{
  JSONArray history=new JSONArray().put(message("system","system")).put(message("user","hello"));
  ContextBudget.Result result=ContextBudget.trim(history,ContextBudget.DEFAULT_CONTEXT,ContextBudget.DEFAULT_OUTPUT);
  assertEquals(128000,ContextBudget.DEFAULT_CONTEXT);assertEquals(1000000,ContextBudget.DEFAULT_OUTPUT);assertEquals(2,result.messages.length());assertTrue(result.outputTokens>100000);assertTrue(result.outputTokens+result.estimatedTokens<=121600);assertEquals(0,result.omitted);
 }
 @Test public void latestLargeTurnCanReduceOutputWithoutLosingTheInput()throws Exception{
  String input=new String(new char[1700]).replace('\0','a');JSONArray history=new JSONArray().put(message("user",input));
  ContextBudget.Result result=ContextBudget.trim(history,1000,1000000);assertEquals(input,result.messages.getJSONObject(0).optString("content"));assertTrue(result.outputTokens>0);assertTrue(result.outputTokens<200);
  assertEquals(32,ContextBudget.trim(new JSONArray().put(message("user","hi")),1000,32).outputTokens);
 }
 @Test public void utf8ByteAccountingDoesNotDoubleCountAttachmentText()throws Exception{assertEquals(6,AttachmentBatch.estimatedBytes("中文"));assertEquals(3,AttachmentBatch.estimatedBytes("abc"));assertEquals(3,AttachmentBatch.estimatedBytes("data:image/jpeg;base64,YWJj"));}
 @Test public void binaryOfficeIsNeverDecodedAsUtf8Text()throws Exception{try{AttachmentBatch.extractDocumentText(new byte[]{0,1,2},"application/msword","old.doc");fail();}catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("二进制"));}}
}
