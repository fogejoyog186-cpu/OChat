package com.changyu.ochat;

import org.json.*;
import java.util.*;

/** Translate the visible control into the selected protocol's actual request fields. */
final class ReasoningRequest {
 private static boolean automatic(String value){return value==null||value.isEmpty()||"default".equals(value);}
 static void chat(JSONObject body,ModelCapabilities caps,String effort,int max)throws JSONException{
  String value=caps.normalize(effort);if(automatic(value))return;
  if(caps.control.equals(ModelCapabilities.TOGGLE)){thinking(body,value);return;}
  if(caps.family.equals("deepseek")||caps.family.equals("glm")){
   thinking(body,value);if(!value.equals("none")&&!value.equals("enabled"))body.put("reasoning_effort",value);return;
  }
  if(caps.control.equals(ModelCapabilities.CLAUDE_BUDGET)){claudeBudget(body,value,max);return;}
  body.put("reasoning_effort",value);
 }
 static void responses(JSONObject body,ModelCapabilities caps,String effort)throws JSONException{
  String value=caps.normalize(effort);JSONObject reasoning=new JSONObject();
  if(!automatic(value)&&!value.equals("enabled"))reasoning.put("effort",value);
  String id=ModelCapabilities.canonical(caps.id);
  if(caps.family.equals("gpt")&&!caps.control.equals(ModelCapabilities.FIXED)&&!id.startsWith("o1")&&!value.equals("none"))reasoning.put("summary","auto");
  if(reasoning.length()>0)body.put("reasoning",reasoning);
 }
 static void anthropic(JSONObject body,ModelCapabilities caps,String effort,int max)throws JSONException{
  String value=caps.normalize(effort);if(automatic(value))return;
  if(caps.family.equals("deepseek")){
   thinking(body,value);if(!value.equals("none")&&!value.equals("enabled"))body.put("output_config",new JSONObject().put("effort",value));return;
  }
  if(caps.needsBudget()){
   claudeBudget(body,value,max);
   if(caps.control.equals(ModelCapabilities.CLAUDE_LEGACY_EFFORT)&&!value.equals("none"))body.put("output_config",new JSONObject().put("effort",value));return;
  }
  if(value.equals("none"))body.put("thinking",new JSONObject().put("type","disabled"));
  else body.put("thinking",new JSONObject().put("type","adaptive")).put("output_config",new JSONObject().put("effort",value));
 }
 static void gemini(JSONObject config,ModelCapabilities caps,String effort)throws JSONException{
  String value=caps.normalize(effort);boolean known=caps.control.equals(ModelCapabilities.GEMINI_BUDGET)||caps.control.equals(ModelCapabilities.GEMINI_LEVEL);
  if(automatic(value)&&!known)return;
  JSONObject thinking=new JSONObject();if(known&&!value.equals("none"))thinking.put("includeThoughts",true);
  if(automatic(value)){config.put("thinkingConfig",thinking);return;}
  if(caps.control.equals(ModelCapabilities.GEMINI_BUDGET)){
   int budget=value.equals("none")?0:value.equals("low")||value.equals("minimal")?1024:value.equals("medium")?8192:24576;
   thinking.put("thinkingBudget",budget);
  }else thinking.put("thinkingLevel",value);
  config.put("thinkingConfig",thinking);
 }
 private static void thinking(JSONObject body,String value)throws JSONException{body.put("thinking",new JSONObject().put("type",value.equals("none")?"disabled":"enabled"));}
 private static void claudeBudget(JSONObject body,String value,int max)throws JSONException{
  if(value.equals("none")){thinking(body,value);return;}
  if(max<=1024)throw new IllegalArgumentException("此 Claude 模型开启思考时，输出上限必须大于 1024 token");
  body.put("thinking",new JSONObject().put("type","enabled").put("budget_tokens",Math.min(8192,Math.max(1024,max/2))));
 }
 static JSONArray chatMessages(JSONArray messages,boolean preserveReasoning)throws JSONException{
  JSONArray result=new JSONArray();
  for(int i=0;i<messages.length();i++){JSONObject original=messages.optJSONObject(i);if(original==null)continue;JSONObject copy=new JSONObject();Iterator<String> keys=original.keys();while(keys.hasNext()){String key=keys.next();if(key.startsWith("_")||key.equals("reasoning_content")&&(!preserveReasoning||!"assistant".equals(original.optString("role"))))continue;copy.put(key,original.get(key));}result.put(copy);}
  return result;
 }
 private ReasoningRequest(){}
}
