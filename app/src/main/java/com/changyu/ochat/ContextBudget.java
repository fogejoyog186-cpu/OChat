package com.changyu.ochat;

import org.json.*;

/** Deliberately conservative estimate, not a tokenizer or a billing measurement. */
final class ContextBudget {
 static final int DEFAULT_CONTEXT=128000,DEFAULT_OUTPUT=1000000;
 static final String DEFAULT_CONTEXT_TEXT="128000",DEFAULT_OUTPUT_TEXT="1000000";
 static final class Result {final JSONArray messages;final int estimatedTokens,omitted,outputTokens;Result(JSONArray m,int t,int o,int output){messages=m;estimatedTokens=t;omitted=o;outputTokens=output;}}
 static int estimate(Object value){
  if(value instanceof JSONArray){int sum=0;JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)sum+=estimate(a.opt(i));return sum;}
  if(value instanceof JSONObject){JSONObject p=(JSONObject)value;String type=p.optString("type");if(type.contains("image"))return 2048;if(type.equals("file")||type.equals("input_file"))return 8192;if(p.has("content"))return 6+estimate(p.opt("content"))+(p.optString("reasoning_content").isEmpty()?0:estimate(p.optString("reasoning_content")));return estimate(p.optString("text",""));}
  String s=value==null?"":String.valueOf(value);int units=0;for(int i=0;i<s.length();i++)units+=s.charAt(i)<128?1:4;return (units+2)/3+1;
 }
 static Result trim(JSONArray history,int context,int output)throws JSONException{
  if(context<512||output<1)throw new IllegalArgumentException("上下文容量至少为 512 token，输出上限必须为正整数");
  int usable=context-Math.max(256,context/20),reserved=Math.min(output,Math.max(1,usable/4)),available=usable-reserved;
  int total=estimate(history),start=0;
  while(start<history.length()&&"system".equals(history.optJSONObject(start).optString("role")))start++;
  int first=start,removed=0;
  while(total>available&&first<history.length()-1){
   int end=first+1;while(end<history.length()&&!"user".equals(history.optJSONObject(end).optString("role")))end++;
   if(end>=history.length())break;
   for(int i=first;i<end;i++){JSONObject message=history.optJSONObject(i);if(message!=null&&("system".equals(message.optString("role"))||"developer".equals(message.optString("role"))))continue;total-=estimate(history.opt(i));removed++;}first=end;
  }
  if(total>=usable)throw new IllegalArgumentException("本次消息或附件超过上下文预算，请缩短内容、减少附件，或在模型详情提高已核实的上下文容量");
  JSONArray out=new JSONArray();for(int i=0;i<history.length();i++){JSONObject message=history.optJSONObject(i);boolean instruction=message!=null&&("system".equals(message.optString("role"))||"developer".equals(message.optString("role")));if(i<start||i>=first||instruction)out.put(history.get(i));}
  return new Result(out,total,removed,Math.min(output,usable-total));
 }
 private ContextBudget(){}
}
