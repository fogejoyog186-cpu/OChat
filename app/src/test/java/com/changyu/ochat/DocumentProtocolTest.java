package com.changyu.ochat;

import org.json.*;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.Assert.*;

public class DocumentProtocolTest {
 private static final String[] PROTOCOLS={ChatApiClient.CHAT_COMPLETIONS,ChatApiClient.RESPONSES,ChatApiClient.ANTHROPIC,ChatApiClient.GEMINI};
 private static final String PDF="data:application/pdf;base64,"+Base64.getEncoder().encodeToString("%PDF-1.7\n/Subtype /Image\noriginal bytes".getBytes(StandardCharsets.UTF_8));
 private JSONArray history(String shape,String data)throws Exception{
  JSONObject file=new JSONObject().put("filename","扫描图片.pdf").put("file_data",data);
  JSONObject part="file".equals(shape)?new JSONObject().put("type","file").put("file",file):file.put("type","input_file");
  return new JSONArray().put(new JSONObject().put("role","user").put("content",new JSONArray().put(new JSONObject().put("type","text").put("text","read every page")).put(part)));
 }
 private JSONObject body(String protocol,JSONArray history)throws Exception{return new ChatApiClient().requestBody("fixture",history,.7,512,1,"default",protocol,SearchOptions.OFF);}
 private JSONObject attachment(JSONObject body,String protocol)throws Exception{
  if(ChatApiClient.GEMINI.equals(protocol))return body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(1);
  return body.getJSONArray(ChatApiClient.RESPONSES.equals(protocol)?"input":"messages").getJSONObject(0).getJSONArray("content").getJSONObject(1);
 }
 @Test public void eitherOpenAiFileShapeRetainsPdfAcrossAllProtocols()throws Exception{
  for(String shape:new String[]{"file","input_file"})for(String protocol:PROTOCOLS){
   JSONArray original=history(shape,PDF);String before=original.toString();JSONObject part=attachment(body(protocol,original),protocol);
   String encoded;
   if(ChatApiClient.CHAT_COMPLETIONS.equals(protocol)){assertEquals("file",part.getString("type"));encoded=part.getJSONObject("file").getString("file_data");assertEquals("扫描图片.pdf",part.getJSONObject("file").getString("filename"));}
   else if(ChatApiClient.RESPONSES.equals(protocol)){assertEquals("input_file",part.getString("type"));encoded=part.getString("file_data");assertEquals("扫描图片.pdf",part.getString("filename"));}
   else if(ChatApiClient.ANTHROPIC.equals(protocol)){assertEquals("document",part.getString("type"));assertEquals("application/pdf",part.getJSONObject("source").getString("media_type"));encoded="data:application/pdf;base64,"+part.getJSONObject("source").getString("data");}
   else{assertEquals("application/pdf",part.getJSONObject("inlineData").getString("mimeType"));encoded="data:application/pdf;base64,"+part.getJSONObject("inlineData").getString("data");}
   assertEquals(protocol,PDF,encoded);assertEquals("request construction must not rewrite stored history",before,original.toString());
  }
 }
 @Test public void responsesImageShapeNormalizesForChatWithoutDroppingPdf()throws Exception{
  JSONArray history=history("input_file",PDF);history.getJSONObject(0).getJSONArray("content").put(new JSONObject().put("type","input_image").put("image_url","data:image/jpeg;base64,/9j/"));
  JSONArray sent=body(ChatApiClient.CHAT_COMPLETIONS,history).getJSONArray("messages").getJSONObject(0).getJSONArray("content");
  assertEquals(3,sent.length());assertEquals("image_url",sent.getJSONObject(2).getString("type"));assertEquals("data:image/jpeg;base64,/9j/",sent.getJSONObject(2).getJSONObject("image_url").getString("url"));
 }
 @Test public void malformedOrUnknownAttachmentsAreRejectedExplicitly()throws Exception{
  for(String protocol:PROTOCOLS){
   for(String data:new String[]{"data:application/pdf;base64,","data:application/pdf;utf8,YQ==","data:application/pdf;base64,Y?=="}){
    try{body(protocol,history("file",data));fail(protocol+" silently accepted malformed file data");}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("附件"));}
   }
   JSONArray history=history("file",PDF);history.getJSONObject(0).getJSONArray("content").put(new JSONObject().put("type","unsupported_document"));
   try{body(protocol,history);fail(protocol+" silently dropped an unsupported block");}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("unsupported_document"));}
  }
 }
 @Test public void chatRejectsNonPdfBinaryFileInsteadOfReplacingItWithText()throws Exception{
  try{body(ChatApiClient.CHAT_COMPLETIONS,history("input_file","data:application/zip;base64,UEsDBA=="));fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("PDF"));}
 }
}
