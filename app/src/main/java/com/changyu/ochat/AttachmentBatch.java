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
 static final int MAX_COUNT=10, MAX_ITEM_CHARS=512*1024, MAX_TOTAL_CHARS=49_999_999, MAX_FILE_BYTES=49_999_999;
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
  String mime=d.optString("mime"),name=d.optString("displayName");
  // Prefer PDF identification before a provider's generic or inaccurate text MIME.
  if(isPdf(mime,name)){try(InputStream input=resolver.openInputStream(u)){return pdfPayload(input);}}
  boolean svg=mime.equals("image/svg+xml")||name.toLowerCase(Locale.ROOT).endsWith(".svg");
  if(mime.startsWith("image/")&&!svg){
   Bitmap bitmap=decode(resolver,u,1024);
   try{
    ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,78,out);
    String value="data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
    if(value.length()>MAX_ITEM_CHARS)throw new IOException("图片编码超过 512 KiB，请压缩图片后发送");
    return value;
   }finally{bitmap.recycle();}
  }
  if(svg||isOfficeOrTextDocument(mime,name)||mime.contains("csv")){
   try(InputStream input=resolver.openInputStream(u)){
    String text=extractDocumentText(input,mime,name);
    if(text.trim().isEmpty())throw new IOException("文件中未提取到可发送的文字："+name);
    return text;
   }
  }
  throw new IOException("不支持直接发送此格式："+name+"。请使用 PDF、图片或可读取的文本文档。");
 }
 /** Encodes the unchanged PDF, including scanned page images, without retaining a raw file copy. */
 static String pdfPayload(InputStream input)throws IOException{
  if(input==null)throw new IOException("文件不可读取");
  BufferedInputStream in=new BufferedInputStream(input);in.mark(1024);
  byte[] header=new byte[1024];int length=0,count;
  while(length<header.length&&(count=in.read(header,length,header.length-length))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("附件读取已取消");length+=count;}
  if(!new String(header,0,length,StandardCharsets.ISO_8859_1).contains("%PDF-"))throw new IOException("文件不是可识别的 PDF，请检查格式或重新导出");
  in.reset();ByteArrayOutputStream bytes=new ByteArrayOutputStream();bytes.write("data:application/pdf;base64,".getBytes(StandardCharsets.US_ASCII));long total=0;
  try(android.util.Base64OutputStream encoded=new android.util.Base64OutputStream(bytes,Base64.NO_WRAP|Base64.NO_CLOSE)){
   byte[] buffer=new byte[16384];while((count=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("附件读取已取消");total+=count;if(total>MAX_FILE_BYTES)throw new IOException("PDF 超过 50 MB 附件限制，请拆分文件后发送");encoded.write(buffer,0,count);}
  }
  return bytes.toString(StandardCharsets.US_ASCII.name());
 }
 static boolean isPdf(String mime,String name){return "application/pdf".equalsIgnoreCase(mime)||name.toLowerCase(Locale.ROOT).endsWith(".pdf");}
 static boolean isOfficeOrTextDocument(String mime,String name){String m=mime==null?"":mime.toLowerCase(Locale.ROOT),n=name==null?"":name.toLowerCase(Locale.ROOT);return m.startsWith("text/")||m.contains("json")||m.contains("xml")||m.contains("rtf")||m.contains("wordprocessingml")||m.contains("presentationml")||m.contains("spreadsheetml")||m.contains("msword")||m.contains("ms-excel")||m.contains("ms-powerpoint")||n.endsWith(".docx")||n.endsWith(".pptx")||n.endsWith(".xlsx")||n.endsWith(".doc")||n.endsWith(".ppt")||n.endsWith(".xls")||n.endsWith(".rtf")||n.endsWith(".md")||n.endsWith(".html")||n.endsWith(".xml")||n.endsWith(".json");}
 static String extractDocumentText(byte[] bytes,String mime,String name)throws IOException{return extractDocumentText(new ByteArrayInputStream(bytes),mime,name);}
 /** The caller owns and closes input. Text extraction never loads the entire document into memory. */
 static String extractDocumentText(InputStream input,String mime,String name)throws IOException{return DocumentText.extract(input,mime,name);}
 private static final String FAILURE_PREFIX="\u0000OChatAttachmentError:";
 static String failure(String message){return FAILURE_PREFIX+(message==null?"附件读取失败":message);}
 static boolean isFailure(String payload){return payload!=null&&payload.startsWith(FAILURE_PREFIX);}
 static String failureMessage(String payload){return isFailure(payload)?payload.substring(FAILURE_PREFIX.length()):"";}
 /** A plain-text document can itself start with "data:" or contain a complete data URI. */
 private static String embeddedMime(String payload){
  if(payload==null||!payload.startsWith("data:"))return "";
  int semi=payload.indexOf(';'),comma=payload.indexOf(',');
  if(semi<=5||semi>128||comma!=semi+7||comma+1>=payload.length()||!";base64".equals(payload.substring(semi,comma)))return "";
  String mime=payload.substring(5,semi).toLowerCase(Locale.ROOT);
  return mime.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")?mime:"";
 }
 private static String binaryMime(JSONObject descriptor,String payload){
  String encoded=embeddedMime(payload);if(encoded.isEmpty())return "";
  String mime=descriptor.optString("mime").toLowerCase(Locale.ROOT),name=descriptor.optString("displayName");
  if(isPdf(mime,name))return encoded;
  if(isOfficeOrTextDocument(mime,name)||mime.contains("csv")){
   // Preserve legacy explicit Office file snapshots; newly extracted Office text remains text.
   boolean rawOffice=encoded.equals(mime)&&(mime.contains("wordprocessingml")||mime.contains("presentationml")||mime.contains("spreadsheetml")||mime.contains("msword")||mime.contains("ms-excel")||mime.contains("ms-powerpoint"));
   if(!rawOffice)return "";
  }
  return encoded;
 }
 static int estimatedBytes(JSONObject descriptor,String payload){return payload==null?0:binaryMime(descriptor,payload).isEmpty()?payload.getBytes(StandardCharsets.UTF_8).length:encodedBytes(payload);}
 static int estimatedBytes(String payload){return payload==null?0:embeddedMime(payload).isEmpty()?payload.getBytes(StandardCharsets.UTF_8).length:encodedBytes(payload);}
 private static int encodedBytes(String payload){int count=payload.length()-payload.indexOf(',')-1,padding=payload.endsWith("==")?2:payload.endsWith("=")?1:0;return Math.max(0,count*3/4-padding);}
 static JSONArray content(String text,List<JSONObject> descriptors,List<String> payloads,String protocol)throws JSONException{
  if(descriptors.size()>MAX_COUNT)throw new JSONException("最多添加 10 个附件");
  if(payloads.size()!=descriptors.size())throw new JSONException("附件准备未完成，请重新选择文件");
  JSONArray content=new JSONArray().put(new JSONObject().put("type","text").put("text",text));long bytes=0;
  for(int i=0;i<descriptors.size();i++){String payload=payloads.get(i);if(payload==null||isFailure(payload))throw new JSONException(payload==null?"附件准备未完成":failureMessage(payload));bytes+=estimatedBytes(descriptors.get(i),payload);if(bytes>MAX_TOTAL_CHARS)throw new JSONException("附件原始数据合计须小于 50 MB，请减少附件或拆分文件");content.put(part(descriptors.get(i),payload,protocol));}
  return content;
 }
 static JSONArray contentForProtocol(String text,List<JSONObject> descriptors,List<String> payloads,String protocol)throws JSONException{return content(text,descriptors,payloads,protocol);}
 private static String normalizedMime(String mime,String name){String m=mime==null?"":mime.toLowerCase(Locale.ROOT);if(m.isEmpty()||"application/octet-stream".equals(m)){String n=name==null?"":name.toLowerCase(Locale.ROOT);String ext=n.contains(".")?n.substring(n.lastIndexOf('.')+1):"";if(Arrays.asList("txt","log","md","kt","kts","java","py","js","ts","tsx","jsx","css","c","cpp","h","rs","go","sh","sql","yaml","yml","toml","ini").contains(ext))return "text/plain";String guessed=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);if(guessed!=null)return guessed;if(n.endsWith(".pdf"))return "application/pdf";if(n.endsWith(".txt"))return "text/plain";if(n.endsWith(".md"))return "text/markdown";if(n.endsWith(".csv"))return "text/csv";if(n.endsWith(".json"))return "application/json";if(n.endsWith(".docx"))return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";}return m.isEmpty()?"application/octet-stream":m;}
 static JSONObject part(JSONObject descriptor,String payload)throws JSONException{return part(descriptor,payload,"responses");}
 static JSONObject part(JSONObject descriptor,String payload,String protocol)throws JSONException{
  String mime=binaryMime(descriptor,payload);
  if(mime.startsWith("image/"))return new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url",payload));
  if(!mime.isEmpty()){String filename=descriptor.optString("displayName","attachment");if("responses".equals(protocol))return new JSONObject().put("type","input_file").put("file_data",payload).put("filename",filename);if("application/pdf".equals(mime))return new JSONObject().put("type","file").put("file",new JSONObject().put("file_data",payload).put("filename",filename));return new JSONObject().put("type","text").put("text","该文件需 Responses API 才能作为文件输入："+filename);}
  return new JSONObject().put("type","text").put("text",payload);
 }
 static JSONArray content(String text,List<JSONObject> descriptors,List<String> payloads)throws JSONException{return content(text,descriptors,payloads,ChatApiClient.RESPONSES);}
}
