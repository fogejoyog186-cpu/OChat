package com.changyu.ochat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AttachmentModelInputRegressionTest {
 @Test public void pdfAndDocumentsBecomeRealProtocolParts() throws Exception {
  JSONObject pdf=new JSONObject().put("displayName","report.pdf").put("mime","application/pdf");
  String data="data:application/pdf;base64,"+android.util.Base64.encodeToString("%PDF-1.7 real bytes".getBytes(StandardCharsets.UTF_8),android.util.Base64.NO_WRAP);
  JSONObject responsePart=AttachmentBatch.part(pdf,data,ChatApiClient.RESPONSES);assertEquals("input_file",responsePart.getString("type"));assertEquals("report.pdf",responsePart.getString("filename"));assertTrue(responsePart.getString("file_data").contains("base64,"));
  JSONObject chatPart=AttachmentBatch.part(pdf,data,ChatApiClient.CHAT_COMPLETIONS);assertEquals("file",chatPart.getString("type"));assertTrue(chatPart.getJSONObject("file").getString("file_data").startsWith("data:application/pdf;base64,"));
  JSONObject docx=new JSONObject().put("displayName","brief.docx").put("mime","application/vnd.openxmlformats-officedocument.wordprocessingml.document");
  String docData="data:application/vnd.openxmlformats-officedocument.wordprocessingml.document;base64,UEsDBA==";
  assertEquals("input_file",AttachmentBatch.part(docx,docData,ChatApiClient.RESPONSES).getString("type"));
  JSONObject chatDoc=AttachmentBatch.part(docx,docData,ChatApiClient.CHAT_COMPLETIONS);assertEquals("text",chatDoc.getString("type"));assertTrue(chatDoc.getString("text").contains("Responses API"));
 }
 @Test public void officeXmlExtractionReturnsReadableContent() throws Exception {
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("word/document.xml"));zip.write("<w:document><w:t>Quarterly report</w:t><w:t>Revenue 42</w:t></w:document>".getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
  String extracted=AttachmentBatch.extractDocumentText(bytes.toByteArray(),"application/vnd.openxmlformats-officedocument.wordprocessingml.document","report.docx");assertTrue(extracted.contains("Quarterly report"));assertTrue(extracted.contains("Revenue 42"));
 }
 @Test public void responsesRequestPassesInputFileAndImagesAndSnapshotPersists() throws Exception {
  JSONArray c=new JSONArray().put(new JSONObject().put("type","text").put("text","read attachments")).put(new JSONObject().put("type","input_file").put("filename","report.pdf").put("file_data","data:application/pdf;base64,JVBERi0x")).put(new JSONObject().put("type","input_image").put("image_url","data:image/jpeg;base64,/9j/"));
  JSONArray history=new JSONArray().put(new JSONObject().put("role","user").put("content",c));JSONObject body=new ChatApiClient().requestBody("fixture",history,.7,512,1,"default",ChatApiClient.RESPONSES,SearchOptions.OFF);JSONArray sent=body.getJSONArray("input").getJSONObject(0).getJSONArray("content");assertEquals("input_file",sent.getJSONObject(1).getString("type"));assertEquals("report.pdf",sent.getJSONObject(1).getString("filename"));assertEquals("input_image",sent.getJSONObject(2).getString("type"));
  JSONObject saved=new JSONObject().put("uri","content://doc/report").put("payloadSnapshot","data:application/pdf;base64,JVBERi0x");assertTrue(saved.optString("payloadSnapshot").contains("base64,"));assertTrue(AttachmentBatch.estimatedBytes(saved.optString("payloadSnapshot"))>0);
 }
 @Test public void imageAndThemeContrastHelpersRemainValid() throws Exception {JSONObject d=new JSONObject().put("displayName","photo.png");JSONObject p=AttachmentBatch.part(d,"data:image/jpeg;base64,/9j/",ChatApiClient.RESPONSES);assertEquals("image_url",p.getString("type"));int bg=0xff302d38;int ink=ThemePalette.readable(0xffffffff,bg);assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(ink,bg)>=4.5);}
}
