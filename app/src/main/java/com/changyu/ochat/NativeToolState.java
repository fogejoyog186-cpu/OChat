package com.changyu.ochat;

import org.json.*;
import java.io.IOException;
import java.util.*;

/** Per-response native tool state, citations, and the exact Claude blocks needed by pause_turn. */
final class NativeToolState {
 final SearchSources sources=new SearchSources();String searchStatus="",container="";boolean searchCalled;
 private final TreeMap<Integer,JSONObject> blocks=new TreeMap<>();
 private final Map<Integer,StringBuilder> inputs=new HashMap<>();private final Set<String> events=new HashSet<>();
 private final StringBuilder fallback=new StringBuilder();
 void observe(String protocol,String type,JSONObject value,ChatApiClient.StreamCallback callback)throws Exception{
  sources.collect(value);
  if(ChatApiClient.ANTHROPIC.equals(protocol)){
   JSONObject message=value.optJSONObject("message"),delta=value.optJSONObject("delta");
   rememberContainer(value);if(message!=null)rememberContainer(message);if(delta!=null)rememberContainer(delta);
   if(type.equals("content_block_start")){
    int index=value.optInt("index");JSONObject block=value.optJSONObject("content_block");
    if(block!=null){blocks.put(index,block);inspectBlock(block,callback,false);}
   }else if(type.equals("content_block_delta")){
    int index=value.optInt("index");JSONObject block=blocks.get(index);
    if(block!=null&&delta!=null){
     for(String key:new String[]{"text","thinking","signature"})if(delta.has(key))block.put(key,block.optString(key)+delta.optString(key));
     if(delta.has("partial_json"))inputs.computeIfAbsent(index,k->new StringBuilder()).append(delta.optString("partial_json"));
     JSONObject citation=delta.optJSONObject("citation");if(citation!=null){JSONArray citations=block.optJSONArray("citations");if(citations==null){citations=new JSONArray();block.put("citations",citations);}citations.put(citation);}
    }
   }else if(type.equals("content_block_stop")){
    int index=value.optInt("index");JSONObject block=blocks.get(index);StringBuilder input=inputs.remove(index);
    if(block!=null){if(input!=null&&input.length()>0)block.put("input",new JSONObject(input.toString()));inspectBlock(block,callback,true);}
   }
  }else if(ChatApiClient.GEMINI.equals(protocol)){
   JSONArray candidates=value.optJSONArray("candidates");JSONObject first=candidates==null?null:candidates.optJSONObject(0);
   if(first!=null){
    JSONObject grounding=first.optJSONObject("groundingMetadata");
    if(grounding!=null&&(grounding.optJSONArray("webSearchQueries")!=null&&grounding.optJSONArray("webSearchQueries").length()>0||grounding.optJSONArray("groundingChunks")!=null&&grounding.optJSONArray("groundingChunks").length()>0)){searchCalled=true;searchStatus="内置搜索完成";record("google_search","completed",grounding.optJSONArray("webSearchQueries")==null?"":grounding.optJSONArray("webSearchQueries").toString(),"",callback);}
    JSONObject urls=first.optJSONObject("urlContextMetadata");JSONArray retrieved=urls==null?null:urls.optJSONArray("urlMetadata");if(retrieved!=null&&retrieved.length()>0){boolean failed=false;for(int i=0;i<retrieved.length();i++){JSONObject item=retrieved.optJSONObject(i);if(item!=null&&!item.optString("urlRetrievalStatus").contains("SUCCESS"))failed=true;}searchCalled=true;searchStatus=failed?"部分网页读取失败":"网页读取完成";record("url_context",failed?"failed":"completed",urls.toString(),"",callback);}
    JSONObject content=first.optJSONObject("content");JSONArray parts=content==null?null:content.optJSONArray("parts");
    for(int i=0;parts!=null&&i<parts.length();i++){
     JSONObject part=parts.optJSONObject(i);if(part==null)continue;JSONObject code=part.optJSONObject("executableCode"),result=part.optJSONObject("codeExecutionResult");
     if(code!=null){callback.onStatus("正在执行代码…");record("code_execution","running",code.optString("code"),"",callback);}
     if(result!=null){boolean ok=result.optString("outcome").equals("OUTCOME_OK");String output=result.optString("output");callback.onStatus(ok?"代码执行完成":"代码执行返回错误");boolean fresh=record("code_execution",ok?"completed":"failed","",output,callback);if(fresh&&!output.isEmpty())fallback.append(ok?"":"执行错误：\n").append(output).append('\n');}
    }
   }
  }else if(ChatApiClient.RESPONSES.equals(protocol)){
   if(type.startsWith("response.code_interpreter_call."))callback.onStatus(type.endsWith("completed")?"代码执行完成":"正在执行代码…");
   JSONObject item=value.optJSONObject("item");if(item!=null&&item.optString("type").equals("code_interpreter_call")&&type.equals("response.output_item.done"))inspectCodeItem(item,callback);
   JSONObject response=value.optJSONObject("response");if(response!=null)inspectResponse(response,callback);
  }else if(sources.items.length()>0)searchStatus="已收到模型引用来源";
  if(!ChatApiClient.RESPONSES.equals(protocol))publish(callback);
 }
 void complete(String protocol,JSONObject response,ChatApiClient.StreamCallback callback)throws Exception{
  sources.collect(response);rememberContainer(response);
  if(ChatApiClient.ANTHROPIC.equals(protocol)){JSONArray content=response.optJSONArray("content");for(int i=0;content!=null&&i<content.length();i++){JSONObject block=content.optJSONObject(i);if(block!=null){blocks.put(i,block);inspectBlock(block,callback,true);}}}
  else if(ChatApiClient.RESPONSES.equals(protocol))inspectResponse(response,callback);
  else if(ChatApiClient.GEMINI.equals(protocol))observe(protocol,"",response,callback);
  publish(callback);
 }
 private void inspectResponse(JSONObject response,ChatApiClient.StreamCallback callback)throws Exception{
  JSONArray output=response.optJSONArray("output");for(int i=0;output!=null&&i<output.length();i++){JSONObject item=output.optJSONObject(i);if(item!=null&&item.optString("type").equals("code_interpreter_call"))inspectCodeItem(item,callback);else if(item!=null&&item.optString("type").equals("web_search_call")){JSONObject action=item.optJSONObject("action");record("web_search",item.optString("status","completed"),action==null?"":action.optString("query",action.optString("url",action.optString("type"))),"",callback);}}
 }
 private void inspectCodeItem(JSONObject item,ChatApiClient.StreamCallback callback)throws Exception{
  StringBuilder output=new StringBuilder();JSONArray values=item.optJSONArray("outputs");for(int i=0;values!=null&&i<values.length();i++){JSONObject value=values.optJSONObject(i);if(value!=null&&value.optString("type").equals("logs"))output.append(value.optString("logs")).append('\n');}
  if(record("code_interpreter",item.optString("status","completed"),item.optString("code"),output.toString(),callback)&&output.length()>0)fallback.append(output);
 }
 private void inspectBlock(JSONObject block,ChatApiClient.StreamCallback callback,boolean ended)throws Exception{
  String type=block.optString("type"),name=block.optString("name");
  if(type.equals("server_tool_use")){
   if(name.equals("web_search")||name.equals("web_fetch")){searchCalled=true;searchStatus=name.equals("web_search")?"内置搜索正在搜索…":"正在读取网页…";}
   callback.onStatus(name.contains("code_execution")?"正在执行代码…":name.equals("web_fetch")?"正在读取网页…":"正在搜索…");
   if(ended)record(name,"running",block.optJSONObject("input")==null?"":block.optJSONObject("input").toString(),"",callback);
  }else if(type.endsWith("_tool_result")){
   JSONObject result=block.optJSONObject("content");boolean failed=result!=null&&(result.optString("type").endsWith("_error")||result.optInt("return_code",0)!=0);
   String tool=type.startsWith("web_search")?"web_search":type.startsWith("web_fetch")?"web_fetch":"code_execution";
   String output=result==null?"":result.optString("stdout")+result.optString("stderr");if(result!=null&&output.isEmpty())output=result.optString("error_code");
   if(tool.equals("web_search")||tool.equals("web_fetch")){searchCalled=true;searchStatus=failed?"内置工具返回错误"+(output.isEmpty()?"":" · "+output):tool.equals("web_search")?"内置搜索完成":"网页读取完成";}
   if(record(tool,failed?"failed":"completed","",output,callback)&&tool.equals("code_execution")&&!output.isEmpty())fallback.append(failed?"执行错误：\n":"").append(output).append('\n');
   sources.collect(block);
  }
 }
 private boolean record(String tool,String status,String input,String output,ChatApiClient.StreamCallback callback)throws JSONException{
  String signature=tool+'\u0000'+status+'\u0000'+input+'\u0000'+output;if(!events.add(signature))return false;
  callback.onToolEvent(new JSONObject().put("tool",tool).put("status",status).put("input",preview(input)).put("output",preview(output)));return true;
 }
 private static String preview(String value){return value.length()>4000?value.substring(0,4000)+"\n… 工具记录已截取":value;}
 private void rememberContainer(JSONObject value){JSONObject c=value.optJSONObject("container");if(c!=null&&!c.optString("id").isEmpty())container=c.optString("id");}
 void publish(ChatApiClient.StreamCallback callback){if(searchCalled||sources.items.length()>0)callback.onSearchSources(sources,searchStatus);}
 String fallback(){return fallback.toString().trim();}
 JSONObject paused(JSONObject usage)throws Exception{
  JSONArray content=new JSONArray();for(JSONObject block:blocks.values()){if(block.optString("type").equals("tool_use")||block.optString("type").equals("mcp_tool_use"))throw new IOException("接口要求客户端工具执行，但本次仅启用了服务端内置工具。");content.put(block);}
  if(content.length()==0)throw new IOException("服务端暂停了工具调用，但没有返回可继续的内容块。");
  return new JSONObject().put("content",content).put("container",container).put("usage",usage);
 }
 static ChatApiClient.StreamCallback callback(ChatApiClient.CallbackResult<?> target){
  return new ChatApiClient.StreamCallback(){public void onDelta(String value){}public void onReasoning(String value){}public void onStatus(String value){}public void onDone(){}public void onError(String message){target.onError(message);}public void onSearchSources(SearchSources sources,String status){target.onSearchSources(sources,status);}public void onToolEvent(JSONObject event){target.onToolEvent(event);}};
 }
}
