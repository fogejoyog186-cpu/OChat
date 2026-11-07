package com.changyu.ochat;

import org.json.*;
import java.util.Locale;

/** Completion and usage are independent: a response may have text and still be incomplete. */
final class GenerationResult {
 final String state,reason;final JSONObject usage;
 GenerationResult(String state,String reason,JSONObject usage){this.state=state;this.reason=reason==null?"":reason;this.usage=usage==null?new JSONObject():usage;}
 static GenerationResult of(String reason,JSONObject usage){
  String r=reason==null?"":reason.toLowerCase(Locale.ROOT),state="incomplete";
  if(r.contains("length")||r.contains("max_token")||r.contains("max_output"))state="truncated";
  else if(r.contains("refusal")||r.contains("filter")||r.contains("safety")||r.contains("block")||r.contains("recitation"))state="filtered";
  else if(r.equals("tool_calls")||r.equals("tool_use")||r.equals("pause_turn"))state="incomplete";
  else if(r.equals("interrupted")||r.isEmpty())state="interrupted";
  else if(r.equals("stop")||r.equals("end_turn")||r.equals("stop_sequence")||r.equals("eos"))state="complete";
  return new GenerationResult(state,reason,usage);
 }
 String label(){switch(state){case "truncated":return "达到输出上限 · 可继续回答";case "filtered":return "内容受限 · 回复未完成";case "interrupted":return "连接中断 · 已保留内容";case "incomplete":return "回复未完成";case "error":return "生成失败 · 可重试";default:return "回复已完成";}}
 static JSONObject usage(JSONObject raw)throws JSONException{
  JSONObject u=new JSONObject();if(raw==null)return u;
  long input=raw.optLong("input_tokens",raw.optLong("prompt_tokens",raw.optLong("promptTokenCount",-1))),output=raw.optLong("output_tokens",raw.optLong("completion_tokens",raw.optLong("candidatesTokenCount",-1)));
  if(input>=0)u.put("input",input);if(output>=0)u.put("output",output);
  JSONObject in=raw.optJSONObject("input_tokens_details");if(in==null)in=raw.optJSONObject("prompt_tokens_details");
  JSONObject out=raw.optJSONObject("output_tokens_details");if(out==null)out=raw.optJSONObject("completion_tokens_details");
  long cached=in==null?raw.optLong("cache_read_input_tokens",raw.optLong("cachedContentTokenCount",-1)):in.optLong("cached_tokens",-1);
  long thinking=out==null?raw.optLong("thoughtsTokenCount",-1):out.optLong("reasoning_tokens",-1);
  if(raw.has("candidatesTokenCount")&&output>=0&&thinking>0)u.put("output",output+thinking);
  if(cached>=0)u.put("cached",cached);if(thinking>=0)u.put("reasoning",thinking);return u;
 }
 static void merge(JSONObject target,JSONObject from)throws JSONException{java.util.Iterator<String> keys=from.keys();while(keys.hasNext()){String k=keys.next();target.put(k,from.get(k));}}
}
