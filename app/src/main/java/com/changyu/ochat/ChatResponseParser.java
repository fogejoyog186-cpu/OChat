package com.changyu.ochat;
import org.json.*;import java.io.IOException;
/** Null-safe parser for both protocols; never exposes reasoning or provider secrets. */
final class ChatResponseParser {
 static String parse(JSONObject r,boolean topic)throws IOException{
  if(r.has("error")&&!r.isNull("error"))throw new IOException(ApiFailure.provider(r));
  String status=r.optString("status","");if("failed".equals(status))throw new IOException(ApiFailure.provider(r));
  String result="";JSONArray choices=r.optJSONArray("choices");JSONObject choice=choices==null?null:choices.optJSONObject(0);JSONObject msg=choice==null?null:choice.optJSONObject("message");
  if(msg!=null){if(hasRefusal(msg))throw new IOException("refusal：模型拒绝生成标题，请调整提示词或模型");result=text(msg.opt("content"));if(topic&&result.trim().isEmpty()){String rs=text(msg.opt("reasoning_content"));if(!rs.trim().isEmpty())return cleanTitle(rs,true);}}
  if(result.trim().isEmpty())result=text(r.opt("output_text"));JSONArray output=r.optJSONArray("output");if(result.trim().isEmpty()&&output!=null){StringBuilder b=new StringBuilder();for(int i=0;i<output.length();i++){JSONObject item=output.optJSONObject(i);if(item==null||"reasoning".equals(item.optString("type")))continue;if(hasRefusal(item))throw new IOException("refusal：模型拒绝生成标题，请调整提示词或模型");b.append(text(item.opt("content")));}result=b.toString();}
  if(result.trim().isEmpty())result=text(r.opt("content"));
  if(result.trim().isEmpty()&&hasRefusal(r))throw new IOException("refusal：模型拒绝生成标题，请调整提示词或模型");
  if(result.trim().isEmpty()&&("incomplete".equals(status)||"length".equals(choice==null?"":choice.optString("finish_reason"))))throw new IOException("token_limit：输出预算耗尽，请提高预算");
  if(choice!=null&&choice.has("delta")&&msg==null)throw new IOException("unexpected_delta：非流式请求收到 delta");
  if(result.trim().isEmpty()&&msg!=null&&msg.has("content")&&!msg.isNull("content")&&!(msg.opt("content") instanceof String))throw new IOException("unsupported_content：回复包含不支持的内容块");
  if(result.trim().isEmpty())throw new IOException("no_text：回复没有可用文本");return topic?cleanTitle(result,false):result.trim();
 }
 static boolean hasRefusal(Object v){if(v instanceof JSONObject){JSONObject o=(JSONObject)v;return "refusal".equals(o.optString("type"))||!text(o.opt("refusal")).isEmpty()||hasRefusal(o.opt("content"));}if(v instanceof JSONArray){JSONArray a=(JSONArray)v;for(int i=0;i<a.length();i++)if(hasRefusal(a.opt(i)))return true;}return false;}
 static String text(Object v){if(v instanceof String)return(String)v;if(v instanceof JSONArray){StringBuilder b=new StringBuilder();for(int i=0;i<((JSONArray)v).length();i++)b.append(text(((JSONArray)v).opt(i)));return b.toString();}if(v instanceof JSONObject){JSONObject o=(JSONObject)v;String type=o.optString("type","");if(!type.isEmpty()&&!type.equals("text")&&!type.equals("output_text"))return"";for(String k:new String[]{"text","value","content"}){String s=text(o.opt(k));if(!s.isEmpty())return s;}}return"";}
 static String cleanTitle(String v,boolean reasoning)throws IOException{String s=v.replaceAll("(?is)<think>.*?</think>","").trim();if(s.contains("<think>"))throw new IOException("reasoning_only：模型只返回了未完成的思考");java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?im)^(?:最终标题|标题|final title|title)[:：]\\s*(.+)$").matcher(s);boolean explicit=false;while(m.find()){s=m.group(1).trim();explicit=true;}if(reasoning&&!explicit&&(s.length()>60||s.contains("\n")))throw new IOException("reasoning_only：模型只有思考文本，未找到明确标题");s=s.replaceAll("[\\\"'「」#*`]","").trim();if(s.isEmpty())throw new IOException("blank_title：标题为空");s=s.split("\\R",2)[0].trim();return s.substring(0,s.offsetByCodePoints(0,Math.min(30,s.codePointCount(0,s.length()))));}
}