package com.changyu.ochat;

import android.content.*;
import android.net.Uri;
import android.util.Base64;
import androidx.core.content.FileProvider;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Portable user-selected backup; file entries are spooled to disk, never held as one huge byte array. */
final class ConversationBackup {
 static final int MAX_JSON=32*1024*1024,MAX_TOTAL=150*1024*1024,MAX_FILE=MAX_TOTAL;
 static int exportTo(Context context,Uri uri,String snapshot)throws Exception{
  JSONArray sessions=new JSONArray(snapshot);Map<String,File> files=new LinkedHashMap<>();Map<String,String> uris=new HashMap<>();int[] total={0},missing={0};
  try{
   walk(sessions,d->{
    String original=d.optString("uri");if(original.isEmpty())return;String name=uris.get(original);
    if(name==null){
     InputStream input=null;
     try{input=context.getContentResolver().openInputStream(Uri.parse(original));}catch(IOException|SecurityException unavailable){}
     if(input==null){
      String payload=null;try{payload=AttachmentSnapshots.read(context,d);}catch(InterruptedIOException cancelled){throw cancelled;}catch(IOException missingSnapshot){}
      if(AttachmentSnapshots.isPdfPayload(payload))input=new FileInputStream(AttachmentSnapshots.pdfData(context,payload).file);
      else if(payload!=null){String type=AttachmentBatch.part(d,payload,ChatApiClient.RESPONSES).optString("type");
       if("input_file".equals(type)||"image_url".equals(type))input=new android.util.Base64InputStream(new ByteArrayInputStream(payload.substring(payload.indexOf(',')+1).getBytes(StandardCharsets.US_ASCII)),Base64.DEFAULT);
       else input=new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));}
     }
     if(input!=null){
      name="attachments/"+UUID.randomUUID();File spool=File.createTempFile("backup-",".part",context.getCacheDir());files.put(name,spool);
      try(InputStream in=input;OutputStream out=new FileOutputStream(spool)){total[0]+=copy(in,out,MAX_TOTAL-total[0]);}
      uris.put(original,name);
     }
    }
    d.remove("payloadSnapshotFile");d.remove("payloadSnapshotChars");d.remove("restoredCopy");
    if(name!=null){d.put("backupFile",name);d.remove("payloadSnapshot");}else {d.put("backupUnavailable",true);missing[0]++;}
   });
   JSONObject root=new JSONObject().put("format","ochat-backup").put("version",1).put("createdAt",System.currentTimeMillis()).put("sessions",sessions);
   byte[] json=root.toString().getBytes(StandardCharsets.UTF_8);if(json.length>MAX_JSON)throw new IOException("对话数据超过 32 MiB，请分批导出");
   try(OutputStream stream=context.getContentResolver().openOutputStream(uri,"wt");ZipOutputStream zip=new ZipOutputStream(Objects.requireNonNull(stream))){
    zip.putNextEntry(new ZipEntry("conversations.json"));zip.write(json);zip.closeEntry();
    for(Map.Entry<String,File> file:files.entrySet()){zip.putNextEntry(new ZipEntry(file.getKey()));try(InputStream in=new FileInputStream(file.getValue())){copy(in,zip,MAX_FILE);}zip.closeEntry();}
   }return missing[0];
  }finally{for(File file:files.values())file.delete();}
 }
 static JSONArray importFrom(Context context,Uri uri)throws Exception{
  Map<String,File> files=new HashMap<>();List<File> created=new ArrayList<>();boolean success=false;
  try{
   int total=0;JSONObject root=null;Set<String> names=new HashSet<>();
   try(InputStream stream=context.getContentResolver().openInputStream(uri);ZipInputStream zip=new ZipInputStream(Objects.requireNonNull(stream))){
    ZipEntry entry;int count=0;while((entry=zip.getNextEntry())!=null){
     if(++count>3000)throw new IOException("备份文件条目过多");String path=entry.getName();if(!names.add(path)||entry.isDirectory()||(!path.equals("conversations.json")&&!path.matches("attachments/[a-zA-Z0-9-]+")))throw new IOException("备份包含无效或重复条目");
     if(path.equals("conversations.json")){byte[] bytes=read(zip,MAX_JSON);total+=bytes.length;root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));}
     else {File spool=File.createTempFile("restore-",".part",context.getCacheDir());files.put(path,spool);try(OutputStream out=new FileOutputStream(spool)){total+=copy(zip,out,MAX_FILE);}}
     if(total>MAX_TOTAL)throw new IOException("备份解压后过大");zip.closeEntry();
    }
   }
   if(root==null||!root.optString("format").equals("ochat-backup")||root.optInt("version")!=1)throw new IOException("不是受支持的 OChat 备份");
   JSONArray sessions=root.getJSONArray("sessions");if(sessions.length()>10000)throw new IOException("对话数量过多");Map<String,String> restored=new HashMap<>();
   File folder=new File(context.getFilesDir(),"imported_attachments");if(!folder.exists()&&!folder.mkdirs())throw new IOException("无法创建附件目录");
   walk(sessions,d->{
    d.remove("payloadSnapshotFile");d.remove("payloadSnapshotChars");d.remove("restoredCopy");d.remove("importedCopy");
    if(AttachmentSnapshots.isPdfPayload(d.optString("payloadSnapshot")))d.remove("payloadSnapshot");
    String path=d.optString("backupFile");if(path.isEmpty())return;File spool=files.get(path);if(spool==null){d.put("backupUnavailable",true);return;}String content=restored.get(path);
    if(content==null){String original=d.optString("displayName","attachment"),ext=original.contains(".")?original.substring(original.lastIndexOf('.')):"";if(!ext.matches("\\.[a-zA-Z0-9]{1,10}"))ext="";
     File file=new File(folder,UUID.randomUUID()+ext);created.add(file);try(InputStream in=new FileInputStream(spool);OutputStream out=new FileOutputStream(file)){copy(in,out,MAX_FILE);}content=FileProvider.getUriForFile(context,context.getPackageName()+".attachments",file).toString();restored.put(path,content);}
    d.put("uri",content).put("importedCopy",true).put("restoredCopy",true);d.remove("backupFile");d.remove("backupUnavailable");
   });
   for(int i=0;i<sessions.length();i++){JSONObject session=sessions.getJSONObject(i);session.remove("pendingReply");session.remove("outbox");if(session.optJSONArray("messages")==null)throw new IOException("对话消息格式无效");session.put("id",UUID.randomUUID().toString());}
   MessageEdits.ensureIds(sessions);success=true;return sessions;
  }finally{for(File file:files.values())file.delete();if(!success)for(File file:created)file.delete();}
 }
 static void discard(Context context,JSONArray sessions){Set<String> uris=new HashSet<>();try{walk(sessions,d->{String uri=d.optString("uri");if(d.optBoolean("restoredCopy")&&uri.startsWith("content://"+context.getPackageName()+".attachments/"))uris.add(uri);});for(String uri:uris)context.getContentResolver().delete(Uri.parse(uri),null,null);}catch(Exception ignored){}}
 interface Descriptor {void accept(JSONObject descriptor)throws Exception;}
 static void walk(Object value,Descriptor operation)throws Exception{walk(value,operation,0);}
 private static void walk(Object value,Descriptor operation,int depth)throws Exception{
  if(depth>64)throw new IOException("备份数据嵌套过深");
  if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)walk(a.opt(i),operation,depth+1);}
  else if(value instanceof JSONObject){JSONObject o=(JSONObject)value;List<String> keys=new ArrayList<>();o.keys().forEachRemaining(keys::add);
   for(String key:keys)if(key.equals("apiKey")||key.equals("tavilyKey")||key.equals("Authorization"))o.remove(key);
   if(o.has("uri")&&o.has("mime")){operation.accept(o);return;}for(String key:keys)walk(o.opt(key),operation,depth+1);
  }
 }
 static byte[] read(InputStream in,int limit)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();copy(in,out,limit);return out.toByteArray();}
 private static int copy(InputStream in,OutputStream out,int limit)throws IOException{if(in==null)throw new IOException("文件不可读取");byte[] bytes=new byte[16384];int total=0,n;while((n=in.read(bytes))!=-1){total+=n;if(total>limit)throw new IOException("文件超过备份大小限制");if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();out.write(bytes,0,n);}return total;}
 private ConversationBackup(){}
}
