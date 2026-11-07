package com.changyu.ochat;

import android.content.SharedPreferences;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Main-thread object cache plus ordered background persistence. Streaming checkpoints
 * serialize only their own conversation; aggregate JSON and disk I/O stay off the UI thread.
 * The legacy key remains a complete, portable migration/backup format.
 */
final class SessionRepository {
 private static final Map<SharedPreferences,SessionRepository> INSTANCES=new WeakHashMap<>();
 static synchronized SessionRepository get(SharedPreferences p){SessionRepository r=INSTANCES.get(p);if(r==null){r=new SessionRepository(p);INSTANCES.put(p,r);}return r;}
 private final SharedPreferences prefs;private final ExecutorService disk=Executors.newSingleThreadExecutor();
 private JSONArray cached;private String observed;private final LinkedHashMap<String,String> encoded=new LinkedHashMap<>();
 private final Object lock=new Object();
 private SessionRepository(SharedPreferences p){prefs=p;}
 synchronized JSONArray read(){
  String raw=prefs.getString("sessions","[]");
  if(cached==null||(!Objects.equals(raw,observed)&&!pending)){
   try{cached=new JSONArray(raw);MessageEdits.ensureIds(cached);observed=raw;encoded.clear();for(int i=0;i<cached.length();i++){JSONObject s=cached.optJSONObject(i);if(s!=null)encoded.put(s.optString("id"),s.toString());}}catch(JSONException e){cached=new JSONArray();observed=raw;}
  }
  return cached;
 }
 private volatile boolean pending;private long revision;
 synchronized void replace(JSONArray all){cached=all;encoded.clear();for(int i=0;i<all.length();i++){JSONObject s=all.optJSONObject(i);if(s!=null)encoded.put(s.optString("id"),s.toString());}enqueue();}
 synchronized void upsert(JSONObject session){
  JSONArray all=read();boolean found=false;for(int i=0;i<all.length();i++)if(session.optString("id").equals(all.optJSONObject(i).optString("id"))){try{all.put(i,session);}catch(JSONException ignored){}found=true;break;}if(!found)all.put(session);
  encoded.put(session.optString("id"),session.toString());enqueue();
 }
 private void enqueue(){
  final long version=++revision;final List<String> snapshot=new ArrayList<>(encoded.values());pending=true;
  disk.execute(()->{
   synchronized(SessionRepository.this){if(version!=revision)return;}
   StringBuilder text=new StringBuilder("[");for(String s:snapshot){if(text.length()>1)text.append(',');text.append(s);}text.append(']');String value=text.toString();
   synchronized(SessionRepository.this){
    if(version!=revision)return; // an older checkpoint must never overwrite a newer edit
   }
   prefs.edit().putString("sessions",value).commit();
   synchronized(SessionRepository.this){if(version==revision){observed=value;pending=false;}
   }
  });
 }
 void checkpoint(String id,JSONObject draft){String value=draft.toString();disk.execute(()->prefs.edit().putString("draft_"+id,value).commit());}
 void clearCheckpoint(String id){disk.execute(()->prefs.edit().remove("draft_"+id).commit());}
 void flush()throws Exception{disk.submit(()->{}).get(10,TimeUnit.SECONDS);}
}
