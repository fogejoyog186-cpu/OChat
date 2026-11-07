package com.changyu.ochat;

import android.content.SharedPreferences;
import org.json.*;

/** Bind effort to the exact provider/model selection, never to a global previous model. */
final class ReasoningSelection {
 static final String BINDING="reasoningModel";
 static String identity(String provider,String model){return provider+"\n"+model;}
 static JSONObject provider(SharedPreferences prefs,String id){JSONArray all=ModelStore.read(prefs);for(int i=0;i<all.length();i++){JSONObject p=all.optJSONObject(i);if(p!=null&&id.equals(p.optString("id")))return p;}return new JSONObject();}
 static boolean reconcile(JSONObject values,SharedPreferences prefs)throws JSONException{
  String provider=values.optString("providerId"),model=values.optString("modelId"),identity=identity(provider,model);
  JSONObject p=provider(prefs,provider);ModelCapabilities caps=new ModelCapabilities(model,p.optString("protocol",ChatApiClient.CHAT_COMPLETIONS),ModelCapabilities.find(p,model));
  String saved=values.optString("reasoningEffort","default"),valid=identity.equals(values.optString(BINDING))?caps.normalize(saved):"default";
  boolean changed=!identity.equals(values.optString(BINDING))||!valid.equals(saved);
  values.put(BINDING,identity).put("reasoningEffort",valid);return changed;
 }
 static void reconcile(SharedPreferences prefs){
  try{JSONObject values=new JSONObject().put("providerId",prefs.getString("providerId","")).put("modelId",prefs.getString("modelId","")).put("reasoningEffort",prefs.getString("reasoningEffort","default")).put(BINDING,prefs.getString(BINDING,""));
   if(reconcile(values,prefs))prefs.edit().putString(BINDING,values.optString(BINDING)).putString("reasoningEffort",values.optString("reasoningEffort")).apply();
  }catch(JSONException ignored){}
 }
 private ReasoningSelection(){}
}
