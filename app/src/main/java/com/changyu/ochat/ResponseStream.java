package com.changyu.ochat;

import org.json.*;
import java.io.IOException;
import java.util.TreeMap;

/** Request-local canonical output. Terminal/full snapshots replace, never append deltas. */
final class ResponseStream {
 private final TreeMap<Long,String> parts=new TreeMap<>(),reasoningParts=new TreeMap<>();
 String reasoning(){StringBuilder out=new StringBuilder();for(String part:reasoningParts.values())if(!part.isEmpty()){if(out.length()>0)out.append("\n\n");out.append(part);}return out.toString();}
 private final java.util.Map<String,Integer> items=new java.util.HashMap<>();
 private final java.util.Set<Long> sequences=new java.util.HashSet<>();
 final SearchSources sources=new SearchSources();String searchStatus="";boolean searchCalled;
 String text(){StringBuilder b=new StringBuilder();for(String s:parts.values())b.append(s);return b.toString();}
 private long key(int output,int content){return ((long)output<<32)|(content&0xffffffffL);}
 void accept(String event,JSONObject o)throws Exception{
  String type=o.optString("type",event==null?"":event);
  if(o.has("sequence_number")&&!sequences.add(o.getLong("sequence_number")))return;
  sources.collect(o);
  if(type.startsWith("response.web_search_call.")){searchCalled=true;searchStatus=toolStatus(type.substring("response.web_search_call.".length()));}
  JSONObject tool=o.optJSONObject("item");if(tool!=null&&"web_search_call".equals(tool.optString("type"))){searchCalled=true;searchStatus=toolStatus(tool.optString("status"));}
  JSONObject finalResponse=o.optJSONObject("response");if(finalResponse!=null)inspectSearch(finalResponse);
  JSONObject item=o.optJSONObject("item");String id=o.optString("item_id",item==null?"":item.optString("id"));
  int i=o.optInt("output_index",items.getOrDefault(id,0)),j=o.optInt("content_index",0);if(!id.isEmpty())items.put(id,i);long k=key(i,j);
  if(type.equals("error")||type.equals("response.failed"))throw new IOException(ApiFailure.provider(o));
  if(type.equals("response.refusal.delta")||type.equals("response.refusal.done"))throw new IOException("refusal：模型拒绝回答");
  int summary=o.optInt("summary_index",0);long reasoningKey=key(i,summary);
  if(type.equals("response.reasoning_summary_text.delta"))reasoningParts.put(reasoningKey,reasoningParts.getOrDefault(reasoningKey,"")+o.optString("delta"));
  else if(type.equals("response.reasoning_summary_text.done")){String value=o.optString("text");if(!value.isEmpty())reasoningParts.put(reasoningKey,value);}
  else if(type.equals("response.reasoning_summary_part.added")||type.equals("response.reasoning_summary_part.done")){String value=ReasoningText.read(o.optJSONObject("part"));if(!value.isEmpty())reasoningParts.put(reasoningKey,value);}
  if(type.equals("response.output_text.delta"))parts.put(k,parts.getOrDefault(k,"")+o.optString("delta",""));
  else if(type.equals("response.output_text.done")){String value=o.optString("text","");if(!value.isEmpty())parts.put(k,value);}
  else if(type.equals("response.content_part.done")){JSONObject part=o.optJSONObject("part");if(part!=null&&"output_text".equals(part.optString("type"))){String value=part.optString("text","");if(!value.isEmpty())parts.put(k,value);}}
  else if(type.equals("response.output_item.done")){if(item!=null)replaceItem(i,item);}
  else if(type.equals("response.completed")||type.equals("response.incomplete")){JSONObject r=o.optJSONObject("response");if(r!=null){if(r.has("error")&&!r.isNull("error"))throw new IOException(ApiFailure.provider(o));JSONArray a=r.optJSONArray("output");if(a!=null)for(int n=0;n<a.length();n++){JSONObject x=a.optJSONObject(n);if(x!=null)replaceItem(n,x);}}if(type.equals("response.incomplete")&&text().isEmpty()&&reasoning().isEmpty())throw new IOException("token_limit：输出预算耗尽，没有可用答案");}
 }
 void inspectSearch(JSONObject response){JSONArray output=response.optJSONArray("output");if(output!=null)for(int n=0;n<output.length();n++){JSONObject item=output.optJSONObject(n);if(item!=null&&"web_search_call".equals(item.optString("type"))){searchCalled=true;searchStatus=toolStatus(item.optString("status"));}}if("incomplete".equals(response.optString("status"))&&searchCalled)searchStatus="内置搜索已调用 · 回复未完成";}
 private static String toolStatus(String status){switch(status){case "in_progress":return "内置搜索已调用 · 准备中…";case "searching":return "内置搜索正在搜索…";case "completed":return "内置搜索完成";case "failed":return "内置搜索失败";case "incomplete":return "内置搜索未完成";default:return "内置搜索已调用";}}
 private void replaceItem(int i,JSONObject item)throws Exception{
  if("reasoning".equals(item.optString("type"))){JSONArray summaries=item.optJSONArray("summary");if(summaries!=null)for(int j=0;j<summaries.length();j++){String value=ReasoningText.read(summaries.opt(j));if(!value.isEmpty())reasoningParts.put(key(i,j),value);}return;}
  if(!"message".equals(item.optString("type")))return;JSONArray c=item.optJSONArray("content");if(c==null)return;
  for(int j=0;j<c.length();j++){
   JSONObject p=c.optJSONObject(j);if(p==null)continue;
   if("refusal".equals(p.optString("type")))throw new IOException("refusal：模型拒绝回答");
   if("output_text".equals(p.optString("type"))){String value=p.optString("text","");if(!value.isEmpty())parts.put(key(i,j),value);}
  }
 }
}
