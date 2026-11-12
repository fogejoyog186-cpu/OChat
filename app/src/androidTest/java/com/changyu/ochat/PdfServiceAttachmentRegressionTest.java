package com.changyu.ochat;

import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Base64;
import androidx.core.content.FileProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Exercises persisted PDF attachments through the real foreground service preparation path. */
@RunWith(AndroidJUnit4.class)
public class PdfServiceAttachmentRegressionTest {
 private static final int URI_ONLY=0,SNAPSHOT=1,MISSING_CURRENT_FILE=2,PDF_REJECTED=3,IMAGES_REJECTED=4,UNAUTHORIZED=5,PARTIAL=6;
 private static final class CaptureTransport extends ChatApiClient {
  volatile int requests,mode,images;volatile JSONArray sent;final List<File> pageFiles=new ArrayList<>();
  @Override public void streamChat(String base,String key,String model,JSONArray history,double temperature,int max,double top,String effort,String protocol,SearchOptions search,StreamCallback callback){
   sent=history;requests++;
   if(mode>=PDF_REJECTED&&requests==1){if(mode==PARTIAL)callback.onDelta("partial");callback.onError("接口错误（"+(mode==UNAUTHORIZED?"401":"400")+"） · Unsupported Document");return;}
   if(mode==IMAGES_REJECTED){callback.onError("接口错误（400） · images unsupported");return;}
   if(mode==PDF_REJECTED)for(int i=0;i<history.length();i++){JSONArray parts=history.optJSONObject(i).optJSONArray("content");for(int j=0;parts!=null&&j<parts.length();j++){JSONObject p=parts.optJSONObject(j);if(p!=null&&p.optJSONObject("image_url")!=null){Object data=p.optJSONObject("image_url").opt("url");if(data instanceof FileAttachmentData){images++;pageFiles.add(((FileAttachmentData)data).file);}}}}
   callback.onDelta("parsed image PDF");callback.onDone();
  }
 }
 @Test public void unsupportedPdfRetriesAsImagesExactlyOnce()throws Exception{exercise(PDF_REJECTED);}
 @Test public void unsupportedImagesRetainBothErrorsAndStopRetrying()throws Exception{exercise(IMAGES_REJECTED);}
 @Test public void authenticationErrorNeverTriggersDocumentRetry()throws Exception{exercise(UNAUTHORIZED);}
 @Test public void partialOutputNeverTriggersDocumentRetry()throws Exception{exercise(PARTIAL);}
 @Test public void imagePdfWithoutSnapshotOrPreparedMapIsActuallySent()throws Exception{exercise(URI_ONLY);}
 @Test public void persistedSnapshotSurvivesMissingOriginalAndEmptyPreparedMap()throws Exception{exercise(SNAPSHOT);}
 @Test public void missingCurrentPdfStopsBeforeCallingModelWithExplicitError()throws Exception{exercise(MISSING_CURRENT_FILE);}

