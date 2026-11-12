package com.changyu.ochat;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeepSeekInputPolicyTest {
 private JSONArray history(String type)throws Exception{
  return new JSONArray().put(new JSONObject().put("role","user").put("content",new JSONArray().put(new JSONObject().put("type",type))));
 }
 @Test public void capabilityOverrideIsRestrictedToTheOfficialHost(){
  for(String protocol:new String[]{ChatApiClient.RESPONSES,ChatApiClient.CHAT_COMPLETIONS,ChatApiClient.ANTHROPIC}){
   assertTrue(DeepSeekInputPolicy.applies("https://API.DEEPSEEK.COM/v1/responses",protocol));
   assertFalse(DeepSeekInputPolicy.applies("https://api.deepseek.com.example.org",protocol));
   assertFalse(DeepSeekInputPolicy.applies("https://api.deepseek.com@proxy.example.org",protocol));
   assertFalse(DeepSeekInputPolicy.applies("https://proxy.example.org/deepseek",protocol));
  }
  assertFalse(DeepSeekInputPolicy.applies("https://api.deepseek.com",ChatApiClient.GEMINI));
  assertFalse(DeepSeekInputPolicy.applies(null,ChatApiClient.RESPONSES));
  assertFalse(DeepSeekInputPolicy.applies("not a URL",ChatApiClient.RESPONSES));
 }
 @Test public void currentAndDocumentedLegacyFlashNamesSupportImages(){
  for(String id:new String[]{"deepseek-flash","deepseek-v4-flash","deepseek-v4-flash-vision-exp","DEEPSEEK-FLASH"})assertTrue(id,DeepSeekInputPolicy.images(id));
  for(String id:new String[]{"deepseek-v4-pro","deepseek-pro","deepseek-chat","deepseek-reasoner","deepseek-future",null})assertFalse(DeepSeekInputPolicy.images(id));
 }
 @Test public void unpreparedFilesFailInsteadOfReachingAnIgnoringService()throws Exception{
  for(String type:new String[]{"file","input_file"})try{
   DeepSeekInputPolicy.validate("https://api.deepseek.com","deepseek-flash",ChatApiClient.RESPONSES,history(type));fail("original files must not be silently ignored");
  }catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("不支持原文件"));assertTrue(expected.getMessage().contains("未发送"));}
 }
 @Test public void textOnlyModelsRejectImagesAndFlashAcceptsConvertedPages()throws Exception{
  for(String type:new String[]{"image_url","input_image"}){
   DeepSeekInputPolicy.validate("https://api.deepseek.com","deepseek-flash",ChatApiClient.RESPONSES,history(type));
   try{DeepSeekInputPolicy.validate("https://api.deepseek.com","deepseek-v4-pro",ChatApiClient.RESPONSES,history(type));fail("text-only model must not ignore images");}
   catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("deepseek-flash"));}
  }
 }
 @Test public void otherProvidersAndOrdinaryTextKeepTheirExistingInputs()throws Exception{
  DeepSeekInputPolicy.validate("https://proxy.example.org","deepseek-v4-pro",ChatApiClient.RESPONSES,history("input_file"));
  DeepSeekInputPolicy.validate("https://api.deepseek.com","deepseek-v4-pro",ChatApiClient.RESPONSES,history("text"));
 }
}
