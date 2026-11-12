package com.changyu.ochat;

import okhttp3.*;
import org.json.*;
import java.io.IOException;
import java.util.Iterator;

/** Resumes paused server-tool turns without inventing user/tool results or losing container state. */
final class AnthropicToolRequest {
 private static final MediaType JSON=MediaType.get("application/json; charset=utf-8");
 private static final int MAX_CONTINUATIONS=10;
 private final ChatApiClient client;private final String base,key;private final JSONObject body;private final SearchOptions options;
 private final int budget;private int continuations;private final JSONObject usage=new JSONObject();
 private final SearchSources sources=new SearchSources();private String sourceStatus="";private final StringBuilder text=new StringBuilder();
 AnthropicToolRequest(ChatApiClient client,String base,String key,JSONObject body,SearchOptions options){this.client=client;this.base=base;this.key=key;this.body=body;this.options=options;budget=body.optInt("max_tokens");}
 private Request request(boolean stream){
  Request.Builder request=new Request.Builder().url(NativeProtocols.endpoint(base,ChatApiClient.ANTHROPIC,body.optString("model"),stream)).header("Accept",stream?"text/event-stream":"application/json");
  NativeProtocols.auth(request,key,ChatApiClient.ANTHROPIC);return request.post(JsonRequestBody.create(body,JSON)).build();
 }
 void stream(ChatApiClient.StreamCallback target){
  if(client.cancelled())return;
  ChatApiClient.StreamCallback callback=new ChatApiClient.StreamCallback(){
   public void onDelta(String value){target.onDelta(value);}public void onSnapshot(String value){target.onSnapshot(value);}
   public void onReasoning(String value){target.onReasoning(value);}public void onReasoningSnapshot(String value){target.onReasoningSnapshot(value);}
   public void onStatus(String value){target.onStatus(value);}public void onDone(){target.onDone();}public void onError(String value){target.onError(value);}
   public void onToolEvent(JSONObject value){target.onToolEvent(value);}
   public void onSearchSources(SearchSources value,String status){mergeSources(value,status);target.onSearchSources(sources,sourceStatus);}
   public void onFinished(GenerationResult result){try{addUsage(result.usage);target.onFinished(new GenerationResult(result.state,result.reason,usage));}catch(JSONException invalid){target.onError("工具用量信息无法解析");}}
  };
  client.enqueue(request(true),new Callback(){
   public void onFailure(Call call,IOException error){if(!client.cancelled())target.onError(safe("网络请求失败："+ApiFailure.local(error)));}
   public void onResponse(Call call,Response response){
    try(Response closed=response){
     if(!closed.isSuccessful()){target.onError(httpError(closed));return;}
     if(closed.body()==null)throw new IOException("接口没有返回内容");
     if(closed.header("Content-Type","").contains("application/json")){JSONObject value=new JSONObject(closed.body().string());if(value.has("error"))throw new IOException(ApiFailure.provider(value));throw new IOException("invalid_stream：已请求流式回复，但接口返回 application/json。");}
     JSONObject pause=client.parseSse(closed.body().byteStream(),ChatApiClient.ANTHROPIC,callback);
     if(pause!=null&&!client.cancelled()){
      addUsage(pause.getJSONObject("usage"));
      if(resume(pause)){target.onStatus("工具仍在执行，正在继续生成…");stream(target);}
      else target.onFinished(new GenerationResult("truncated","max_tokens",usage));
     }
    }catch(Exception failure){if(!client.cancelled())target.onError(safe(ApiFailure.local(failure)));}
   }
  });
 }
 void chat(ChatApiClient.CallbackResult<String> target){
  if(client.cancelled())return;
  client.enqueue(request(false),new Callback(){
   public void onFailure(Call call,IOException error){if(!client.cancelled())target.onError(safe("网络请求失败："+ApiFailure.local(error)));}
   public void onResponse(Call call,Response response){
    try(Response closed=response){
     if(!closed.isSuccessful()){target.onError(httpError(closed));return;}
     JSONObject value=new JSONObject(closed.body()==null?"":closed.body().string());if(value.has("error"))throw new IOException(ApiFailure.provider(value));
     NativeToolState state=new NativeToolState();state.complete(ChatApiClient.ANTHROPIC,value,NativeToolState.callback(target));mergeSources(state.sources,state.searchStatus);
     if(options.grounded())target.onSearchSources(sources,sourceStatus.isEmpty()?"本次未调用搜索或网页读取":sourceStatus);
     JSONArray content=value.optJSONArray("content");for(int i=0;content!=null&&i<content.length();i++){JSONObject part=content.optJSONObject(i);if(part!=null&&part.optString("type").equals("text"))text.append(part.optString("text"));}
     addUsage(GenerationResult.usage(value.optJSONObject("usage")));
     if("pause_turn".equals(value.optString("stop_reason"))){
      if(resume(state.paused(new JSONObject())))chat(target);
      else if(text.length()>0)target.onSuccess(text.toString());else throw new IOException("工具执行已达到输出预算，尚未生成最终答复。");
      return;
     }
     if(text.length()==0&&!state.fallback().isEmpty())text.append(state.fallback());if(text.length()==0)throw new IOException("模型未返回可用文本");
     target.onSuccess(text.toString());
    }catch(Exception failure){if(!client.cancelled())target.onError(safe(ApiFailure.local(failure)));}
   }
  });
 }
 private boolean resume(JSONObject paused)throws Exception{
  if(++continuations>MAX_CONTINUATIONS)throw new IOException("服务端工具连续暂停超过 "+MAX_CONTINUATIONS+" 次，本次已停止；已生成的内容会保留。");
  long remaining=budget-usage.optLong("output",0);JSONObject thinking=body.optJSONObject("thinking");
  if(remaining<=0||thinking!=null&&thinking.optLong("budget_tokens",0)>=remaining)return false;
  body.put("max_tokens",remaining);body.getJSONArray("messages").put(new JSONObject().put("role","assistant").put("content",paused.getJSONArray("content")));
  String container=paused.optString("container");if(!container.isEmpty())body.put("container",container);return true;
 }
 private void addUsage(JSONObject round)throws JSONException{Iterator<String> keys=round.keys();while(keys.hasNext()){String name=keys.next();usage.put(name,usage.optLong(name)+round.optLong(name));}}
 private void mergeSources(SearchSources value,String status){
  JSONArray all=new JSONArray();for(int i=0;i<sources.items.length();i++)all.put(sources.items.opt(i));for(int i=0;i<value.items.length();i++)all.put(value.items.opt(i));SearchSources merged=SearchSources.parse(all);
  for(int i=0;i<merged.items.length();i++)try{sources.items.put(i,merged.items.get(i));}catch(JSONException ignored){}
  if(!status.isEmpty())sourceStatus=status;
 }
 private String httpError(Response response)throws IOException{
  String detail=ApiFailure.http(response,ChatApiClient.ANTHROPIC,body,key);
  return options.hasTools()?BuiltinTools.error(response.code(),ChatApiClient.ANTHROPIC,options)+"\n"+detail:detail;
 }
 private String safe(String message){return ApiFailure.safe((options.hasTools()?BuiltinTools.error(0,ChatApiClient.ANTHROPIC,options)+"\n":"")+message,key);}
}
