package com.changyu.ochat;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DeepSeekDocumentInputRegressionTest {
 private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
 private File pdf(boolean text)throws Exception{
  File file=File.createTempFile("deepseek-doc-",".pdf",context.getCacheDir());PdfDocument document=new PdfDocument();
  try(OutputStream output=new FileOutputStream(file)){
   for(int i=1;i<=2;i++){
    PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(400,500,i).create());
    if(text){Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);ink.setTextSize(20);page.getCanvas().drawText("Document secret "+(314159+i),20,50,ink);page.getCanvas().drawText("中文附件测试",20,100,ink);}
    else{Paint ink=new Paint();ink.setColor(i==1?Color.CYAN:Color.MAGENTA);page.getCanvas().drawRect(20,20,380,480,ink);}
    document.finishPage(page);
   }document.writeTo(output);
  }finally{document.close();}
  return file;
 }
 private JSONArray history(File file,boolean legacy)throws Exception{
  Object data=legacy?"data:application/pdf;base64,"+android.util.Base64.encodeToString(read(file),android.util.Base64.NO_WRAP):new FileAttachmentData(file,"application/pdf",true);
  JSONObject attachment=new JSONObject().put("type","input_file").put("filename","report.pdf").put("file_data",data);
  return new JSONArray().put(new JSONObject().put("role","user").put("content",new JSONArray().put(new JSONObject().put("type","text").put("text","Read the document")).put(attachment)));
 }
 private byte[] read(File file)throws Exception{try(InputStream input=new FileInputStream(file);ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);return output.toByteArray();}}
 private JSONArray prepare(String model,JSONArray history,PdfPageFallback.Session files)throws Exception{return DeepSeekDocumentInput.prepare(context,"https://api.deepseek.com/v1",model,ChatApiClient.RESPONSES,history,files,()->true,status->{});}
 private String wire(JSONArray history,String model,String protocol)throws Exception{
  JSONObject body=new ChatApiClient().requestBody(model,history,.7,512,1,"default",protocol,SearchOptions.OFF);okio.Buffer buffer=new okio.Buffer();
  JsonRequestBody.create(body,okhttp3.MediaType.get("application/json; charset=utf-8")).writeTo(buffer);return buffer.readUtf8();
 }
 @Test public void proPdfRequiresRecognitionInsteadOfLocalTextExtraction()throws Exception{
  File pdf=pdf(true);
  try(PdfPageFallback.Session files=new PdfPageFallback.Session()){
   for(boolean legacy:new boolean[]{false,true})try{prepare("deepseek-v4-pro",history(pdf,legacy),files);fail("PDF requires opt-in OCR");}
   catch(IOException expected){assertTrue(expected.getMessage().contains("文档识别"));assertTrue(expected.getMessage().contains("未发送"));}
  }finally{pdf.delete();}
 }
 @Test public void flashGetsEveryPdfPageAsActualImageDataBeforeTheFirstRequest()throws Exception{
  File pdf=pdf(false);List<File> pages=new ArrayList<>();
  try(PdfPageFallback.Session files=new PdfPageFallback.Session()){
   JSONArray prepared=prepare("deepseek-flash",history(pdf,false),files);JSONArray content=prepared.getJSONObject(0).getJSONArray("content");int images=0;
   for(int i=0;i<content.length();i++){JSONObject part=content.getJSONObject(i);if(part.optJSONObject("image_url")!=null){images++;FileAttachmentData image=(FileAttachmentData)part.getJSONObject("image_url").get("url");pages.add(image.file);assertTrue(image.file.isFile());}}
   assertEquals(2,images);String body=wire(prepared,"deepseek-flash",ChatApiClient.RESPONSES);assertFalse(body.contains("input_file"));assertTrue(body.contains("input_image"));assertTrue(body.contains("data:image/jpeg;base64,"));assertTrue(body.contains("第 2 / 2 页"));assertFalse(body.contains("本地附件数据"));
  }finally{pdf.delete();}
  for(File page:pages)assertFalse("converted pages must be cleaned",page.exists());
 }
 @Test public void scanForProFailsClearlyInsteadOfCallingAModelWithoutDocumentContent()throws Exception{
  File pdf=pdf(false);
  try(PdfPageFallback.Session files=new PdfPageFallback.Session()){
   try{prepare("deepseek-v4-pro",history(pdf,false),files);fail("scan cannot be sent to text-only model");}
   catch(IOException expected){assertTrue(expected.getMessage().contains("文档识别"));assertTrue(expected.getMessage().contains("未发送"));}
  }finally{pdf.delete();}
 }
 @Test public void historicalUnreadableScanIsExplicitAndDoesNotPreventANewTextQuestion()throws Exception{
  File pdf=pdf(false);
  try(PdfPageFallback.Session files=new PdfPageFallback.Session()){
   JSONArray original=history(pdf,false);original.put(new JSONObject().put("role","assistant").put("content","Earlier answer")).put(new JSONObject().put("role","user").put("content","New text question"));
   JSONArray prepared=prepare("deepseek-v4-pro",original,files);String body=wire(prepared,"deepseek-v4-pro",ChatApiClient.RESPONSES);
   assertTrue(body.contains("历史附件未发送"));assertTrue(body.contains("New text question"));assertFalse(body.contains("input_file"));assertTrue(PdfPageFallback.hasPdf(original));
  }finally{pdf.delete();}
 }
 @Test public void legacyImageFileUsesImageInputAndUnknownBinaryFilesAreRejected()throws Exception{
  Bitmap image=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888);ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  try{image.compress(Bitmap.CompressFormat.PNG,100,bytes);}finally{image.recycle();}
  String data="data:image/png;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
  JSONObject file=new JSONObject().put("type","input_file").put("filename","picture.png").put("file_data",data);
  JSONArray original=new JSONArray().put(new JSONObject().put("role","user").put("content",new JSONArray().put(file)));
  try(PdfPageFallback.Session files=new PdfPageFallback.Session()){
   String body=wire(prepare("deepseek-flash",original,files),"deepseek-flash",ChatApiClient.RESPONSES);assertTrue(body.contains(data));assertTrue(body.contains("input_image"));assertFalse(body.contains("input_file"));
   try{prepare("deepseek-v4-pro",original,files);fail("a text-only model cannot read an image file");}catch(IOException expected){assertTrue(expected.getMessage().contains("deepseek-flash"));}
   file.put("filename","binary.bin").put("file_data","data:application/octet-stream;base64,AQID");
   try{prepare("deepseek-flash",original,files);fail("unknown binary must not become unreadable text");}catch(IOException expected){assertTrue(expected.getMessage().contains("原文件类型"));}
  }
 }
 @Test public void otherHostsKeepTheirPdfAndCancellationDoesNotBecomeAnAttachmentNote()throws Exception{
  File pdf=pdf(true);
  try(PdfPageFallback.Session files=new PdfPageFallback.Session()){
   JSONArray original=history(pdf,false);assertSame(original,DeepSeekDocumentInput.prepare(context,"https://proxy.example.org","deepseek-v4-pro",ChatApiClient.RESPONSES,original,files,()->true,status->{}));
   try{DeepSeekDocumentInput.prepare(context,"https://api.deepseek.com","deepseek-v4-pro",ChatApiClient.RESPONSES,original,files,()->false,status->{});fail("cancelled conversion must stop");}
   catch(InterruptedIOException expected){}
  }finally{pdf.delete();}
 }
}
