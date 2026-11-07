package com.changyu.ochat;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** URI-only persistence. Payloads exist only while preparing/sending a request. */
final class AttachmentBatch {
 static final int MAX_COUNT=10, MAX_ITEM_CHARS=512*1024, MAX_TOTAL_CHARS=8*1024*1024, MAX_FILE_BYTES=3*1024*1024;
 static List<Uri> collect(Intent data){
  LinkedHashSet<Uri> out=new LinkedHashSet<>();
  if(data!=null){if(data.getData()!=null)out.add(data.getData());ClipData clip=data.getClipData();if(clip!=null)for(int i=0;i<clip.getItemCount();i++){Uri u=clip.getItemAt(i).getUri();if(u!=null)out.add(u);}}
  return new ArrayList<>(out);
 }
 static JSONObject descriptor(ContentResolver resolver,Uri uri)throws JSONException{
  String mime=resolver.getType(uri),name="附件";if(mime==null)mime="application/octet-stream";
  try(android.database.Cursor c=resolver.query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0&&!c.isNull(i))name=c.getString(i);}}catch(Exception ignored){}
  mime=normalizedMime(mime,name);
  return new JSONObject().put("uri",uri.toString()).put("mime",mime).put("displayName",name).put("type",mime.startsWith("image/")?"image":mime.startsWith("text/")||mime.contains("csv")?"text":"document");
 }
 static Bitmap decode(ContentResolver resolver,Uri uri,int max)throws IOException{
  BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
  try(InputStream in=resolver.openInputStream(uri)){BitmapFactory.decodeStream(in,null,o);}
  if(o.outWidth<=0||o.outHeight<=0)throw new IOException("图片无法解码");
  int sample=1;while(o.outWidth/sample>max||o.outHeight/sample>max)sample*=2;
  o.inJustDecodeBounds=false;o.inSampleSize=sample;
  try(InputStream in=resolver.openInputStream(uri)){Bitmap b=BitmapFactory.decodeStream(in,null,o);if(b==null)throw new IOException("图片无法解码");return b;}
 }
 static String payload(ContentResolver resolver,JSONObject d)throws Exception{
  Uri u=Uri.parse(d.optString("uri"));if(!"content".equals(u.getScheme()))throw new IOException("附件地址无效");
  String mime=d.optString("mime");
  if(mime.equals("image/svg+xml")||d.optString("displayName").toLowerCase(Locale.ROOT).endsWith(".svg")){return new String(readBounded(resolver,u,MAX_FILE_BYTES),StandardCharsets.UTF_8);}
  if(mime.startsWith("image/")){
   Bitmap b=decode(resolver,u,1024);try{ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,78,out);String value="data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);return value.length()>MAX_ITEM_CHARS?"[图片超过单项 512KiB 编码限制，请选择较小图片]":value;}finally{b.recycle();}
  }
  if(mime.startsWith("text/")||mime.contains("csv")){
   try(InputStream in=resolver.openInputStream(u);Reader reader=new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)){
    StringBuilder s=new StringBuilder();char[] buffer=new char[4096];int n;while(s.length()<200000&&(n=reader.read(buffer,0,Math.min(buffer.length,200000-s.length())))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();s.append(buffer,0,n);}if(reader.read()!=-1)s.append("\n[文本超过 200000 字符，已截断]");return s.toString();
   }
  }
  String name=d.optString("displayName");
  String lower=name.toLowerCase(Locale.ROOT);if(lower.endsWith(".doc")||lower.endsWith(".xls")||lower.endsWith(".ppt")||mime.equals("application/msword")||mime.equals("application/vnd.ms-excel")||mime.equals("application/vnd.ms-powerpoint"))throw new IOException("旧版 Office 二进制格式无法读取，请另存为 DOCX、XLSX、PPTX 或 PDF");
  if(isPdf(mime,name)){
   byte[] bytes=readBounded(resolver,u,MAX_FILE_BYTES);
   return "data:application/pdf;base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP);
  }
  if(isOfficeOrTextDocument(mime,name)){
   byte[] bytes=readBounded(resolver,u,MAX_FILE_BYTES);
   String extracted=extractDocumentText(bytes,mime,name);
   if(extracted.trim().isEmpty())throw new IOException("文件中未提取到可发送的文字："+name);
   return extracted;
  }
  throw new IOException("不支持直接发送此格式："+name+"。请使用 PDF、图片或可读取的文本文档。");
 }
 static boolean isPdf(String mime,String name){return "application/pdf".equalsIgnoreCase(mime)||name.toLowerCase(Locale.ROOT).endsWith(".pdf");}
 static boolean isOfficeOrTextDocument(String mime,String name){String m=mime==null?"":mime.toLowerCase(Locale.ROOT),n=name==null?"":name.toLowerCase(Locale.ROOT);return m.startsWith("text/")||m.contains("json")||m.contains("xml")||m.contains("rtf")||m.contains("wordprocessingml")||m.contains("presentationml")||m.contains("spreadsheetml")||m.contains("msword")||m.contains("ms-excel")||m.contains("ms-powerpoint")||n.endsWith(".docx")||n.endsWith(".pptx")||n.endsWith(".xlsx")||n.endsWith(".doc")||n.endsWith(".ppt")||n.endsWith(".xls")||n.endsWith(".rtf")||n.endsWith(".md")||n.endsWith(".html")||n.endsWith(".xml")||n.endsWith(".json");}
 static String extractDocumentText(byte[] bytes,String mime,String name)throws IOException{String n=name.toLowerCase(Locale.ROOT);if(n.endsWith(".doc")||n.endsWith(".xls")||n.endsWith(".ppt"))throw new IOException("旧版 Office 二进制格式不支持，请转换为新版 Office 或 PDF");if(n.endsWith(".docx")||n.endsWith(".pptx")||n.endsWith(".xlsx")||mime.contains("wordprocessingml")||mime.contains("presentationml")||mime.contains("spreadsheetml")){StringBuilder text=new StringBuilder();try(java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes))){java.util.zip.ZipEntry entry;byte[] buffer=new byte[8192];while((entry=zip.getNextEntry())!=null){String path=entry.getName();boolean wanted=path.equals("word/document.xml")||path.startsWith("ppt/slides/slide")&&path.endsWith(".xml")||path.equals("xl/sharedStrings.xml")||path.startsWith("xl/worksheets/sheet")&&path.endsWith(".xml");if(wanted){ByteArrayOutputStream out=new ByteArrayOutputStream();int count,total=0;while((count=zip.read(buffer))!=-1){total+=count;if(total>2*1024*1024)throw new IOException("文档 XML 部分过大");out.write(buffer,0,count);}String xml=new String(out.toByteArray(),StandardCharsets.UTF_8);java.util.regex.Matcher m=java.util.regex.Pattern.compile("<(?:[\\w.-]+:)?t(?:\\s[^>]*)?>(.*?)</(?:[\\w.-]+:)?t>|<(?:[\\w.-]+:)?v(?:\\s[^>]*)?>(.*?)</(?:[\\w.-]+:)?v>",java.util.regex.Pattern.DOTALL).matcher(xml);while(m.find()){String value=m.group(1)!=null?m.group(1):m.group(2);text.append(value.replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replace("&quot;","\\\"").replace("&apos;","'")).append(' ');if(text.length()>200000)break;}text.append('\n');}zip.closeEntry();}}catch(Exception e){if(e instanceof IOException)throw(IOException)e;throw new IOException("Office 文档解析失败");}if(text.toString().trim().isEmpty())throw new IOException("Office 文档中没有提取到文字");return text.toString();}String value=new String(bytes,StandardCharsets.UTF_8);if(name.endsWith(".rtf"))value=value.replaceAll("\\\\'[0-9a-fA-F]{2}"," ").replaceAll("\\\\[a-zA-Z]+-?\\d* ?"," ").replaceAll("[{}]","");return value.length()>200000?value.substring(0,200000)+"\\n[文档文字超过 200000 字符，已截断]":value;}
 static int estimatedBytes(String payload){if(payload==null)return 0;if(payload.startsWith("data:")){int comma=payload.indexOf(',');int count=payload.length()-comma-1,padding=payload.endsWith("==")?2:payload.endsWith("=")?1:0;return comma<0?payload.length():Math.max(0,count*3/4-padding);}return payload.getBytes(StandardCharsets.UTF_8).length;}
 static JSONArray content(String text,List<JSONObject> descriptors,List<String> payloads,String protocol)throws JSONException{JSONArray content=new JSONArray().put(new JSONObject().put("type","text").put("text",text));int bytes=0;for(int i=0;i<Math.min(MAX_COUNT,descriptors.size());i++){String payload=payloads.get(i);bytes+=estimatedBytes(payload);if(bytes>MAX_TOTAL_CHARS)throw new JSONException("附件总大小超过限制");content.put(part(descriptors.get(i),payload,protocol));}return content;}
 static JSONArray contentForProtocol(String text,List<JSONObject> descriptors,List<String> payloads,String protocol)throws JSONException{return content(text,descriptors,payloads,protocol);}
 private static String normalizedMime(String mime,String name){String m=mime==null?"":mime.toLowerCase(Locale.ROOT);if(m.isEmpty()||"application/octet-stream".equals(m)){String n=name==null?"":name.toLowerCase(Locale.ROOT);String ext=n.contains(".")?n.substring(n.lastIndexOf('.')+1):"";if(Arrays.asList("txt","log","md","kt","kts","java","py","js","ts","tsx","jsx","css","c","cpp","h","rs","go","sh","sql","yaml","yml","toml","ini").contains(ext))return "text/plain";String guessed=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);if(guessed!=null)return guessed;if(n.endsWith(".pdf"))return "application/pdf";if(n.endsWith(".txt"))return "text/plain";if(n.endsWith(".md"))return "text/markdown";if(n.endsWith(".csv"))return "text/csv";if(n.endsWith(".json"))return "application/json";if(n.endsWith(".docx"))return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";}return m.isEmpty()?"application/octet-stream":m;}
 private static byte[] readBounded(ContentResolver resolver,Uri uri,int max)throws IOException{try(InputStream in=resolver.openInputStream(uri)){if(in==null)throw new IOException("文件不可读取");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[16384];int n,total=0;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();total+=n;if(total>max)throw new IOException("文件超过 3 MiB 附件限制，请选择较小文件");out.write(b,0,n);}if(total==0)throw new IOException("文件内容为空");return out.toByteArray();}}
 static JSONObject part(JSONObject descriptor,String payload)throws JSONException{return part(descriptor,payload,"responses");}
 static JSONObject part(JSONObject descriptor,String payload,String protocol)throws JSONException{
  if(payload.startsWith("data:image/"))return new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url",payload));
  if(payload.startsWith("data:")){String mime=payload.substring(5,payload.indexOf(';')>0?payload.indexOf(';'):payload.indexOf(','));String filename=descriptor.optString("displayName","attachment");if("responses".equals(protocol))return new JSONObject().put("type","input_file").put("file_data",payload).put("filename",filename);if("application/pdf".equals(mime))return new JSONObject().put("type","file").put("file",new JSONObject().put("file_data",payload).put("filename",filename));return new JSONObject().put("type","text").put("text","该文件需 Responses API 才能作为文件输入："+filename);}
  return new JSONObject().put("type","text").put("text",payload);
 }
 static JSONArray content(String text,List<JSONObject> descriptors,List<String> payloads)throws JSONException{return content(text,descriptors,payloads,ChatApiClient.RESPONSES);}
}
