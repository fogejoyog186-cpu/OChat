package com.changyu.ochat;

import org.json.*;

/** Hosted tools execute at the configured provider, with protocol-specific declarations. */
final class BuiltinTools {
 static JSONObject apply(JSONObject body,String protocol,SearchOptions options)throws JSONException{
  if(!options.error.isEmpty())throw new IllegalArgumentException(options.error);
  if(!options.hasTools())return body;JSONArray tools=new JSONArray();
  if(ChatApiClient.RESPONSES.equals(protocol)){
   if(options.builtin){tools.put(new JSONObject().put("type","web_search"));body.put("include",new JSONArray().put("web_search_call.action.sources"));}
   if(options.codeExecution)tools.put(new JSONObject().put("type","code_interpreter").put("container",new JSONObject().put("type","auto")));
   if(tools.length()>0)body.put("tools",tools).put("tool_choice","auto");
  }else if(ChatApiClient.ANTHROPIC.equals(protocol)){
   if(options.builtin)tools.put(new JSONObject().put("type","web_search_20250305").put("name","web_search"));
   if(options.urlContext)tools.put(new JSONObject().put("type","web_fetch_20250910").put("name","web_fetch").put("citations",new JSONObject().put("enabled",true)));
   if(options.codeExecution)tools.put(new JSONObject().put("type","code_execution_20260521").put("name","code_execution"));
   if(tools.length()>0)body.put("tools",tools);
  }else if(ChatApiClient.GEMINI.equals(protocol)){
   if(options.builtin)tools.put(new JSONObject().put("googleSearch",new JSONObject()));
   if(options.urlContext)tools.put(new JSONObject().put("urlContext",new JSONObject()));
   if(options.codeExecution)tools.put(new JSONObject().put("codeExecution",new JSONObject()));
   if(tools.length()>0)body.put("tools",tools);
  }else if(ChatApiClient.CHAT_COMPLETIONS.equals(protocol)&&options.builtin)body.put("web_search_options",new JSONObject());
  return body;
 }
 static String error(int code,String protocol,SearchOptions options){
  String prefix=options.builtin&&!options.codeExecution&&!options.urlContext?"web_search_error":"builtin_tools_error";
  return prefix+"："+(code>0?"接口错误（"+code+"）":"请求未完成")+"。请核对供应商与模型是否支持所选内置工具、工具权限及计费。Chat Completions 内置搜索需要支持搜索的专用模型；普通模型可使用 Tavily。协议："+protocol+"。";
 }
 static String details(JSONArray events){
  if(events==null||events.length()==0)return "";StringBuilder text=new StringBuilder("\n\n工具记录：");
  for(int i=0;i<events.length();i++){
   JSONObject event=events.optJSONObject(i);if(event==null)continue;String state=event.optString("status");
   text.append("\n\n").append(event.optString("tool")).append(" · ").append(state.equals("completed")?"完成":state.equals("failed")?"失败":"调用");
   if(!event.optString("input").isEmpty())text.append("\n输入：\n").append(event.optString("input"));
   if(!event.optString("output").isEmpty())text.append("\n输出：\n").append(event.optString("output"));
  }
  return text.toString();
 }
 private BuiltinTools(){}
}
