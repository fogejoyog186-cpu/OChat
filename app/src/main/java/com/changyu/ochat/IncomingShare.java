package com.changyu.ochat;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Process;
import androidx.core.content.IntentCompat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Only URI/text data crosses the public share boundary; no nested intent is launched. */
final class IncomingShare {
 static final int MAX_TEXT_CHARS=64000,MAX_URI_CHARS=8192;
 static boolean accepts(Intent intent){return intent!=null&&(Intent.ACTION_SEND.equals(intent.getAction())||Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction()));}
 static final class Request {
  final String id;final ArrayList<Uri> uris;final int flags;final String text;final boolean shared;
  boolean started,autoSend;String expectedDraft="",sessionId="",modelId="",providerId="";
  Request(List<Uri> uris,int flags,String text,boolean shared){this(UUID.randomUUID().toString(),uris,flags,text,shared);}
  private Request(String id,List<Uri> uris,int flags,String text,boolean shared){this.id=id;this.uris=new ArrayList<>(uris);this.flags=flags;this.text=text;this.shared=shared;}
  JSONObject json()throws JSONException{
   JSONArray sources=new JSONArray();for(Uri uri:uris)sources.put(uri.toString());
   return new JSONObject().put("id",id).put("uris",sources).put("flags",flags).put("text",text).put("shared",shared).put("started",started).put("autoSend",autoSend).put("expectedDraft",expectedDraft).put("sessionId",sessionId).put("modelId",modelId).put("providerId",providerId);
  }
  static Request restore(JSONObject json)throws JSONException{
   JSONArray sources=json.getJSONArray("uris");if(sources.length()>AttachmentBatch.MAX_COUNT)throw new JSONException("Too many shared files");
   ArrayList<Uri> uris=new ArrayList<>();for(int i=0;i<sources.length();i++){String value=sources.getString(i);if(value.length()>MAX_URI_CHARS)throw new JSONException("Shared URI too long");Uri uri=Uri.parse(value);if(!"content".equals(uri.getScheme()))throw new JSONException("Invalid shared URI");uris.add(uri);}
   String text=json.optString("text");if(text.length()>MAX_TEXT_CHARS)throw new JSONException("Shared text too long");
   Request request=new Request(json.getString("id"),uris,json.optInt("flags")&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),text,json.optBoolean("shared"));
   request.started=json.optBoolean("started");request.autoSend=json.optBoolean("autoSend");request.expectedDraft=json.optString("expectedDraft");request.sessionId=json.optString("sessionId");request.modelId=json.optString("modelId");request.providerId=json.optString("providerId");return request;
  }
 }
 static Request read(Context context,Intent intent)throws IOException{
  if(!accepts(intent))return null;
  LinkedHashSet<Uri> uris=new LinkedHashSet<>();
  try{
   if(Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())){
    ArrayList<Uri> streams=IntentCompat.getParcelableArrayListExtra(intent,Intent.EXTRA_STREAM,Uri.class);
    if(streams!=null){if(streams.size()>AttachmentBatch.MAX_COUNT)throw new IOException("一次最多分享 "+AttachmentBatch.MAX_COUNT+" 个文件");for(Uri uri:streams)add(uris,uri);}
   }else add(uris,IntentCompat.getParcelableExtra(intent,Intent.EXTRA_STREAM,Uri.class));
   ClipData clip=intent.getClipData();
   if(clip!=null){if(clip.getItemCount()>AttachmentBatch.MAX_COUNT)throw new IOException("一次最多分享 "+AttachmentBatch.MAX_COUNT+" 个文件");for(int i=0;i<clip.getItemCount();i++)add(uris,clip.getItemAt(i).getUri());}
   add(uris,intent.getData());
   if(uris.isEmpty())throw new IOException("没有收到可读取的文件，请在其他应用选中文档后分享到 OChat");
   if(uris.size()>AttachmentBatch.MAX_COUNT)throw new IOException("一次最多分享 "+AttachmentBatch.MAX_COUNT+" 个文件");
   for(Uri uri:uris)validate(context,uri);
   CharSequence value=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
   String text=value==null?"":value.toString();if(text.length()>MAX_TEXT_CHARS)throw new IOException("分享附带的文本过长，请缩短后重试");
   // A share grant is temporary. Always make a local copy instead of retaining caller flags.
   return new Request(new ArrayList<>(uris),Intent.FLAG_GRANT_READ_URI_PERMISSION,text,true);
  }catch(IOException error){throw error;}catch(RuntimeException malformed){throw new IOException("分享数据无效或文件授权不可用，请重新分享");}
 }
 private static void add(Collection<Uri> uris,Uri uri)throws IOException{
  if(uri==null)return;if(uri.toString().length()>MAX_URI_CHARS)throw new IOException("文件地址过长");uris.add(uri.normalizeScheme());
 }
 static void validate(Context context,Uri uri)throws IOException{
  if(uri==null||!"content".equals(uri.getScheme())||uri.toString().length()>MAX_URI_CHARS)throw new IOException("分享文件必须提供 content:// 读取授权");
  String authority=uri.getAuthority();if(authority==null||authority.isEmpty())throw new IOException("分享文件地址无效");
  int user=authority.lastIndexOf('@');if(user>=0){if(!authority.substring(0,user).matches("[0-9]+"))throw new IOException("分享文件地址无效");authority=authority.substring(user+1);}
  if((context.getPackageName()+".attachments").equals(authority))throw new IOException("不能通过外部分享读取 OChat 的私有附件");
  ProviderInfo provider=context.getPackageManager().resolveContentProvider(authority,0);
  if(provider!=null&&provider.applicationInfo!=null&&provider.applicationInfo.uid==Process.myUid())throw new IOException("不能通过外部分享读取 OChat 的私有文件");
  boolean granted=context.checkUriPermission(uri,Process.myPid(),Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION)==PackageManager.PERMISSION_GRANTED;
  // Public read-only providers need no grant. Non-exported FileProviders are accepted only with a real URI grant.
  if(!granted&&(provider==null||!provider.exported||provider.readPermission!=null||(provider.pathPermissions!=null&&provider.pathPermissions.length>0)))throw new IOException("分享应用未授予文件读取权限，请从原应用重新分享");
 }
 private IncomingShare(){}
}
