package com.changyu.ochat;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BuiltinToolsTest {
 private SearchOptions options(String protocol,boolean web,boolean url,boolean code){return new SearchOptions(web,SearchOptions.BUILTIN,protocol,"fixture","",5,"basic",url,code);}
 @Test public void eachProtocolUsesItsOwnToolSchema()throws Exception{
  JSONObject response=BuiltinTools.apply(new JSONObject(),ChatApiClient.RESPONSES,options(ChatApiClient.RESPONSES,true,true,true));
  assertEquals("web_search",response.getJSONArray("tools").getJSONObject(0).getString("type"));assertEquals("code_interpreter",response.getJSONArray("tools").getJSONObject(1).getString("type"));assertEquals("auto",response.getJSONArray("tools").getJSONObject(1).getJSONObject("container").getString("type"));
  JSONObject claude=BuiltinTools.apply(new JSONObject(),ChatApiClient.ANTHROPIC,options(ChatApiClient.ANTHROPIC,true,true,true));
  assertEquals("web_search_20250305",claude.getJSONArray("tools").getJSONObject(0).getString("type"));assertEquals("web_fetch",claude.getJSONArray("tools").getJSONObject(1).getString("name"));assertEquals("code_execution_20260521",claude.getJSONArray("tools").getJSONObject(2).getString("type"));
  JSONObject gemini=BuiltinTools.apply(new JSONObject(),ChatApiClient.GEMINI,options(ChatApiClient.GEMINI,true,true,true));JSONArray tools=gemini.getJSONArray("tools");
  assertEquals(3,tools.length());assertTrue(tools.getJSONObject(0).has("googleSearch"));assertTrue(tools.getJSONObject(1).has("urlContext"));assertTrue(tools.getJSONObject(2).has("codeExecution"));assertFalse(gemini.has("web_search_options"));
  JSONObject chat=BuiltinTools.apply(new JSONObject(),ChatApiClient.CHAT_COMPLETIONS,options(ChatApiClient.CHAT_COMPLETIONS,true,true,true));assertTrue(chat.has("web_search_options"));assertFalse(chat.has("tools"));
 }
 @Test public void searchSwitchAndHostedCodeSwitchAreIndependent()throws Exception{
  JSONObject body=BuiltinTools.apply(new JSONObject(),ChatApiClient.GEMINI,options(ChatApiClient.GEMINI,false,false,true));
  assertEquals(1,body.getJSONArray("tools").length());assertTrue(body.getJSONArray("tools").getJSONObject(0).has("codeExecution"));assertFalse(body.toString().contains("googleSearch"));
  assertFalse(BuiltinTools.apply(new JSONObject(),ChatApiClient.RESPONSES,SearchOptions.OFF).has("tools"));
 }
 @Test public void automaticRoutesNativeProtocolsAndSearchModelsWithoutForcingOrdinaryChat(){
  for(String protocol:new String[]{ChatApiClient.RESPONSES,ChatApiClient.GEMINI,ChatApiClient.ANTHROPIC}){SearchOptions o=new SearchOptions(true,SearchOptions.AUTO,protocol,"fixture","",5,"basic",false,false);assertTrue(o.builtin);assertFalse(o.tavily);}
  assertTrue(new SearchOptions(true,SearchOptions.AUTO,ChatApiClient.CHAT_COMPLETIONS,"gpt-5-search-api","",5,"basic",false,false).builtin);
  assertTrue(new SearchOptions(true,SearchOptions.AUTO,ChatApiClient.CHAT_COMPLETIONS,"gpt-6.1-sol","",5,"basic",false,false).tavily);
  assertTrue(new SearchOptions(true,SearchOptions.RESPONSES,ChatApiClient.ANTHROPIC,"",5,"basic").builtin);
 }
 @Test public void officialDeepSeekCapabilitiesDoNotLeakToThirdPartyGateways(){
  SearchOptions flash=new SearchOptions(true,SearchOptions.AUTO,ChatApiClient.RESPONSES,"deepseek-flash","",5,"basic",true,true,"https://api.deepseek.com");
  assertTrue(flash.tavily);assertFalse(flash.builtin);assertFalse(flash.codeExecution);assertFalse(flash.urlContext);
  SearchOptions manual=new SearchOptions(true,SearchOptions.BUILTIN,ChatApiClient.RESPONSES,"deepseek-flash","",5,"basic",false,false,"https://api.deepseek.com");assertFalse(manual.error.isEmpty());
  for(String protocol:new String[]{ChatApiClient.RESPONSES,ChatApiClient.ANTHROPIC})assertTrue(new SearchOptions(true,SearchOptions.AUTO,protocol,"deepseek-v4-pro","",5,"basic",false,false,"https://api.deepseek.com/anthropic").builtin);
  assertTrue(new SearchOptions(true,SearchOptions.AUTO,ChatApiClient.RESPONSES,"deepseek-flash","",5,"basic",false,false,"https://proxy.example.org").builtin);
 }
 @Test public void disablingExternalSearchPreservesIndependentlyEnabledTools()throws Exception{
  SearchOptions before=new SearchOptions(true,SearchOptions.TAVILY,ChatApiClient.GEMINI,"fixture","",5,"basic",true,true);
  SearchOptions after=before.withoutSearch();assertFalse(after.builtin);assertFalse(after.tavily);assertTrue(after.urlContext);assertTrue(after.codeExecution);
  JSONObject body=BuiltinTools.apply(new JSONObject(),ChatApiClient.GEMINI,after);assertEquals(2,body.getJSONArray("tools").length());assertFalse(body.toString().contains("googleSearch"));assertTrue(before.tavily);
 }
 @Test public void requestBuildersKeepToolsOutOfTopicAndUnrelatedRequests()throws Exception{
  ChatApiClient client=new ChatApiClient();JSONArray history=new JSONArray().put(new JSONObject().put("role","user").put("content","hello"));
  for(String protocol:new String[]{ChatApiClient.RESPONSES,ChatApiClient.ANTHROPIC,ChatApiClient.GEMINI,ChatApiClient.CHAT_COMPLETIONS}){
   JSONObject plain=client.requestBody("fixture",history,.7,512,1,"default",protocol,SearchOptions.OFF);assertFalse(plain.has("tools"));assertFalse(plain.has("web_search_options"));
   JSONObject hosted=client.requestBody("fixture",history,.7,512,1,"default",protocol,options(protocol,true,false,false));assertTrue(protocol,hosted.has("tools")||hosted.has("web_search_options"));
  }
 }
}
