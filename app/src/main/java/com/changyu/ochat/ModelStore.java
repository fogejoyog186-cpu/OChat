package com.changyu.ochat;

import android.content.SharedPreferences;
import org.json.*;

/** Configured models are never inferred from the available-model network cache. */
final class ModelStore {
 static JSONArray read(SharedPreferences p){try{return new JSONArray(p.getString("providers","[]"));}catch(Exception e){return new JSONArray();}}
 /** Sort only the fetched choices, preserving the real IDs and existing configured order. */
 static String[] sortedFetchedIds(JSONArray fetched){
  java.util.List<String> ids=new java.util.ArrayList<>();
  if(fetched!=null)for(int i=0;i<fetched.length();i++){
   JSONObject model=fetched.optJSONObject(i);String id=model==null?fetched.optString(i,""):model.optString("id");
   if(!id.trim().isEmpty())ids.add(id);
  }
  ids.sort(String.CASE_INSENSITIVE_ORDER);return ids.toArray(new String[0]);
 }
 static JSONObject model(String id){JSONObject m=new JSONObject();try{m.put("id",id).put("displayName",id).put("outputTokens",ContextBudget.DEFAULT_OUTPUT_TEXT).put("contextLength",ContextBudget.DEFAULT_CONTEXT_TEXT);}catch(Exception ignored){}return m;}
 static void migrateProvider(JSONObject p)throws JSONException {
  if(!p.has("protocol")||p.optString("protocol").trim().isEmpty())p.put("protocol",ChatApiClient.CHAT_COMPLETIONS);
  if(!p.has("models")){JSONArray models=new JSONArray();String id=p.optString("modelId",p.optString("model","")).trim();if(!id.isEmpty()){JSONObject m=model(id);m.put("displayName",p.optString("model",id));for(String k:new String[]{"outputTokens","contextLength"})if(p.has(k)&&!p.optString(k).trim().isEmpty())m.put(k,p.get(k));models.put(m);}p.put("models",models).put("selectedModelId",id);}
  fallback(p);
 }
 static void fallback(JSONObject p)throws JSONException{JSONArray a=p.optJSONArray("models");String selected=p.optString("selectedModelId","");boolean found=false;if(a!=null)for(int i=0;i<a.length();i++)if(a.optJSONObject(i)!=null&&selected.equals(a.optJSONObject(i).optString("id")))found=true;if(!found)p.put("selectedModelId",a!=null&&a.length()>0?a.getJSONObject(0).optString("id"):"");}
 static void addChecked(JSONObject p,String[] ids,boolean[] checked)throws JSONException{JSONArray a=p.getJSONArray("models");for(int i=0;i<ids.length;i++)if(checked[i]&&!ids[i].trim().isEmpty()){boolean exists=false;for(int j=0;j<a.length();j++)if(ids[i].trim().equals(a.getJSONObject(j).optString("id")))exists=true;if(!exists)a.put(model(ids[i].trim()));}fallback(p);}
 static void migrate(SharedPreferences prefs){try{JSONArray a=read(prefs);if(a.length()==0&&!prefs.contains("providers")){JSONObject p=new JSONObject().put("id","default").put("name",prefs.getString("provider","OpenAI Compatible"));for(String k:new String[]{"api","apiKey","model","modelId","contextLength","outputTokens","temperature","topP"})p.put(k,prefs.getString(k,k.equals("modelId")?prefs.getString("model","gpt-4o-mini"):k.equals("model")?"gpt-4o-mini":k.equals("api")?"https://api.openai.com/v1":k.equals("temperature")?"0.7":k.equals("topP")?"0.9":k.equals("outputTokens")?ContextBudget.DEFAULT_OUTPUT_TEXT:""));a.put(p);prefs.edit().putString("providerId","default").apply();}for(int i=0;i<a.length();i++)migrateProvider(a.getJSONObject(i));SharedPreferences.Editor e=prefs.edit().putString("providers",a.toString());if(!prefs.contains(SearchOptions.PREF))e.putString(SearchOptions.PREF,SearchOptions.AUTO);if(!prefs.contains("conversationTemperature")){JSONObject active=a.length()>0?a.getJSONObject(0):new JSONObject();for(int i=0;i<a.length();i++)if(prefs.getString("providerId","").equals(a.getJSONObject(i).optString("id")))active=a.getJSONObject(i);e.putString("conversationTemperature",prefs.getString("temperature",active.optString("temperature","0.7"))).putString("conversationTopP",prefs.getString("topP",active.optString("topP","0.9")));}e.apply();ReasoningSelection.reconcile(prefs);}catch(Exception e){android.util.Log.e("ModelStore","Migration failed",e);}}
 static JSONObject selected(JSONObject p){JSONArray a=p.optJSONArray("models");if(a!=null)for(int i=0;i<a.length();i++){JSONObject m=a.optJSONObject(i);if(m!=null&&p.optString("selectedModelId").equals(m.optString("id")))return m;}return new JSONObject();}
 static void activate(SharedPreferences prefs,JSONArray all,JSONObject p)throws JSONException{JSONObject m=selected(p);String binding=ReasoningSelection.identity(p.optString("id"),m.optString("id"));ModelCapabilities caps=new ModelCapabilities(m.optString("id"),p.optString("protocol",ChatApiClient.CHAT_COMPLETIONS),m);String effort=binding.equals(prefs.getString(ReasoningSelection.BINDING,""))?caps.normalize(prefs.getString("reasoningEffort","default")):"default";prefs.edit().putString(ReasoningSelection.BINDING,binding).putString("reasoningEffort",effort).putString("providers",all.toString()).putString("providerId",p.optString("id")).putString("provider",p.optString("name")).putString("api",p.optString("api")).putString("apiKey",p.optString("apiKey")).putString("modelId",m.optString("id")).putString("model",m.optString("displayName",m.optString("id"))).putString("outputTokens",m.optString("outputTokens",ContextBudget.DEFAULT_OUTPUT_TEXT)).apply();}
 static void deleteProvider(SharedPreferences prefs,String id)throws JSONException{
  JSONArray all=read(prefs),remaining=new JSONArray();JSONObject removed=null;
  for(int i=0;i<all.length();i++){JSONObject provider=all.optJSONObject(i);if(provider==null)continue;if(id.equals(provider.optString("id")))removed=provider;else remaining.put(provider);}
  if(removed==null)return;
  boolean active=id.equals(prefs.getString("providerId",""));
  if(active&&remaining.length()>0){
   JSONObject next=remaining.getJSONObject(0);
   for(int i=0;i<remaining.length();i++){JSONObject candidate=remaining.getJSONObject(i);JSONArray models=candidate.optJSONArray("models");if(models!=null&&models.length()>0){next=candidate;break;}}
   fallback(next);activate(prefs,remaining,next);
  }else{
   SharedPreferences.Editor editor=prefs.edit().putString("providers",remaining.toString());
   if(active||remaining.length()==0)for(String key:new String[]{"providerId","provider","api","apiKey","modelId","model","outputTokens","contextLength"})editor.putString(key,"");
   editor.apply();
  }
  String topicId=prefs.getString("topicProviderId","");
  boolean removedTopic=id.equals(topicId)||topicId.equals(removed.optString("name"))||(topicId.isEmpty()&&removed.optString("name").equals(prefs.getString("topicProvider","")));
  SharedPreferences.Editor cleanup=prefs.edit().remove("models_"+id);
  if(removedTopic||remaining.length()==0)cleanup.putBoolean("topicEnabled",false).putString("topicProviderId","__current__").putString("topicProvider","当前聊天供应商").putString("topicModel","");
  cleanup.apply();
 }
 static void saveProvider(SharedPreferences prefs,JSONObject provider)throws JSONException{
  JSONArray latest=read(prefs),next=new JSONArray();boolean replaced=false;fallback(provider);
  for(int i=0;i<latest.length();i++){JSONObject old=latest.optJSONObject(i);if(old==null)continue;if(provider.optString("id").equals(old.optString("id"))){next.put(provider);replaced=true;}else next.put(old);}
  if(!replaced)next.put(provider);activate(prefs,next,provider);
 }
 static double range(String s,double max){double v=Double.parseDouble(s);if(!Double.isFinite(v)||v<0||v>max)throw new IllegalArgumentException("范围 0.."+max);return v;}
}
