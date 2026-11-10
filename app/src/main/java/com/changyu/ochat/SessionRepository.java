package com.changyu.ochat;

import android.content.Context;
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
 private volatile Context snapshotContext;
 private static final long SNAPSHOT_SWEEP_INTERVAL=60L*60*1000;
 private long lastSnapshotSweep=-SNAPSHOT_SWEEP_INTERVAL;
 private JSONArray cached;private String observed;private final LinkedHashMap<String,String> encoded=new LinkedHashMap<>();
 private final Object lock=new Object();
 private SessionRepository(SharedPreferences p){prefs=p;}
 /** Register restored drafts before asynchronous cleanup, then retain application context only. */
 synchronized void manageSnapshots(Context context,Collection<JSONObject> pendingAttachments){
  AttachmentSnapshots.keepAlive(pendingAttachments);snapshotContext=context.getApplicationContext();read();disk.execute(this::pruneSnapshots);
 }
 private void pruneSnapshots(){
  Context context=snapshotContext;if(context==null)return;
  long elapsed=android.os.SystemClock.elapsedRealtime();if(elapsed-lastSnapshotSweep<SNAPSHOT_SWEEP_INTERVAL)return;lastSnapshotSweep=elapsed;
  List<String> records;synchronized(this){records=new ArrayList<>(encoded.values());}
  // Include committed state too while a newer revision is waiting on the disk executor.
  for(Map.Entry<String,?> entry:prefs.getAll().entrySet()){
   String key=entry.getKey();if(("sessions".equals(key)||key.startsWith("draft_"))&&entry.getValue() instanceof String)records.add((String)entry.getValue());
  }
  AttachmentSnapshots.prune(context,records,System.currentTimeMillis());
 }
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
   boolean saved=prefs.edit().putString("sessions",value).commit();
   synchronized(SessionRepository.this){if(version==revision){observed=value;pending=false;}
   }
   if(saved)pruneSnapshots();
  });
 }
 void checkpoint(String id,JSONObject draft){String value=draft.toString();disk.execute(()->{if(prefs.edit().putString("draft_"+id,value).commit())pruneSnapshots();});}
 void clearCheckpoint(String id){disk.execute(()->{if(prefs.edit().remove("draft_"+id).commit())pruneSnapshots();});}
 void flush()throws Exception{disk.submit(()->{}).get(10,TimeUnit.SECONDS);}
}
