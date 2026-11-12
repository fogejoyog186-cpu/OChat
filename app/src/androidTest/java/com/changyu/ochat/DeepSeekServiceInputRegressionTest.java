package com.changyu.ochat;

import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.SystemClock;
import androidx.core.content.FileProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

/** A success response without file support must not bypass document preparation. No real API calls. */
@RunWith(AndroidJUnit4.class)
public class DeepSeekServiceInputRegressionTest {
 private static final class AcceptingTransport extends ChatApiClient {
  volatile int requests;volatile String wire="",failure="";final List<File> images=new ArrayList<>();
  @Override public void streamChat(String base,String key,String model,JSONArray history,double temperature,int max,double top,String effort,String protocol,SearchOptions search,StreamCallback callback){
   requests++;
   try{
    DeepSeekInputPolicy.validate(base,model,protocol,history);
    for(int i=0;i<history.length();i++){
     JSONArray parts=history.getJSONObject(i).optJSONArray("content");
     for(int j=0;parts!=null&&j<parts.length();j++){
      JSONObject part=parts.getJSONObject(j),image=part.optJSONObject("image_url");
      if(image!=null&&image.opt("url") instanceof FileAttachmentData)images.add(((FileAttachmentData)image.get("url")).file);
     }
    }
    okio.Buffer output=new okio.Buffer();JsonRequestBody.create(requestBody(model,history,temperature,max,top,effort,protocol,search),okhttp3.MediaType.get("application/json; charset=utf-8")).writeTo(output);wire=output.readUtf8();
    callback.onDelta("accepted");callback.onDone();
   }catch(Exception invalid){failure=invalid.toString();callback.onError(failure);}
  }
 }
 @Test public void proPdfRequiresConfiguredRecognitionBeforeAnyRequest()throws Exception{exercise("deepseek-v4-pro",true);}
 @Test public void flashReceivesPdfImagesOnTheFirstSuccessfulResponsesRequest()throws Exception{exercise("deepseek-flash",false);}
 @Test public void proScanFailsBeforeAnyNetworkRequest()throws Exception{exercise("deepseek-v4-pro",false);}
 private void exercise(String model,boolean text)throws Exception{
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();SharedPreferences prefs=context.getSharedPreferences("chat",Context.MODE_PRIVATE);SessionRepository repository=SessionRepository.get(prefs);repository.flush();
  assertNull("test must not interrupt an existing generation",ChatGenerationService.active());Map<String,?> backup=new HashMap<>(prefs.getAll());
  File original=null;JSONObject descriptor=null;ActivityScenario<MainActivity> scenario=null;ChatGenerationService.Run[] run={null};AcceptingTransport transport=new AcceptingTransport();
  try{
   File directory=new File(context.getFilesDir(),"imported_attachments");assertTrue(directory.isDirectory()||directory.mkdirs());original=File.createTempFile("deepseek-service-",".pdf",directory);
   PdfDocument document=new PdfDocument();
   try(OutputStream output=new FileOutputStream(original)){
    for(int i=1;i<=2;i++){
     PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(400,500,i).create());Paint ink=new Paint();ink.setColor(Color.BLUE);ink.setTextSize(20);
     if(text)page.getCanvas().drawText("Document secret "+(314159+i),20,80,ink);else page.getCanvas().drawCircle(150,200,90,ink);document.finishPage(page);
    }document.writeTo(output);
   }finally{document.close();}
   Uri uri=FileProvider.getUriForFile(context,context.getPackageName()+".attachments",original);descriptor=AttachmentBatch.descriptor(context.getContentResolver(),uri);
   String id="deepseek-service-"+UUID.randomUUID();JSONObject message=new JSONObject().put("id","user-"+id).put("user",true).put("text","Read this document").put("attachments",new JSONArray().put(descriptor));
   JSONObject session=new JSONObject().put("id",id).put("title","DeepSeek document regression").put("messages",new JSONArray().put(message));
   prefs.edit().clear().putString("sessions",new JSONArray().put(session).toString()).putString("currentSessionId",id).commit();scenario=ActivityScenario.launch(MainActivity.class);
   scenario.onActivity(activity->{
    try{
     ChatGenerationService.Run request=new ChatGenerationService.Run(id,model,"default",System.currentTimeMillis());request.base="https://api.deepseek.com";request.key="fixture-key";request.protocol=ChatApiClient.RESPONSES;request.outputTokens="512";request.client=transport;request.session=session;request.history=ChatGenerationService.history(session,"");run[0]=request;
     ChatGenerationService.launch(activity,request);
    }catch(Exception error){throw new AssertionError(error);}
   });
   await(run[0]);repository.flush();
   if(model.equals("deepseek-v4-pro")){
    assertTrue(run[0].error);assertEquals(0,transport.requests);assertTrue(run[0].partial.toString().contains("文档识别"));
   }else{
    assertFalse(run[0].partial.toString(),run[0].error);assertEquals(1,transport.requests);assertEquals("",transport.failure);assertFalse(transport.wire.contains("input_file"));
    if(text){assertTrue(transport.wire.contains("Document secret 314160"));assertTrue(transport.wire.contains("Document secret 314161"));assertFalse(transport.wire.contains("input_image"));}
    else{assertEquals(2,transport.images.size());assertTrue(transport.wire.contains("input_image"));for(File image:transport.images)assertFalse(image.exists());}
   }
  }finally{
   if(run[0]!=null&&!run[0].finished){ChatGenerationService.stop(context,run[0]);await(run[0]);}
   if(scenario!=null)scenario.close();repository.flush();restore(prefs,backup);if(descriptor!=null)AttachmentSnapshots.discard(context,descriptor);if(original!=null)original.delete();
  }
 }
 private void await(ChatGenerationService.Run run)throws Exception{
  assertNotNull(run);long deadline=SystemClock.uptimeMillis()+15000;
  while(!run.finished&&SystemClock.uptimeMillis()<deadline)Thread.sleep(20);
  InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertTrue("generation did not finish",run.finished);
 }
 private void restore(SharedPreferences prefs,Map<String,?> values){
  SharedPreferences.Editor editor=prefs.edit().clear();
  for(Map.Entry<String,?> entry:values.entrySet()){
   Object value=entry.getValue();String key=entry.getKey();
   if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
   else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);
   else if(value instanceof Float)editor.putFloat(key,(Float)value);else if(value instanceof Set)editor.putStringSet(key,new HashSet<>((Set<String>)value));
  }editor.commit();
 }
}
