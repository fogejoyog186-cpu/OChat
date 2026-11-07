package com.changyu.ochat;

import org.json.*;
import okhttp3.Response;

/** Bounded, readable diagnostics. Only request settings are shown, never message/file bodies. */
final class ApiFailure {
 static String http(Response r,String protocol,JSONObject sent,String secret){
  String raw="";try{if(r.body()!=null)raw=r.peekBody(32768).string();}catch(Exception ignored){}
  String detail;
  try{detail=provider(new JSONObject(raw));}
  catch(Exception notJson){detail=raw.replaceAll("(?is)<script[^>]*>.*?</script>"," ").replaceAll("(?s)<[^>]*>"," ").replaceAll("\\s+"," ").trim();if(detail.isEmpty())detail="服务端未提供错误正文";detail=shorten(detail,1200);}
  String requestId="";
  for(String header:new String[]{"x-request-id","request-id","x-goog-request-id","cf-ray"}){String id=r.header(header);if(id!=null&&!id.isEmpty()){requestId=header+": "+shorten(id,160);break;}}
  String result="接口错误（"+r.code()+"） · "+status(r.code())+"\n"+detail;
  if(!requestId.isEmpty())result+="\n请求 ID："+requestId;
  String settings=settings(protocol,sent);if(!settings.isEmpty())result+="\n本次请求："+settings;
  return safe(result,secret);
 }
 static String provider(JSONObject root){
  JSONObject response=root.optJSONObject("response");if(response!=null)root=response;
  Object value=root.opt("error");if(value==null||value==JSONObject.NULL)value=root;
  StringBuilder out=new StringBuilder("provider_error：");
  if(value instanceof JSONObject){
   JSONObject e=(JSONObject)value;String message=e.optString("message","");out.append(message.isEmpty()?"服务端拒绝或未能完成请求":shorten(message,1800));
   for(String k:new String[]{"code","type","param","status"}){Object v=e.opt(k);if(v!=null&&v!=JSONObject.NULL&&!(v instanceof JSONObject)&&!(v instanceof JSONArray))out.append("\n").append(k).append("：").append(shorten(String.valueOf(v),240));}
   JSONArray details=e.optJSONArray("details");
   if(details!=null)for(int i=0;i<Math.min(6,details.length());i++){
    JSONObject d=details.optJSONObject(i);if(d==null)continue;JSONArray fields=d.optJSONArray("fieldViolations");if(fields!=null)for(int j=0;j<Math.min(6,fields.length());j++){JSONObject f=fields.optJSONObject(j);if(f!=null)out.append("\n字段 ").append(shorten(f.optString("field"),160)).append("：").append(shorten(f.optString("description"),500));}
    for(String k:new String[]{"reason","message","description"})if(d.has(k))out.append("\n").append(k).append("：").append(shorten(d.optString(k),500));
   }
  }else out.append(shorten(String.valueOf(value),1800));
  return safe(out.toString(),"");
 }
 static String settings(String protocol,JSONObject body){
  StringBuilder out=new StringBuilder(protocol==null?"":protocol);if(body==null)return out.toString();
  for(String key:new String[]{"model","max_tokens","max_completion_tokens","max_output_tokens","temperature","top_p","reasoning_effort"})append(out,key,body.opt(key));
  JSONObject reasoning=body.optJSONObject("reasoning");if(reasoning!=null)append(out,"reasoning.effort",reasoning.opt("effort"));
  JSONObject thinking=body.optJSONObject("thinking");if(thinking!=null){append(out,"thinking.type",thinking.opt("type"));append(out,"thinking.budget_tokens",thinking.opt("budget_tokens"));}
  JSONObject output=body.optJSONObject("output_config");if(output!=null)append(out,"output_config.effort",output.opt("effort"));
  JSONObject config=body.optJSONObject("generationConfig");if(config!=null){
   for(String key:new String[]{"maxOutputTokens","temperature","topP"})append(out,key,config.opt(key));
   JSONObject tc=config.optJSONObject("thinkingConfig");if(tc!=null)for(String key:new String[]{"thinkingLevel","thinkingBudget"})append(out,key,tc.opt(key));
  }
  if(body.has("stream_options"))append(out,"stream_options.include_usage",true);
  if(body.has("tools"))append(out,"tools","web_search");
  return out.toString();
 }
 private static void append(StringBuilder b,String key,Object value){if(value==null||value==JSONObject.NULL||value instanceof JSONObject||value instanceof JSONArray)return;if(b.length()>0)b.append(" · ");b.append(key).append('=').append(shorten(String.valueOf(value),160));}
 static String local(Throwable error){String message=error.getMessage();return safe(error.getClass().getSimpleName()+(message==null||message.trim().isEmpty()?"":": "+message),"");}
 static String safe(String text,String secret){
  String s=text==null?"未知错误":text;
  if(secret!=null&&!secret.isEmpty())s=s.replace(secret,"[已隐藏密钥]");
  s=s.replaceAll("(?i)Bearer\\s+[^\\s,;\\\"'<>]+","Bearer [已隐藏密钥]")
   .replaceAll("(?i)([\\\"']?(?:api[_-]?key|authorization|access[_-]?token|x-api-key)[\\\"']?\\s*[:=]\\s*[\\\"']?)[^\\s,;\\\"'<>]+","$1[已隐藏密钥]")
   .replaceAll("\\bsk-[A-Za-z0-9_-]{8,}","[已隐藏密钥]")
   .replaceAll("[\\p{Cc}&&[^\\n\\t]]"," ");
  return shorten(s,6000);
 }
 private static String status(int code){switch(code){case 400:case 422:return "参数或协议不被接受，请核对下方字段与模型能力";case 401:return "身份验证失败，请检查 API Key";case 403:return "无访问权限，请检查模型权限和区域限制";case 404:return "模型或接口路径不存在";case 413:return "请求或附件过大";case 429:return "额度不足或请求过于频繁";default:return code>=500?"服务端暂时异常，可稍后重试":"请求未成功";}}
 private static String shorten(String s,int max){return s.length()<=max?s:s.substring(0,max)+"…";}
 private ApiFailure(){}
}
