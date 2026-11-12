package com.changyu.ochat;

/** User-facing base URL guidance matches the paths assembled by this client. */
final class ProtocolAddress {
 static String example(String protocol){
  if(ChatApiClient.ANTHROPIC.equals(protocol))return "https://api.anthropic.com";
  if(ChatApiClient.GEMINI.equals(protocol))return "https://generativelanguage.googleapis.com";
  return "https://api.openai.com/v1";
 }
 static boolean isDefault(String value){
  String base=value.trim().replaceAll("/+$","");
  return base.equals("https://api.openai.com")||base.equals("https://api.openai.com/v1")
   ||base.equals("https://api.anthropic.com")||base.equals("https://api.anthropic.com/v1")
   ||base.equals("https://generativelanguage.googleapis.com")||base.equals("https://generativelanguage.googleapis.com/v1beta");
 }
 static String hint(String protocol){
  if(ChatApiClient.ANTHROPIC.equals(protocol))return "推荐填写 "+example(protocol)+"\n应用自动补齐 /v1/messages，无需手动添加 /v1；已有版本路径或完整接口地址也可识别。";
  if(ChatApiClient.GEMINI.equals(protocol))return "推荐填写 "+example(protocol)+"\n应用自动补齐 /v1beta/models/模型ID:generateContent。此处使用 Gemini 原生地址，请勿混用 OpenAI 兼容地址。";
  return "OpenAI 官方地址需包含 /v1，例如 "+example(protocol)+"\n应用会追加 "+(ChatApiClient.RESPONSES.equals(protocol)?"/responses":"/chat/completions")+"；中转接口的版本路径请按供应商说明填写。";
 }
 private ProtocolAddress(){}
}
