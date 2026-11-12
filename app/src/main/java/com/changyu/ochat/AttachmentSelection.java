package com.changyu.ochat;

import android.content.*;
import android.net.Uri;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.*;
import java.util.UUID;

/** SAF for system documents; GET_CONTENT also admits third-party file managers such as MT. */
final class AttachmentSelection {
 static Intent documents(boolean images){return new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(images?"image/*":"*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);}
 static Intent managers(){
  Intent pick=new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
  return Intent.createChooser(pick,"选择文件管理器");
 }
 static JSONObject retain(Context context,Uri source,int flags)throws Exception{
  if(!"content".equals(source.getScheme()))throw new IOException("文件管理器未提供可读取的 content:// 文件，请在“文件”中改用支持授权的文件管理器");
  JSONObject descriptor=AttachmentBatch.descriptor(context.getContentResolver(),source);descriptor.put("sourceUri",source.toString());
  if((context.getPackageName()+".attachments").equals(source.getAuthority()))return descriptor;
  if((flags&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0){
   try{context.getContentResolver().takePersistableUriPermission(source,Intent.FLAG_GRANT_READ_URI_PERMISSION);return descriptor;}catch(SecurityException ignored){}
  }
  File folder=new File(context.getFilesDir(),"imported_attachments");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("无法创建附件目录");
  String name=descriptor.optString("displayName"),ext=name.contains(".")?name.substring(name.lastIndexOf('.')):"";if(!ext.matches("\\.[a-zA-Z0-9]{1,10}"))ext="";
  File target=new File(folder,UUID.randomUUID()+ext);boolean success=false;
  try(InputStream in=context.getContentResolver().openInputStream(source);OutputStream out=new FileOutputStream(target)){
   if(in==null)throw new IOException("无法打开所选文件");byte[] buffer=new byte[16384];int n;long total=0;
   while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();total+=n;out.write(buffer,0,n);}
   if(total==0)throw new IOException("所选文件为空");
   descriptor.put("uri",FileProvider.getUriForFile(context,context.getPackageName()+".attachments",target).toString()).put("importedCopy",true);success=true;return descriptor;
  }finally{if(!success)target.delete();}
 }
 static void discard(Context c,JSONObject d){AttachmentSnapshots.discard(c,d);if(d.optBoolean("importedCopy"))try{Uri uri=Uri.parse(d.optString("uri"));if((c.getPackageName()+".attachments").equals(uri.getAuthority()))c.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}}
 private AttachmentSelection(){}
}