 private void exercise(int mode)throws Exception{
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  SharedPreferences prefs=context.getSharedPreferences("chat",Context.MODE_PRIVATE);
  SessionRepository repository=SessionRepository.get(prefs);repository.flush();
  assertNull("device regression must not interrupt an existing generation",ChatGenerationService.active());
  Map<String,?> backup=new HashMap<>(prefs.getAll());
  File original=null;JSONObject descriptor=null;ActivityScenario<MainActivity> scenario=null;
  ChatGenerationService.Run[] request={null};CaptureTransport transport=new CaptureTransport();transport.mode=mode;
  try{
   File directory=new File(context.getFilesDir(),"imported_attachments");assertTrue(directory.isDirectory()||directory.mkdirs());
   original=File.createTempFile("pdf-service-", ".pdf",directory);byte[] pdf=writeImagePdf(original);
   Uri uri=FileProvider.getUriForFile(context,context.getPackageName()+".attachments",original);
   descriptor=AttachmentBatch.descriptor(context.getContentResolver(),uri).put("displayName","图片扫描.pdf");
   assertFalse(descriptor.has("payloadSnapshot"));assertFalse(AttachmentSnapshots.has(descriptor));
   String payload="data:application/pdf;base64,"+Base64.encodeToString(pdf,Base64.NO_WRAP);
   if(mode==SNAPSHOT){
    assertTrue("fixture must exercise the file-backed snapshot path",payload.length()>AttachmentSnapshots.INLINE_CHARS);
    AttachmentSnapshots.capture(context,descriptor,payload);assertTrue(AttachmentSnapshots.has(descriptor));
    assertTrue(descriptor.has("payloadSnapshotFile"));assertFalse(descriptor.has("payloadSnapshot"));
   }
   String sessionId="pdf-service-"+UUID.randomUUID(),messageId="pdf-user-"+UUID.randomUUID();
   JSONObject message=new JSONObject().put("id",messageId).put("user",true).put("text","Read the picture in this PDF.")
    .put("timestamp",System.currentTimeMillis()).put("attachments",new JSONArray().put(descriptor));
   JSONObject session=new JSONObject().put("id",sessionId).put("title","PDF service regression").put("messages",new JSONArray().put(message));
   prefs.edit().clear().putString("sessions",new JSONArray().put(session).toString()).putString("currentSessionId",sessionId).commit();
   if(mode==SNAPSHOT||mode==MISSING_CURRENT_FILE)assertTrue(original.delete());
   // Deserialize the saved conversation rather than passing the in-memory descriptor through.
   JSONObject restored=MessageEdits.findSession(new JSONArray(prefs.getString("sessions","[]")),sessionId);
   JSONObject restoredDescriptor=restored.getJSONArray("messages").getJSONObject(0).getJSONArray("attachments").getJSONObject(0);
   descriptor=restoredDescriptor;
   if(mode==SNAPSHOT)assertEquals(payload,AttachmentSnapshots.read(context,restoredDescriptor));
   else assertFalse(AttachmentSnapshots.has(restoredDescriptor));
   scenario=ActivityScenario.launch(MainActivity.class);
   scenario.onActivity(activity->{
    try{
     ChatGenerationService.Run run=new ChatGenerationService.Run(sessionId,"gemini-fixture","default",System.currentTimeMillis());
     run.client=transport;run.base="https://unused.invalid";run.key="fixture";run.protocol=ChatApiClient.GEMINI;
     run.outputTokens="512";run.session=restored;run.branchId=restored.optString("activeBranchId");
     run.prepared=Collections.emptyMap();run.history=ChatGenerationService.history(restored,"");request[0]=run;
     ChatGenerationService.launch(activity,run);
    }catch(Exception failure){throw new AssertionError(failure);}
   });
   awaitFinished(request[0]);repository.flush();
   if(mode==MISSING_CURRENT_FILE){
    assertEquals("an unreadable current document must not degrade to a text-only model call",0,transport.requests);
    assertTrue(request[0].error);assertTrue(request[0].partial.toString(),request[0].partial.toString().contains("附件"));
    assertTrue(request[0].partial.toString(),request[0].partial.toString().contains("未发送"));
    assertTrue(request[0].partial.toString(),request[0].partial.toString().contains("图片扫描.pdf"));
   }else if(mode>=PDF_REJECTED){
    assertEquals(mode==PDF_REJECTED||mode==IMAGES_REJECTED?2:1,transport.requests);
    if(mode==PDF_REJECTED){assertFalse(request[0].partial.toString(),request[0].error);assertEquals(1,transport.images);for(File page:transport.pageFiles)assertFalse("temporary pages must be removed",page.exists());}
    else if(mode==PARTIAL){assertFalse(request[0].error);assertTrue(request[0].stopped);assertEquals("partial",request[0].partial.toString());}
    else {assertTrue(request[0].error);assertTrue(request[0].partial.toString().contains("Unsupported Document"));if(mode==IMAGES_REJECTED)assertTrue(request[0].partial.toString().contains("images unsupported"));}
   }else{
    assertFalse(request[0].partial.toString(),request[0].error);assertEquals(1,transport.requests);assertNotNull(transport.sent);
    assertPdfBytes(transport.sent,pdf);
    JSONObject saved=MessageEdits.findSession(new JSONArray(prefs.getString("sessions","[]")),sessionId);
    assertNotNull(saved);
    JSONObject savedUser=saved.getJSONArray("messages").getJSONObject(0);
    if(mode==SNAPSHOT){
     JSONObject savedFile=savedUser.getJSONArray("attachments").getJSONObject(0);
     assertTrue(AttachmentSnapshots.has(savedFile));assertEquals(payload,AttachmentSnapshots.read(context,savedFile));
    }
   }
  }finally{
   if(request[0]!=null&&!request[0].finished){ChatGenerationService.stop(context,request[0]);awaitFinished(request[0]);}
   if(scenario!=null)scenario.close();
   repository.flush();restore(prefs,backup);
   if(descriptor!=null)AttachmentSnapshots.discard(context,descriptor);
   if(original!=null)original.delete();
  }
 }
 private void assertPdfBytes(JSONArray history,byte[] expected)throws Exception{
  assertEquals(1,history.length());JSONArray content=history.getJSONObject(0).getJSONArray("content");
  assertEquals("the service must keep both the question and PDF block",2,content.length());
  JSONObject part=content.getJSONObject(1);assertEquals("file",part.getString("type"));
  okio.Buffer wire=new okio.Buffer();JsonRequestBody.create(part,okhttp3.MediaType.get("application/json; charset=utf-8")).writeTo(wire);
  String data=new JSONObject(wire.readUtf8()).getJSONObject("file").getString("file_data");assertTrue(data.startsWith("data:application/pdf;base64,"));
  assertArrayEquals("the service silently skipped or changed the image-bearing PDF",expected,Base64.decode(data.substring(data.indexOf(',')+1),Base64.DEFAULT));
 }
 private byte[] writeImagePdf(File target)throws Exception{
  final int width=1024,height=512;int[] pixels=new int[width*height];Random random=new Random(7919);
  for(int i=0;i<pixels.length;i++)pixels[i]=0xff000000|random.nextInt(0x1000000);
  Bitmap image=Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888);PdfDocument document=new PdfDocument();
  try{
   PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(1024,700,1).create());
   page.getCanvas().drawBitmap(image,0,50,null);document.finishPage(page);
   try(FileOutputStream out=new FileOutputStream(target)){document.writeTo(out);}
  }finally{document.close();image.recycle();}
  assertTrue("fixture must stay below the old 3 MiB limit",target.length()<3*1024*1024);
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  try(InputStream input=new FileInputStream(target)){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);}
  return bytes.toByteArray();
 }
 private void awaitFinished(ChatGenerationService.Run run)throws Exception{
  assertNotNull(run);long deadline=SystemClock.uptimeMillis()+10000;
  while(!run.finished&&SystemClock.uptimeMillis()<deadline)Thread.sleep(20);
  InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertTrue("foreground service did not finish",run.finished);
 }
 private void restore(SharedPreferences prefs,Map<String,?> values){
  SharedPreferences.Editor editor=prefs.edit().clear();
  for(Map.Entry<String,?> entry:values.entrySet()){
   Object value=entry.getValue();String key=entry.getKey();
   if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
   else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);
   else if(value instanceof Float)editor.putFloat(key,(Float)value);else if(value instanceof Set)editor.putStringSet(key,new HashSet<>((Set<String>)value));
  }
  editor.commit();
 }
}
