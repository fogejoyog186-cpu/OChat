package com.changyu.ochat;

import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Session-local settings reference provider credentials by ID; secrets are never copied. */
final class SessionConfig implements SharedPreferences {
 static final Set<String> KEYS=new HashSet<>(Arrays.asList("providerId","provider","modelId","model","reasoningEffort","systemPrompt","conversationTemperature","conversationTopP","outputTokens","webSearchEnabled",ReasoningSelection.BINDING));
 final SharedPreferences global;final JSONObject session,values;
 SessionConfig(SharedPreferences p,JSONObject s){global=p;session=s;JSONObject v=s.optJSONObject("config");if(v==null){v=capture(p);try{JSONArray messages=s.optJSONArray("messages");for(int i=messages==null?-1:messages.length()-1;i>=0;i--){JSONObject message=messages.optJSONObject(i);String id=message==null?"":message.optString("modelId");if(message==null||message.optBoolean("user")||id.isEmpty())continue;v.put("modelId",id);JSONArray providers=ModelStore.read(p);for(int j=0;j<providers.length();j++){JSONObject provider=providers.optJSONObject(j);JSONObject model=provider==null?new JSONObject():ModelCapabilities.find(provider,id);if(model.has("id")){v.put("providerId",provider.optString("id")).put("provider",provider.optString("name")).put("model",model.optString("displayName",id)).put("outputTokens",model.optString("outputTokens",ContextBudget.DEFAULT_OUTPUT_TEXT));break;}}break;}s.put("config",v);if(!s.optString("id").isEmpty())SessionRepository.get(p).upsert(s);}catch(JSONException ignored){}}values=v;try{if(ReasoningSelection.reconcile(values,global)&&!session.optString("id").isEmpty())SessionRepository.get(global).upsert(session);}catch(JSONException ignored){}}
 static JSONObject capture(SharedPreferences p){JSONObject v=new JSONObject();for(String key:KEYS){Object x=p.getAll().get(key);if(x!=null)try{v.put(key,x);}catch(JSONException ignored){}}if(!v.has("webSearchEnabled"))try{v.put("webSearchEnabled",false);}catch(JSONException ignored){}try{v.put("reasoningEffort","default").put(ReasoningSelection.BINDING,ReasoningSelection.identity(v.optString("providerId"),v.optString("modelId")));}catch(JSONException ignored){}return v;}
 JSONObject provider(){JSONArray all=ModelStore.read(global);for(int i=0;i<all.length();i++){JSONObject p=all.optJSONObject(i);if(p!=null&&getString("providerId","").equals(p.optString("id")))return p;}return new JSONObject();}
 JSONObject model(){return ModelCapabilities.find(provider(),getString("modelId",""));}
 public Map<String,?> getAll(){Map<String,Object> all=new HashMap<>(global.getAll());for(String key:KEYS)if(values.has(key))all.put(key,values.opt(key));return all;}
 public String getString(String k,String d){return KEYS.contains(k)?values.optString(k,d):global.getString(k,d);}
 public Set<String> getStringSet(String k,Set<String>d){return global.getStringSet(k,d);}
 public int getInt(String k,int d){return KEYS.contains(k)?values.optInt(k,d):global.getInt(k,d);}
 public long getLong(String k,long d){return KEYS.contains(k)?values.optLong(k,d):global.getLong(k,d);}
 public float getFloat(String k,float d){return KEYS.contains(k)?(float)values.optDouble(k,d):global.getFloat(k,d);}
 public boolean getBoolean(String k,boolean d){return KEYS.contains(k)?values.optBoolean(k,d):global.getBoolean(k,d);}
 public boolean contains(String k){return KEYS.contains(k)?values.has(k):global.contains(k);}
 public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){global.registerOnSharedPreferenceChangeListener(l);}
 public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){global.unregisterOnSharedPreferenceChangeListener(l);}
 public Editor edit(){return new Editor(){
  final Map<String,Object> changes=new HashMap<>();final SharedPreferences.Editor delegate=global.edit();
  private Editor put(String k,Object v){if(KEYS.contains(k))changes.put(k,v);else if(v==null)delegate.remove(k);else if(v instanceof Boolean)delegate.putBoolean(k,(Boolean)v);else if(v instanceof Number)delegate.putString(k,String.valueOf(v));else delegate.putString(k,String.valueOf(v));return this;}
  public Editor putString(String k,String v){return put(k,v);}public Editor putBoolean(String k,boolean v){return put(k,v);}public Editor putInt(String k,int v){return put(k,v);}public Editor putLong(String k,long v){return put(k,v);}public Editor putFloat(String k,float v){return put(k,v);}public Editor putStringSet(String k,Set<String>v){delegate.putStringSet(k,v);return this;}public Editor remove(String k){return put(k,null);}public Editor clear(){for(String k:KEYS)changes.put(k,null);return this;}
  public void apply(){for(Map.Entry<String,Object> c:changes.entrySet())try{if(c.getValue()==null)values.remove(c.getKey());else values.put(c.getKey(),c.getValue());}catch(JSONException ignored){}try{ReasoningSelection.reconcile(values,global);}catch(JSONException ignored){}delegate.apply();if(!session.optString("id").isEmpty())SessionRepository.get(global).upsert(session);}
  public boolean commit(){apply();return true;}
 };}
}
