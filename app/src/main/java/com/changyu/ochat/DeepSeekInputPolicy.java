package com.changyu.ochat;

import okhttp3.HttpUrl;
import org.json.*;

/** Documented capabilities of the official service, never inferred for third-party hosts. */
final class DeepSeekInputPolicy {
 static boolean applies(String base,String protocol){
  if(!ChatApiClient.RESPONSES.equals(protocol)&&!ChatApiClient.CHAT_COMPLETIONS.equals(protocol)&&!ChatApiClient.ANTHROPIC.equals(protocol))return false;
  HttpUrl url=base==null?null:HttpUrl.parse(base);
  return url!=null&&"api.deepseek.com".equals(url.host());
 }
 static boolean images(String model){
  String id=ModelCapabilities.canonical(model);
  return id.equals("deepseek-flash")||id.equals("deepseek-v4-flash")||id.equals("deepseek-v4-flash-vision-exp");
 }
 static String imageError(String model){
  return "DeepSeek 官方模型 "+model+" 未提供已核实的图像输入支持。图片或扫描 PDF 请改用 deepseek-flash；当前模型只能接收提取出的文字。";
 }
 static void validate(String base,String model,String protocol,JSONArray history){
  if(!applies(base,protocol))return;
  for(int i=0;i<history.length();i++){
   JSONObject message=history.optJSONObject(i);JSONArray parts=message==null?null:message.optJSONArray("content");
   for(int j=0;parts!=null&&j<parts.length();j++){
    JSONObject part=parts.optJSONObject(j);String type=part==null?"":part.optString("type");
    if(type.equals("file")||type.equals("input_file"))throw new IllegalArgumentException("DeepSeek 官方接口不支持原文件输入；附件必须先转换为文字，或使用 deepseek-flash 接收 PDF 页面图像。文件未发送。");
    if((type.equals("image_url")||type.equals("input_image"))&&!images(model))throw new IllegalArgumentException(imageError(model));
   }
  }
 }
 private DeepSeekInputPolicy(){}
}
