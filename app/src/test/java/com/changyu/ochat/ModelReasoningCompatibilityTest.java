package com.changyu.ochat;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Published model-specific controls and their actual protocol fields. No paid requests. */
public class ModelReasoningCompatibilityTest {
 private ModelCapabilities caps(String id){return new ModelCapabilities(id,ChatApiClient.CHAT_COMPLETIONS,new JSONObject());}
 private void levels(String id,String... expected){assertArrayEquals(id,expected,caps(id).efforts);}
 private JSONArray history()throws Exception{return new JSONArray().put(new JSONObject().put("role","user").put("content","hi"));}
 private JSONObject request(String id,String effort,String protocol)throws Exception{return new ChatApiClient().requestBody(id,history(),.7,4096,.9,effort,protocol,SearchOptions.OFF);}
 @Test public void namespacedIdsRecognizeAllRequestedFamilies(){
  assertEquals("gpt",caps("openai/GPT-6.1-Sol").family);assertEquals("claude",caps("anthropic/claude-sonnet-4.6").family);
  assertEquals("deepseek",caps("deepseek-ai/DeepSeek-V4-Pro").family);assertEquals("gemini",caps("models/gemini-3.8-flash").family);
  assertEquals("grok",caps("x-ai/grok-4.6:online").family);assertEquals("grok",caps("gork-4.6").family);
  assertEquals("kimi",caps("moonshotai/Kimi-K3.1-Pro").family);assertEquals("glm",caps("z-ai/GLM-5.3").family);
 }
 @Test public void gptFamiliesHaveDifferentEffortsAndNoInventedUltra(){
  levels("gpt-6.1-sol","default","low","medium","high","xhigh","max");levels("gpt-6-astra","default","low","medium","high","xhigh","max");
  levels("gpt-6-sol","default","none","low","medium","high","xhigh","max");
  levels("gpt-5.6","default","none","low","medium","high","xhigh","max");
  levels("gpt-5.1","default","none","low","medium","high");levels("gpt-5","default","minimal","low","medium","high");
  levels("o3-mini","default","low","medium","high");levels("gpt-4o-mini","default");levels("o1-preview","default");
 }
 @Test public void claudeVersionsUsePublishedAdaptiveOrBudgetControls()throws Exception{
  levels("claude-3-opus-20240229","default");levels("claude-3-sonnet-20240229","default");levels("claude-3-5-sonnet-20241022","default");
  levels("claude-3-7-sonnet-20250219","default","none","enabled");levels("claude-sonnet-4-20250514","default","none","enabled");
  assertFalse(caps("claude-5-5-opus").supports("none"));
  levels("claude-opus-5-5","default","low","medium","high","xhigh","max");
  levels("claude-sonnet-4-6","default","none","low","medium","high","max");levels("claude-haiku-4-5","default","none","enabled");
  JSONObject adaptive=request("claude-opus-4-7","xhigh",ChatApiClient.ANTHROPIC);assertEquals("adaptive",adaptive.getJSONObject("thinking").getString("type"));assertEquals("xhigh",adaptive.getJSONObject("output_config").getString("effort"));
  JSONObject budget=request("claude-3-7-sonnet-latest","enabled",ChatApiClient.ANTHROPIC);assertEquals("enabled",budget.getJSONObject("thinking").getString("type"));assertEquals(2048,budget.getJSONObject("thinking").getInt("budget_tokens"));assertFalse(budget.has("output_config"));
  JSONObject off=request("claude-haiku-4-5","none",ChatApiClient.ANTHROPIC);assertEquals("disabled",off.getJSONObject("thinking").getString("type"));
 }
 @Test public void deepseekAndGlmUseThinkingSwitchWithoutOpenaiTokenField()throws Exception{
  levels("deepseek-v4-pro","default","none","low","high","max");levels("deepseek-v3.2","default","none","enabled");
  JSONObject d=request("deepseek-v4-pro","high",ChatApiClient.CHAT_COMPLETIONS);assertEquals("enabled",d.getJSONObject("thinking").getString("type"));assertEquals("high",d.getString("reasoning_effort"));assertTrue(d.has("max_tokens"));assertFalse(d.has("max_completion_tokens"));
  JSONObject off=request("deepseek-v4-pro","none",ChatApiClient.CHAT_COMPLETIONS);assertEquals("disabled",off.getJSONObject("thinking").getString("type"));assertFalse(off.has("reasoning_effort"));
  JSONObject legacy=request("deepseek-v3.2","enabled",ChatApiClient.CHAT_COMPLETIONS);assertFalse(legacy.has("reasoning_effort"));
  levels("glm-5.3","default","low","high","max");levels("glm-5.2","default","none","high","max");levels("glm-4.7","default","none","enabled");
  JSONObject g=request("glm-5.3","low",ChatApiClient.CHAT_COMPLETIONS);assertEquals("low",g.getString("reasoning_effort"));assertEquals("enabled",g.getJSONObject("thinking").getString("type"));assertTrue(g.has("max_tokens"));
 }
 @Test public void geminiVersionsUseLevelsOrLegacyBudgets()throws Exception{
  levels("gemini-3.8-flash","default","low","medium","high");levels("gemini-3-flash-preview","default","minimal","low","medium","high");
  levels("gemini-3-pro-preview","default","low","high");levels("gemini-3.1-pro-preview","default","low","medium","high");
  levels("gemini-2.5-pro","default","low","medium","high");levels("gemini-2.5-flash","default","none","low","medium","high");
  JSONObject level=request("gemini-3.8-flash","medium",ChatApiClient.GEMINI);assertEquals("medium",level.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"));
  JSONObject budget=request("gemini-2.5-flash","high",ChatApiClient.GEMINI);assertEquals(24576,budget.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getInt("thinkingBudget"));
  JSONObject off=request("gemini-2.5-flash","none",ChatApiClient.GEMINI);assertEquals(0,off.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getInt("thinkingBudget"));
 }
 @Test public void kimiAndGrokDoNotShareUnsupportedLevels()throws Exception{
  levels("kimi-k3.1-pro","default","low","high","max");levels("kimi-k2.6","default","none","enabled");levels("kimi-k2.7-code","default");levels("kimi-k2-thinking","default");
  JSONObject kimi=request("kimi-k3","max",ChatApiClient.CHAT_COMPLETIONS);assertEquals("max",kimi.getString("reasoning_effort"));assertFalse(kimi.has("thinking"));assertFalse(kimi.has("temperature"));assertFalse(kimi.has("top_p"));assertTrue(kimi.has("max_tokens"));
  JSONObject toggle=request("kimi-k2.6","none",ChatApiClient.CHAT_COMPLETIONS);assertEquals("disabled",toggle.getJSONObject("thinking").getString("type"));assertFalse(toggle.has("reasoning_effort"));
  levels("grok-3-mini","default","low","high");levels("grok-4.3","default","none","low","medium","high");levels("grok-4.5","default","low","medium","high");levels("grok-4.6","default","low","medium","high","xhigh");
 }
 @Test public void defaultAndUnsupportedValuesProduceNoManualReasoningFields()throws Exception{
  for(String id:new String[]{"gpt-6.1-sol","claude-opus-4-7","deepseek-v4-pro","gemini-3.8-flash","grok-4.6","kimi-k3","glm-5.3","unknown"}){
   JSONObject auto=request(id,"default",ChatApiClient.CHAT_COMPLETIONS);assertFalse(id,auto.has("thinking"));assertFalse(id,auto.has("reasoning_effort"));
   JSONObject stale=request(id,"ultra",ChatApiClient.CHAT_COMPLETIONS);assertFalse(id,stale.has("thinking"));assertFalse(id,stale.has("reasoning_effort"));
  }
  JSONObject normalized=request("gpt-6.1-sol","none",ChatApiClient.RESPONSES).getJSONObject("reasoning");
  assertFalse(normalized.has("effort"));assertEquals("auto",normalized.getString("summary"));
 }
 @Test public void oldAutoGeneratedOverridesAreIgnoredButExplicitGatewayOverrideRemains(){
  JSONObject stale=new JSONObject();try{stale.put("reasoningLevels","none,ultra").put("supportsSampling",true);}catch(Exception e){throw new AssertionError(e);}
  ModelCapabilities detected=new ModelCapabilities("gpt-6.1-sol",ChatApiClient.RESPONSES,stale);assertFalse(detected.supports("none"));assertFalse(detected.supports("ultra"));assertFalse(detected.sampling);
  try{stale.put("capabilityMode","custom");}catch(Exception e){throw new AssertionError(e);}
  assertTrue(new ModelCapabilities("gateway-model",ChatApiClient.CHAT_COMPLETIONS,stale).supports("ultra"));
 }
 @Test public void preservedKimiReasoningIsReplayedOnlyInItsOwnProtocol()throws Exception{
  JSONArray history=new JSONArray().put(new JSONObject().put("role","user").put("content","q").put("reasoning_content","not a user field"))
   .put(new JSONObject().put("role","assistant").put("content","a").put("reasoning_content","provider reasoning"));
  JSONObject kimi=new ChatApiClient().requestBody("kimi-k3",history,.7,4096,.9,"default",ChatApiClient.CHAT_COMPLETIONS,SearchOptions.OFF);
  assertFalse(kimi.getJSONArray("messages").getJSONObject(0).has("reasoning_content"));assertEquals("provider reasoning",kimi.getJSONArray("messages").getJSONObject(1).getString("reasoning_content"));
  JSONObject gpt=new ChatApiClient().requestBody("gpt-6.1-sol",history,.7,4096,.9,"default",ChatApiClient.CHAT_COMPLETIONS,SearchOptions.OFF);assertFalse(gpt.getJSONArray("messages").getJSONObject(1).has("reasoning_content"));
 }
}
