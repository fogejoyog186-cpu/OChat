package com.changyu.ochat;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class AttachmentTextPartTest {
 private JSONObject descriptor(String mime,String name)throws Exception{return new JSONObject().put("mime",mime).put("displayName",name);}
 @Test public void yamlAndPlainTextBeginningWithDataRemainUnchanged()throws Exception{
  JSONObject file=descriptor("text/plain","config.yaml");
  for(String text:new String[]{"data:\n  title: 文档","data: value, still ordinary text","data:image/png;notbase64,plain"}){
   JSONObject part=AttachmentBatch.part(file,text,ChatApiClient.RESPONSES);
   assertEquals("text",part.getString("type"));assertEquals(text,part.getString("text"));
   assertEquals(text.getBytes(StandardCharsets.UTF_8).length,AttachmentBatch.estimatedBytes(text));
   assertEquals(text.getBytes(StandardCharsets.UTF_8).length,AttachmentBatch.estimatedBytes(file,text));
  }
 }
 @Test public void textContainingACompleteDataUriIsNotAnImageOrFile()throws Exception{
  String data="data:image/png;base64,aGVsbG8=";
  for(JSONObject file:new JSONObject[]{descriptor("text/plain","example.txt"),descriptor("application/json","example.json"),descriptor("image/svg+xml","example.svg"),descriptor("application/vnd.openxmlformats-officedocument.wordprocessingml.document","example.docx")}){
   JSONObject part=AttachmentBatch.part(file,data,ChatApiClient.RESPONSES);
   assertEquals("text",part.getString("type"));assertEquals(data,part.getString("text"));
   assertEquals(data.getBytes(StandardCharsets.UTF_8).length,AttachmentBatch.estimatedBytes(file,data));
  }
 }
 @Test public void dataPrefixedTextSurvivesAllProtocolConversions()throws Exception{
  String data="data:\n  name: 测试";JSONObject file=descriptor("text/plain","config.yaml");
  for(String protocol:new String[]{ChatApiClient.CHAT_COMPLETIONS,ChatApiClient.RESPONSES,ChatApiClient.ANTHROPIC,ChatApiClient.GEMINI}){
   JSONArray content=AttachmentBatch.content("read this",Collections.singletonList(file),Collections.singletonList(data),protocol);
   JSONObject request=new ChatApiClient().requestBody("fixture",new JSONArray().put(new JSONObject().put("role","user").put("content",content)),.5,256,1,"default",protocol,SearchOptions.OFF);
   JSONArray parts=ChatApiClient.GEMINI.equals(protocol)?request.getJSONArray("contents").getJSONObject(0).getJSONArray("parts"):
    request.getJSONArray(ChatApiClient.RESPONSES.equals(protocol)?"input":"messages").getJSONObject(0).getJSONArray("content");
   assertEquals(data,parts.getJSONObject(1).getString("text"));
  }
 }
 @Test public void pdfMislabelledAsTextAndLegacyOfficeFileRemainBinary()throws Exception{
  String pdf="data:application/pdf;base64,JVBERi0x";JSONObject pdfFile=descriptor("text/plain","scan.pdf");
  assertEquals("file",AttachmentBatch.part(pdfFile,pdf,ChatApiClient.CHAT_COMPLETIONS).getString("type"));
  assertEquals(6,AttachmentBatch.estimatedBytes(pdfFile,pdf));
  String mime="application/vnd.openxmlformats-officedocument.wordprocessingml.document",docx="data:"+mime+";base64,UEsDBA==";
  JSONObject docxFile=descriptor(mime,"brief.docx");
  assertEquals("input_file",AttachmentBatch.part(docxFile,docx,ChatApiClient.RESPONSES).getString("type"));
  assertTrue(AttachmentBatch.part(docxFile,docx,ChatApiClient.CHAT_COMPLETIONS).getString("text").contains("Responses API"));
  assertEquals(4,AttachmentBatch.estimatedBytes(docxFile,docx));
  JSONObject image=descriptor("image/png","photo.png");
  assertEquals("image_url",AttachmentBatch.part(image,"data:image/jpeg;base64,/9j/",ChatApiClient.RESPONSES).getString("type"));
 }
}
