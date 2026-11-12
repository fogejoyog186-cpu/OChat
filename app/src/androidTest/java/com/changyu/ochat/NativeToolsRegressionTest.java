package com.changyu.ochat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NativeToolsRegressionTest {
 private static final class Output implements ChatApiClient.StreamCallback {
  String text="",status="",error="";int done;SearchSources sources=new SearchSources();JSONArray events=new JSONArray();
  public void onDelta(String value){text+=value;}public void onSnapshot(String value){text=value;}public void onReasoning(String value){}public void onStatus(String value){status=value;}
  public void onDone(){done++;}public void onError(String value){error=value;}public void onSearchSources(SearchSources value,String state){sources=SearchSources.parse(value.items);status=state;}public void onToolEvent(JSONObject value){events.put(value);}
 }
 private String event(JSONObject value){return "data: "+value+"\n\n";}
 private JSONObject start(int index,JSONObject block)throws Exception{return new JSONObject().put("type","content_block_start").put("index",index).put("content_block",block);}
 private JSONObject delta(int index,JSONObject value)throws Exception{return new JSONObject().put("type","content_block_delta").put("index",index).put("delta",value);}
 private JSONObject stop(int index)throws Exception{return new JSONObject().put("type","content_block_stop").put("index",index);}
 private JSONObject end(String reason)throws Exception{return new JSONObject().put("type","message_delta").put("delta",new JSONObject().put("stop_reason",reason)).put("usage",new JSONObject().put("output_tokens",7));}
 private JSONObject parse(String stream,String protocol,Output output)throws Exception{return new ChatApiClient().parseSse(new ByteArrayInputStream(stream.getBytes(StandardCharsets.UTF_8)),protocol,output);}
 @Test public void anthropicSearchResultsAndCitationDeltasAreCollectedWithoutUnsafeLinks()throws Exception{
  String stream=event(start(0,new JSONObject().put("type","server_tool_use").put("id","srv-1").put("name","web_search").put("input",new JSONObject())))
   +event(delta(0,new JSONObject().put("type","input_json_delta").put("partial_json","{\"query\":\"fixture search\"}")))+event(stop(0))
   +event(start(1,new JSONObject().put("type","web_search_tool_result").put("tool_use_id","srv-1").put("content",new JSONArray().put(new JSONObject().put("type","web_search_result").put("url","https://example.org/a").put("title","Result").put("encrypted_content","DO_NOT_PERSIST")).put(new JSONObject().put("type","web_search_result").put("url","javascript:bad")))))
   +event(stop(1))+event(start(2,new JSONObject().put("type","text").put("text","")))
   +event(delta(2,new JSONObject().put("type","text_delta").put("text","answer")))
   +event(delta(2,new JSONObject().put("type","citations_delta").put("citation",new JSONObject().put("type","web_search_result_location").put("url","https://example.org/b").put("title","Citation"))))
   +event(stop(2))+event(end("end_turn"))+event(new JSONObject().put("type","message_stop"));
  Output output=new Output();assertNull(parse(stream,ChatApiClient.ANTHROPIC,output));assertEquals("answer",output.text);assertEquals("",output.error);assertEquals(1,output.done);assertEquals(2,output.sources.items.length());assertEquals("内置搜索完成",output.status);assertTrue(output.events.length()>=2);assertFalse(output.events.toString().contains("DO_NOT_PERSIST"));
 }
 @Test public void pausedClaudeBlocksPreservePartialInputsThinkingAndContainer()throws Exception{
  String stream=event(new JSONObject().put("type","message_start").put("message",new JSONObject().put("container",new JSONObject().put("id","container-fixture"))))
   +event(start(0,new JSONObject().put("type","thinking").put("thinking","")))+event(delta(0,new JSONObject().put("thinking","thought").put("type","thinking_delta")))+event(delta(0,new JSONObject().put("signature","signed").put("type","signature_delta")))+event(stop(0))
   +event(start(1,new JSONObject().put("type","server_tool_use").put("id","srv-2").put("name","web_search").put("input",new JSONObject())))
   +event(delta(1,new JSONObject().put("partial_json","{\"query\":").put("type","input_json_delta")))+event(delta(1,new JSONObject().put("partial_json","\"fixture\"}").put("type","input_json_delta")))+event(stop(1))+event(end("pause_turn"))+event(new JSONObject().put("type","message_stop"));
  Output output=new Output();JSONObject pause=parse(stream,ChatApiClient.ANTHROPIC,output);assertNotNull(pause);assertEquals(0,output.done);assertEquals("container-fixture",pause.getString("container"));
  assertEquals("signed",pause.getJSONArray("content").getJSONObject(0).getString("signature"));assertEquals("thought",pause.getJSONArray("content").getJSONObject(0).getString("thinking"));assertEquals("fixture",pause.getJSONArray("content").getJSONObject(1).getJSONObject("input").getString("query"));
 }
 private JSONObject gemini(String text,boolean finish,boolean code,boolean result,boolean sources)throws Exception{
  JSONArray parts=new JSONArray();if(!text.isEmpty())parts.put(new JSONObject().put("text",text));if(code)parts.put(new JSONObject().put("executableCode",new JSONObject().put("language","PYTHON").put("code","print(6 * 7)")));if(result)parts.put(new JSONObject().put("codeExecutionResult",new JSONObject().put("outcome","OUTCOME_OK").put("output","42")));
  JSONObject candidate=new JSONObject().put("content",new JSONObject().put("parts",parts));if(finish)candidate.put("finishReason","STOP");
  if(sources)candidate.put("groundingMetadata",new JSONObject().put("webSearchQueries",new JSONArray().put("fixture")).put("groundingChunks",new JSONArray().put(new JSONObject().put("web",new JSONObject().put("uri","https://example.org/g").put("title","Google source"))).put(new JSONObject().put("web",new JSONObject().put("uri","javascript:bad")))).put("searchEntryPoint",new JSONObject().put("renderedContent","<div>Google suggestions</div>")));
  return new JSONObject().put("candidates",new JSONArray().put(candidate));
 }
 @Test public void geminiStreamsTextCodeResultsAndGroundingWithSuggestions()throws Exception{
  Output output=new Output();String stream=event(gemini("Before ",false,true,false,false))+event(gemini("after",false,false,true,true))+event(gemini("",true,false,true,true));
  assertNull(parse(stream,ChatApiClient.GEMINI,output));assertEquals("Before after",output.text);assertEquals(1,output.done);assertEquals(1,output.sources.items.length());assertEquals(3,output.events.length());assertEquals("<div>Google suggestions</div>",output.sources.items.getJSONObject(0).getString("searchSuggestionsHtml"));
 }
 @Test public void codeOnlyGeminiHasUsefulOutputInsteadOfAnEmptyReply()throws Exception{
  Output output=new Output();parse(event(gemini("",true,true,true,false)),ChatApiClient.GEMINI,output);assertEquals("42",output.text);assertEquals("",output.error);assertEquals(1,output.done);
 }
 @Test public void chatSearchAnnotationsAreSourcesButOrdinaryAnswerLinksAreNot()throws Exception{
  JSONObject citation=new JSONObject().put("type","url_citation").put("url_citation",new JSONObject().put("url","https://example.org/chat").put("title","Chat source"));
  JSONObject part=new JSONObject().put("content","answer https://invented.example/").put("annotations",new JSONArray().put(citation));
  JSONObject root=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("delta",part).put("finish_reason","stop")));
  Output output=new Output();parse(event(root),ChatApiClient.CHAT_COMPLETIONS,output);assertEquals(1,output.sources.items.length());assertEquals("https://example.org/chat",output.sources.items.getJSONObject(0).getString("url"));
 }
 @Test public void serverToolErrorsRemainVisibleAndAreNotMarkedSuccessful()throws Exception{
  JSONObject block=new JSONObject().put("type","web_search_tool_result").put("content",new JSONObject().put("type","web_search_tool_result_error").put("error_code","max_uses_exceeded"));
  Output output=new Output();parse(event(start(0,block))+event(stop(0))+event(start(1,new JSONObject().put("type","text").put("text","Could not search")))+event(stop(1))+event(end("end_turn"))+event(new JSONObject().put("type","message_stop")),ChatApiClient.ANTHROPIC,output);
  assertTrue(output.status,output.status.contains("错误"));assertTrue(output.events.toString().contains("failed"));assertEquals(0,output.sources.items.length());
 }
 @Test public void cancellationSuppressesNativeToolCallbacks()throws Exception{
  ChatApiClient.CancellationHandle cancel=new ChatApiClient.CancellationHandle();cancel.cancel();Output output=new Output();
  new ChatApiClient(cancel).parseSse(new ByteArrayInputStream(event(gemini("unused",true,true,true,true)).getBytes(StandardCharsets.UTF_8)),ChatApiClient.GEMINI,output);
  assertEquals("",output.text);assertEquals("",output.error);assertEquals(0,output.done);assertEquals(0,output.events.length());assertEquals(0,output.sources.items.length());
 }
}
