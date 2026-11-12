package com.changyu.ochat;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Keep large immutable request snapshots out of SharedPreferences and saved instance state. */
final class AttachmentSnapshots {
 static final int INLINE_CHARS=128*1024;
 static final long ORPHAN_GRACE_MILLIS=24L*60*60*1000;
 private static final String KEY_PATTERN="[a-f0-9-]{36}\\.(?:payload|pdf)";
 static final String PDF_MARKER="\u0000OChatPdf:";
 static boolean isPdfPayload(String payload){return payload!=null&&payload.startsWith(PDF_MARKER);}
 static FileAttachmentData pdfData(Context context,String payload)throws IOException{
  if(!isPdfPayload(payload))throw new IOException("PDF 引用无效");
  File file=resolve(context,payload.substring(PDF_MARKER.length()));if(!file.getName().endsWith(".pdf")||!file.isFile())throw new IOException("PDF 快照不可用，请重新添加");
  return new FileAttachmentData(file,"application/pdf",true);
 }
 static String capturePdf(Context context,JSONObject descriptor,InputStream input)throws Exception{
  if(input==null)throw new IOException("PDF 无法读取");
  File directory=folder(context);if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法保存 PDF");
  String key=UUID.randomUUID()+".pdf";File target=new File(directory,key),partial=new File(directory,key+".part");boolean saved=false;
  try{
   BufferedInputStream source=new BufferedInputStream(input);source.mark(1024);byte[] header=new byte[1024];int count=0,n;
   while(count<header.length&&(n=source.read(header,count,header.length-count))!=-1){checkCancelled();count+=n;}
   if(!new String(header,0,count,StandardCharsets.ISO_8859_1).contains("%PDF-"))throw new IOException("文件不是可识别的 PDF，请检查格式");source.reset();
   try(OutputStream output=new FileOutputStream(partial)){byte[] buffer=new byte[16384];while((n=source.read(buffer))!=-1){checkCancelled();output.write(buffer,0,n);}}
   checkCancelled();if(!partial.renameTo(target))throw new IOException("无法保存 PDF");
   descriptor.put(FILE,key).remove("payloadSnapshot");descriptor.remove(CHARS);keepAlive(descriptor);saved=true;return PDF_MARKER+key;
  }finally{partial.delete();if(!saved)target.delete();}
 }
 // Weak leases protect unsent/being-read descriptors without retaining an Activity or draft forever.
 private static final Map<JSONObject,String> LIVE=new WeakHashMap<>();
 static void keepAlive(Collection<JSONObject> descriptors){synchronized(LIVE){for(JSONObject d:descriptors)keepAlive(d);}}
 private static void keepAlive(JSONObject descriptor){synchronized(LIVE){String key=descriptor.optString(FILE);if(key.matches(KEY_PATTERN))LIVE.put(descriptor,key);}}
 /** Only removes old, unreferenced private snapshots; original attachments are never traversed. */
 static int prune(Context context,Collection<String> records,long now){
  Set<String> referenced=new HashSet<>();
  try{for(String record:records){Object state=new JSONTokener(record).nextValue();if(!(state instanceof JSONObject)&&!(state instanceof JSONArray))return 0;collect(state,referenced,0);}}
  catch(JSONException|RuntimeException invalid){return 0;} // Unreadable state must not cause data loss.
  File[] files=folder(context).listFiles();if(files==null)return 0;int removed=0;
  for(File file:files){
   String name=file.getName(),key=name.endsWith(".part")?name.substring(0,name.length()-5):name;
   long modified=file.lastModified();
   if(!key.matches(KEY_PATTERN)||!file.isFile()||modified<=0||modified>now-ORPHAN_GRACE_MILLIS||referenced.contains(key))continue;
   synchronized(LIVE){if(!LIVE.containsValue(key)&&file.delete())removed++;}
  }
  return removed;
 }
 private static void collect(Object value,Set<String> keys,int depth)throws JSONException{
  if(depth>64)throw new JSONException("Snapshot references are nested too deeply");
  if(value instanceof JSONObject){
   JSONObject object=(JSONObject)value;String key=object.optString(FILE);if(key.matches(KEY_PATTERN))keys.add(key);
   Iterator<String> names=object.keys();while(names.hasNext())collect(object.opt(names.next()),keys,depth+1);
  }else if(value instanceof JSONArray){JSONArray array=(JSONArray)value;for(int i=0;i<array.length();i++)collect(array.opt(i),keys,depth+1);}
 }
 private static final String FILE="payloadSnapshotFile",CHARS="payloadSnapshotChars";
 private static File folder(Context context){return new File(context.getFilesDir(),"attachment_payloads");}
 static boolean has(JSONObject descriptor){return descriptor.has("payloadSnapshot")||!descriptor.optString(FILE).isEmpty();}
 static void capture(Context context,JSONObject descriptor,String payload)throws Exception{
  if(payload==null||AttachmentBatch.isFailure(payload))throw new IOException("附件内容尚未准备好");
  if(isPdfPayload(payload)){pdfData(context,payload);descriptor.put(FILE,payload.substring(PDF_MARKER.length()));descriptor.remove("payloadSnapshot");descriptor.remove(CHARS);keepAlive(descriptor);return;}
  String previous=descriptor.optString(FILE);
  if(payload.length()<=INLINE_CHARS){
   descriptor.put("payloadSnapshot",payload);descriptor.remove(FILE);descriptor.remove(CHARS);delete(context,previous);return;
  }
  File directory=folder(context);if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法保存附件内容");
  String key=UUID.randomUUID()+".payload";File target=new File(directory,key),partial=new File(directory,key+".part");boolean saved=false;
  try{
   try(Writer out=new OutputStreamWriter(new FileOutputStream(partial),StandardCharsets.UTF_8)){
    for(int offset=0;offset<payload.length();offset+=8192){checkCancelled();out.write(payload,offset,Math.min(8192,payload.length()-offset));}
   }
   checkCancelled();if(!partial.renameTo(target))throw new IOException("无法保存附件内容");
   descriptor.put(FILE,key).put(CHARS,payload.length());descriptor.remove("payloadSnapshot");keepAlive(descriptor);saved=true;delete(context,previous);
  }finally{partial.delete();if(!saved)target.delete();}
 }
 static String read(Context context,JSONObject descriptor)throws IOException{
  if(descriptor.has("payloadSnapshot"))return descriptor.optString("payloadSnapshot");
  String key=descriptor.optString(FILE);if(key.isEmpty())return null;
  keepAlive(descriptor);File file=resolve(context,key);if(!file.isFile())throw new IOException("已保存的附件内容不可用，请重新添加附件");
  if(key.endsWith(".pdf"))return PDF_MARKER+key;
  try(Reader input=new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8)){
   StringBuilder out=new StringBuilder();char[] buffer=new char[8192];int count;
   while((count=input.read(buffer))!=-1){checkCancelled();out.append(buffer,0,count);}return out.toString();
  }
 }
 /** Only removes our private snapshot, never an original or provider-owned attachment. */
 static void discard(Context context,JSONObject descriptor){
  delete(context,descriptor.optString(FILE));descriptor.remove(FILE);descriptor.remove(CHARS);descriptor.remove("payloadSnapshot");
 }
 private static File resolve(Context context,String key)throws IOException{
  if(!key.matches(KEY_PATTERN))throw new IOException("附件快照地址无效");
  File directory=folder(context).getCanonicalFile(),file=new File(directory,key).getCanonicalFile();
  if(!directory.equals(file.getParentFile()))throw new IOException("附件快照地址无效");return file;
 }
 private static void delete(Context context,String key){if(!key.isEmpty())try{resolve(context,key).delete();}catch(IOException ignored){}}
 private static void checkCancelled()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("附件准备已取消");}
 private AttachmentSnapshots(){}
}
