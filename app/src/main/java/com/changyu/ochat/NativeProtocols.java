package com.changyu.ochat;

import org.json.*;
import java.util.*;
import okhttp3.Request;

/** Native wire formats; internally messages use the existing OpenAI content shape. */
final class NativeProtocols {
 static boolean nativeProtocol(String p){return ChatApiClient.ANTHROPIC.equals(p)||ChatApiClient.GEMINI.equals(p);}
 static void auth(Request.Builder b,String key,String protocol){
  if(ChatApiClient.ANTHROPIC.equals(protocol))b.header("x-api-key",key.trim()).header("anthropic-version","2023-06-01");
  else if(ChatApiClient.GEMINI.equals(protocol))b.header("x-goog-api-key",key.trim());
  else if(key!=null&&!key.trim().isEmpty())b.header("Authorization","Bearer "+key.trim());
 }
 static String endpoint(String base,String protocol,String model,boolean stream){
  String b=base.trim();while(b.endsWith("/"))b=b.substring(0,b.length()-1);
  if(!b.startsWith("https://")&&!b.startsWith("http://"))throw new IllegalArgumentException("API 地址必须以 http:// 或 https:// 开头");
  if(ChatApiClient.ANTHROPIC.equals(protocol)){if(b.endsWith("/messages"))return b;return b+(b.endsWith("/v1")?"":"/v1")+"/messages";}
  int at=b.indexOf("/models/");if(at>=0)b=b.substring(0,at);
  if(!b.matches(".*/v1(beta)?$"))b+="/v1beta";
  String id=model.startsWith("models/")?model.substring(7):model;
  return b+"/models/"+encode(id)+(stream?":streamGenerateContent?alt=sse":":generateContent");
 }
 static String modelsEndpoint(String base,String protocol){
  if(ChatApiClient.ANTHROPIC.equals(protocol)){String b=base.replaceAll("/+$","").replaceAll("/messages$","");return b+(b.endsWith("/v1")?"":"/v1")+"/models";}
  String b=base.replaceAll("/+$","");int i=b.indexOf("/models");if(i>=0)b=b.substring(0,i);return b+(b.matches(".*/v1(beta)?$")?"":"/v1beta")+"/models";
 }
 static JSONObject body(String model,JSONArray history,double temperature,int max,double top,String effort,String protocol,ModelCapabilities caps)throws Exception{
  boolean sampling=caps.sampling;
  boolean claude=ChatApiClient.ANTHROPIC.equals(protocol);JSONArray messages=new JSONArray();StringBuilder system=new StringBuilder();
  for(int i=0;i<history.length();i++){JSONObject m=history.getJSONObject(i);String role=m.optString("role");if("system".equals(role)||"developer".equals(role)){system.append(m.optString("content")).append('\n');continue;}
   JSONArray parts=parts(m.opt("content"),claude);messages.put(new JSONObject().put("role","assistant".equals(role)?(claude?"assistant":"model"):"user").put(claude?"content":"parts",parts));
  }
  if(claude){JSONObject b=new JSONObject().put("model",model).put("messages",messages).put("max_tokens",max);if(system.length()>0)b.put("system",system.toString());if(sampling)b.put("temperature",Math.min(1,temperature)).put("top_p",top);
   ReasoningRequest.anthropic(b,caps,effort,max);return b;}
  JSONObject config=new JSONObject().put("maxOutputTokens",max);if(sampling)config.put("temperature",temperature).put("topP",top);
  ReasoningRequest.gemini(config,caps,effort);
  JSONObject b=new JSONObject().put("contents",messages).put("generationConfig",config);if(system.length()>0)b.put("systemInstruction",new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text",system.toString()))));return b;
 }
 private static JSONArray parts(Object content,boolean claude)throws Exception{
  JSONArray a=content instanceof JSONArray?(JSONArray)content:new JSONArray().put(new JSONObject().put("type","text").put("text",content==null?"":String.valueOf(content)));
  JSONArray out=new JSONArray();
  for(int i=0;i<a.length();i++){
   JSONObject p=a.optJSONObject(i);if(p==null)throw new IllegalArgumentException("附件内容块无效");
   String type=p.optString("type"),name="attachment";Object data="";boolean image=false;
   if(type.equals("text")||type.equals("input_text")||type.equals("output_text")){
    String value=p.optString("text");if(value.isEmpty())continue;
    JSONObject t=new JSONObject().put("text",value);if(claude)t.put("type","text");out.put(t);continue;
   }
   if(type.equals("image_url")){JSONObject picture=p.optJSONObject("image_url");if(picture==null)throw new IllegalArgumentException("图片内容字段无效");data=picture.opt("url");image=true;}
   else if(type.equals("input_image")){data=p.opt("image_url");image=true;}
   else if(type.equals("file")||type.equals("input_file")){JSONObject file=fileData(p);data=file.get("file_data");name=file.getString("filename");}
   else throw new IllegalArgumentException("当前原生协议不支持附件内容类型："+type);
   String mime=embeddedMime(data);
   if(image&&!mime.startsWith("image/"))throw new IllegalArgumentException("图片附件的 MIME 类型无效");
   if(!image&&!"application/pdf".equals(mime))throw new IllegalArgumentException("此原生协议的原文件输入仅支持 PDF，其他文档请提取文字后发送");
   Object base64=data instanceof FileAttachmentData?((FileAttachmentData)data).base64():((String)data).substring(((String)data).indexOf(',')+1);
   if(claude){
    JSONObject block=new JSONObject().put("type",image?"image":"document").put("source",new JSONObject().put("type","base64").put("media_type",mime).put("data",base64));
    if(!image)block.put("title",name);out.put(block);
   }else out.put(new JSONObject().put("inlineData",new JSONObject().put("mimeType",mime).put("data",base64)));
  }
  return out;
 }
 /** Normalize both OpenAI file shapes without copying their potentially large data strings. */
 static JSONObject fileData(JSONObject part)throws JSONException{
  JSONObject file="file".equals(part.optString("type"))?part.optJSONObject("file"):part;
  if(file==null)throw new IllegalArgumentException("附件缺少 file 字段");
  Object data=file.opt("file_data");String name=file.optString("filename",part.optString("filename","attachment"));
  if(!(data instanceof FileAttachmentData)&&(!(data instanceof String)||((String)data).isEmpty()))throw new IllegalArgumentException("附件缺少原始文件数据，请重新选择");
  return new JSONObject().put("file_data",data).put("filename",name.isEmpty()?"attachment":name);
 }
 /** The local attachment path uses standard base64, not URLs or encoded JSON strings. */
 static String embeddedMime(Object value){
  if(value instanceof FileAttachmentData)return ((FileAttachmentData)value).mime;
  if(!(value instanceof String))throw new IllegalArgumentException("附件数据无效");String data=(String)value;
  int semi=data.indexOf(';'),comma=data.indexOf(',');
  if(!data.startsWith("data:")||semi<=5||comma<=semi||!";base64".equals(data.substring(semi,comma))||comma+1>=data.length())throw new IllegalArgumentException("附件需要有效的 base64 内嵌文件数据");
  int length=data.length()-comma-1,padding=data.endsWith("==")?2:data.endsWith("=")?1:0;
  if(length%4==1||(padding>0&&length%4!=0))throw new IllegalArgumentException("附件 base64 数据不完整，请重新选择");
  for(int i=comma+1;i<data.length()-padding;i++){
   char c=data.charAt(i);if(!(c>='A'&&c<='Z'||c>='a'&&c<='z'||c>='0'&&c<='9'||c=='+'||c=='/'))throw new IllegalArgumentException("附件 base64 数据无效，请重新选择");
  }
  return data.substring(5,semi).toLowerCase(Locale.ROOT);
 }
 static String text(JSONObject o,String protocol)throws Exception{
  StringBuilder b=new StringBuilder();JSONArray a=ChatApiClient.ANTHROPIC.equals(protocol)?o.optJSONArray("content"):null;
  if(a==null){JSONArray candidates=o.optJSONArray("candidates");JSONObject first=candidates==null?null:candidates.optJSONObject(0),content=first==null?null:first.optJSONObject("content");a=content==null?null:content.optJSONArray("parts");}
  if(a!=null)for(int i=0;i<a.length();i++){JSONObject p=a.optJSONObject(i);if(p!=null&&!p.optBoolean("thought")&&(!p.has("type")||"text".equals(p.optString("type"))))b.append(p.optString("text"));}
  if(b.length()==0)throw new IllegalArgumentException("模型没有返回可用文本");return b.toString();
 }
 private static String encode(String id){try{return java.net.URLEncoder.encode(id,"UTF-8").replace("+","%20");}catch(java.io.UnsupportedEncodingException impossible){throw new IllegalStateException(impossible);}}
 private NativeProtocols(){}
}
