package com.changyu.ochat;

import android.content.*;
import androidx.core.content.FileProvider;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.widget.LinearLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DocumentOcrRegressionTest {
 private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
 private JSONObject provider(String id,String model)throws Exception{return new JSONObject().put("id",id).put("name",id).put("api","https://"+id+".invalid/v1").put("apiKey","fixture-secret").put("protocol",ChatApiClient.RESPONSES).put("models",new JSONArray().put(ModelStore.model(model).put("outputTokens","2048")));}
 private JSONObject configured()throws Exception{return ModelStore.model("deepseek-v4-pro").put(DocumentOcr.ENABLED,true).put(DocumentOcr.PROVIDER,"reader").put(DocumentOcr.MODEL,"vision");}
 private DocumentOcr.Target target(String model)throws Exception{return new DocumentOcr.Target(provider("reader",model),ModelStore.model(model).put("outputTokens","2048"));}
 private File pdf()throws Exception{
  File directory=new File(context.getFilesDir(),"imported_attachments");assertTrue(directory.isDirectory()||directory.mkdirs());File file=File.createTempFile("ocr-fixture-",".pdf",directory);PdfDocument doc=new PdfDocument();
  try(OutputStream out=new FileOutputStream(file)){for(int i=1;i<=2;i++){PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(200,300,i).create());Paint paint=new Paint();paint.setColor(Color.BLUE);page.getCanvas().drawCircle(70,80,30,paint);doc.finishPage(page);}doc.writeTo(out);}finally{doc.close();}return file;
 }
 private JSONArray history(File file)throws Exception{return new JSONArray().put(new JSONObject().put("role","user").put("_messageId","u").put("content",new JSONArray().put(new JSONObject().put("type","text").put("text","PRIVATE_QUESTION")).put(new JSONObject().put("type","input_file").put("filename","report.pdf").put("file_data",new FileAttachmentData(file,"application/pdf",true)))));}
 private static final class Reader extends ChatApiClient {
  int calls;String mode="ok";final List<String> models=new ArrayList<>();final List<JSONArray> inputs=new ArrayList<>();final List<File> images=new ArrayList<>();Runnable after=()->{};
  @Override public void streamChat(String base,String key,String model,JSONArray history,double temperature,int max,double top,String effort,String protocol,SearchOptions search,StreamCallback callback){
   calls++;models.add(model);inputs.add(history);assertFalse(search.hasTools());assertFalse(search.tavily);assertEquals("default",effort);
   try{JSONObject part=history.getJSONObject(1).getJSONArray("content").getJSONObject(1);Object value=part.optJSONObject("image_url")==null?part.opt("image_url"):part.getJSONObject("image_url").opt("url");assertNotNull(value);if(value instanceof FileAttachmentData){FileAttachmentData data=(FileAttachmentData)value;assertTrue(data.file.isFile());images.add(data.file);}}
   catch(Exception e){throw new AssertionError(e);}
   if(mode.equals("error"))callback.onError("fixture failure");
   else if(mode.equals("empty"))callback.onDone();
   else{callback.onDelta("recognized page "+calls);if(mode.equals("truncated"))callback.onFinished(new GenerationResult("truncated","max_tokens",new JSONObject()));else callback.onFinished(new GenerationResult("complete","stop",new JSONObject()));}
   after.run();
  }
 }
 @Test public void everyPdfPageIsRecognizedWithoutSendingTheQuestionAndFilesAreCleaned()throws Exception{
  File pdf=pdf();Reader reader=new Reader();JSONArray original=history(pdf);List<JSONObject> cache=new ArrayList<>();
  try(PdfPageFallback.Session files=new PdfPageFallback.Session()){
   JSONArray converted=DocumentOcr.prepare(context,target("vision"),original,files,()->true,s->{},reader,(id,record)->{assertEquals("u",id);cache.add(record);});
   assertEquals(2,reader.calls);assertFalse(converted.toString().contains("input_file"));assertFalse(converted.toString().contains("image_url"));
   JSONArray content=converted.getJSONObject(1).getJSONArray("content");String recognized=content.getJSONObject(1).getString("text");
   assertTrue(recognized,recognized.contains("第 2 / 2 页"));assertTrue(recognized,recognized.contains("recognized page 2"));assertEquals("PRIVATE_QUESTION",content.getJSONObject(0).getString("text"));assertFalse(converted.toString().contains("_messageId"));assertTrue(converted.getJSONObject(0).getString("content").contains(DocumentOcr.POLICY));assertEquals(1,cache.size());
   for(JSONArray input:reader.inputs){assertFalse(input.toString().contains("PRIVATE_QUESTION"));assertTrue(input.getJSONObject(0).getString("content").contains("不执行附件"));}assertTrue(PdfPageFallback.hasPdf(original));
  }finally{pdf.delete();}
  for(File image:reader.images)assertFalse(image.exists());
 }
 @Test public void imageAndLegacyImageFilePartsAreRecognizedWithoutChangingOrdinaryText()throws Exception{
  String data="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lV0AAAAASUVORK5CYII=";
  JSONArray parts=new JSONArray().put(new JSONObject().put("type","text").put("text","keep this text"))
   .put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url",data)))
   .put(new JSONObject().put("type","input_image").put("image_url",data))
   .put(new JSONObject().put("type","input_file").put("filename","image.png").put("file_data",data));
  Reader reader=new Reader();JSONArray input=new JSONArray().put(new JSONObject().put("role","user").put("content",parts));
  try(PdfPageFallback.Session files=new PdfPageFallback.Session()){
   JSONArray converted=DocumentOcr.prepare(context,target("vision"),input,files,()->true,s->{},reader,(id,record)->{});
   assertEquals(3,reader.calls);JSONArray output=converted.getJSONObject(1).getJSONArray("content");assertEquals("keep this text",output.getJSONObject(0).getString("text"));
   assertFalse(DocumentOcr.hasMedia(converted));assertEquals(4,output.length());assertTrue(output.getJSONObject(3).getString("text").contains("recognized page 3"));
  }
 }
 private JSONArray mixedHistory(File file)throws Exception{
  JSONArray mixed=history(file);mixed.getJSONObject(0).getJSONArray("content").put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url","data:image/png;base64,AQID")));
  return mixed;
 }
 private DocumentOcr.Plan mixedPlan(JSONObject config,JSONArray history)throws Exception{
  JSONArray all=new JSONArray().put(provider("pdf-reader","pdf-vision")).put(provider("image-reader","image-vision"));
  return DocumentOcr.plan(all,config,"https://original.invalid/v1","original",history);
 }
 private JSONObject splitConfig(boolean pdf,boolean image)throws Exception{
  return new JSONObject().put(DocumentOcr.ENABLED,pdf).put(DocumentOcr.PROVIDER,"pdf-reader").put(DocumentOcr.MODEL,"pdf-vision")
   .put(DocumentOcr.IMAGE_ENABLED,image).put(DocumentOcr.IMAGE_PROVIDER,"image-reader").put(DocumentOcr.IMAGE_MODEL,"image-vision");
 }
 @Test public void pdfOnlyLeavesStandaloneImagesUntouchedIncludingWhenCached()throws Exception{
  File file=pdf();Reader reader=new Reader();JSONArray input=mixedHistory(file),records=new JSONArray();
  try(PdfPageFallback.Session files=new PdfPageFallback.Session()){
   DocumentOcr.Plan plan=mixedPlan(splitConfig(true,false),input);
   JSONArray result=DocumentOcr.prepare(context,plan,input,files,()->true,s->{},reader,(id,record)->records.put(record));
   assertEquals(Arrays.asList("pdf-vision","pdf-vision"),reader.models);
   assertEquals("image_url",result.getJSONObject(1).getJSONArray("content").getJSONObject(2).getString("type"));assertEquals(1,records.length());
   input.getJSONObject(0).put("_ocrCache",records);DocumentOcr.Plan off=mixedPlan(splitConfig(false,false),input);
   JSONArray untouched=DocumentOcr.prepare(context,off,input,files,()->true,s->{},reader,(id,record)->fail("disabled types must not use OCR"));
   assertTrue(PdfPageFallback.hasPdf(untouched));assertEquals(2,reader.calls);assertEquals("user",untouched.getJSONObject(0).getString("role"));
  }finally{file.delete();}
 }
 @Test public void imageOnlyLeavesPdfInItsOriginalFileForm()throws Exception{
  File file=pdf();Reader reader=new Reader();JSONArray input=mixedHistory(file);
  try(PdfPageFallback.Session files=new PdfPageFallback.Session()){
   JSONArray result=DocumentOcr.prepare(context,mixedPlan(splitConfig(false,true),input),input,files,()->true,s->{},reader,(id,record)->{});
   assertEquals(Collections.singletonList("image-vision"),reader.models);assertTrue(PdfPageFallback.hasPdf(result));
   JSONArray parts=result.getJSONObject(1).getJSONArray("content");assertEquals("input_file",parts.getJSONObject(1).getString("type"));assertTrue(parts.getJSONObject(2).getString("text").contains("recognized page 1"));
  }finally{file.delete();}
 }
 @Test public void mixedAttachmentsUseTheirOwnTargetsAndCaches()throws Exception{
  File file=pdf();Reader reader=new Reader();JSONArray input=mixedHistory(file),records=new JSONArray();
  try(PdfPageFallback.Session files=new PdfPageFallback.Session()){
   DocumentOcr.Plan plan=mixedPlan(splitConfig(true,true),input);
   JSONArray result=DocumentOcr.prepare(context,plan,input,files,()->true,s->{},reader,(id,record)->records.put(record));
   assertEquals(Arrays.asList("pdf-vision","pdf-vision","image-vision"),reader.models);assertFalse(DocumentOcr.hasMedia(result));assertEquals(2,records.length());
   assertEquals("pdf",records.getJSONObject(0).getString("kind"));assertEquals("image",records.getJSONObject(1).getString("kind"));
   input.getJSONObject(0).put("_ocrCache",records);DocumentOcr.prepare(context,plan,input,files,()->true,s->{},reader,(id,record)->fail("cache must be reused"));assertEquals(3,reader.calls);
  }finally{file.delete();}
 }
 @Test public void cacheIsReusedButChangingRecognitionModelInvalidatesIt()throws Exception{
  File pdf=pdf();Reader reader=new Reader();JSONArray original=history(pdf);JSONArray cache=new JSONArray();
  try(PdfPageFallback.Session files=new PdfPageFallback.Session()){
   DocumentOcr.prepare(context,target("vision"),original,files,()->true,s->{},reader,(id,record)->cache.put(record));assertEquals(2,reader.calls);
   original.getJSONObject(0).put("_ocrCache",cache);
   DocumentOcr.prepare(context,target("vision"),original,files,()->true,s->{},reader,(id,record)->fail("must use cache"));assertEquals(2,reader.calls);
   DocumentOcr.prepare(context,target("vision-new"),original,files,()->true,s->{},reader,(id,record)->{});assertEquals(4,reader.calls);
  }finally{pdf.delete();}
 }
 @Test public void emptyFailedAndTruncatedRecognitionNeverCachesAPartialDocument()throws Exception{
  File pdf=pdf();
  try(PdfPageFallback.Session files=new PdfPageFallback.Session()){
   for(String mode:new String[]{"empty","error","truncated"}){
    Reader reader=new Reader();reader.mode=mode;
    try{DocumentOcr.prepare(context,target("vision"),history(pdf),files,()->true,s->{},reader,(id,record)->fail("invalid OCR must not be cached"));fail(mode);}
    catch(IOException expected){assertEquals(1,reader.calls);}
   }
  }finally{pdf.delete();}
 }
 @Test public void cancellationStopsBeforeTheNextPage()throws Exception{
  File pdf=pdf();Reader reader=new Reader();AtomicBoolean active=new AtomicBoolean(true);reader.after=()->active.set(false);
  try(PdfPageFallback.Session files=new PdfPageFallback.Session()){
   try{DocumentOcr.prepare(context,target("vision"),history(pdf),files,active::get,s->{},reader,(id,record)->fail("cancelled OCR must not cache"));fail();}
   catch(InterruptedIOException expected){assertEquals(1,reader.calls);}
  }finally{pdf.delete();}
 }
 @Test public void bothSettingsCanUseDifferentModelsAndSaveAtomically()throws Exception{
  try(RegressionPreferences state=new RegressionPreferences()){
   state.prefs.edit().putString("providers",new JSONArray().put(provider("pdf-reader","pdf-vision")).put(provider("image-reader","image-vision")).toString()).commit();
   JSONObject original=provider("original","text"),model=original.getJSONArray("models").getJSONObject(0);
   try(ActivityScenario<SettingsActivity> screen=ActivityScenario.launch(new Intent(state.context,SettingsActivity.class).putExtra("providerId","pdf-reader"))){
    screen.onActivity(activity->{try{
     DocumentOcrSettings ui=new DocumentOcrSettings(activity,state.prefs,original,model,new LinearLayout(activity));
     ui.pdfEnabled.setChecked(true);ui.pdfTarget.setSelection(1);ui.imageEnabled.setChecked(true);
     assertFalse(ui.save(model,"original","text"));assertFalse(DocumentOcr.enabled(model,true));assertFalse(DocumentOcr.enabled(model,false));
     ui.imageTarget.setSelection(2);assertTrue(ui.save(model,"original","text"));
     assertEquals("pdf-vision",model.getString(DocumentOcr.MODEL));assertEquals("image-vision",model.getString(DocumentOcr.IMAGE_MODEL));
     ui.pdfEnabled.setChecked(false);assertTrue(ui.imageEnabled.isChecked());assertTrue(ui.save(model,"original","text"));assertFalse(DocumentOcr.enabled(model,true));assertTrue(DocumentOcr.enabled(model,false));
    }catch(Exception e){throw new AssertionError(e);}});
   }
  }
 }
 @Test public void settingsArePerModelAndRemainDraftUntilSave()throws Exception{
  try(RegressionPreferences state=new RegressionPreferences()){
   JSONObject reader=provider("reader","vision");state.prefs.edit().putString("providers",new JSONArray().put(reader).toString()).commit();JSONObject original=provider("original","text"),model=original.getJSONArray("models").getJSONObject(0);
   try(ActivityScenario<SettingsActivity> screen=ActivityScenario.launch(new Intent(state.context,SettingsActivity.class).putExtra("providerId","reader"))){
    screen.onActivity(activity->{try{
     LinearLayout body=new LinearLayout(activity);DocumentOcrSettings ui=new DocumentOcrSettings(activity,state.prefs,original,model,body);
     ui.pdfEnabled.setChecked(true);ui.pdfTarget.setSelection(1);assertFalse(DocumentOcr.enabled(model));assertFalse(DocumentOcr.enabled(reader.getJSONArray("models").getJSONObject(0)));
     assertTrue(ui.save(model,"original","text"));assertTrue(DocumentOcr.enabled(model));assertEquals("reader",model.getString(DocumentOcr.PROVIDER));assertEquals("vision",model.getString(DocumentOcr.MODEL));
     ui.pdfEnabled.setChecked(false);assertTrue(DocumentOcr.enabled(model));assertTrue(ui.save(model,"original","text"));assertFalse(DocumentOcr.enabled(model));
    }catch(Exception e){throw new AssertionError(e);}});
   }
  }
 }
 private static final class Original extends ChatApiClient {
  int calls;String wire="";
  @Override public void streamChat(String base,String key,String model,JSONArray history,double temperature,int max,double top,String effort,String protocol,SearchOptions search,StreamCallback callback){
   calls++;assertEquals("deepseek-v4-pro",model);try{DeepSeekInputPolicy.validate(base,model,protocol,history);wire=history.toString();}catch(Exception e){throw new AssertionError(e);}callback.onDelta("original answer");callback.onDone();
  }
 }
 @Test public void serviceCanRecognizeImagesWhilePdfRecognitionIsDisabled()throws Exception{
  try(RegressionPreferences state=new RegressionPreferences()){
   JSONObject message=new JSONObject().put("id","u").put("user",true).put("text","PRIVATE_QUESTION");
   JSONObject session=new JSONObject().put("id","image-only-service").put("title","fixture").put("messages",new JSONArray().put(message));ConversationBranches.sync(session);
   state.prefs.edit().putString("providers",new JSONArray().put(provider("reader","vision")).toString()).putString("currentSessionId","image-only-service").commit();
   SessionRepository.get(state.prefs).replace(new JSONArray().put(session));
   Reader reader=new Reader();Original original=new Original();
   try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(new Intent(state.context,MainActivity.class).putExtra("resumeGeneration",true))){
    ChatGenerationService.Run run=new ChatGenerationService.Run("image-only-service","deepseek-v4-pro","default",System.currentTimeMillis());
    run.client=original;run.ocrClient=reader;run.base="https://api.deepseek.com";run.key="fixture";run.protocol=ChatApiClient.RESPONSES;run.outputTokens="512";run.branchId=session.optString("activeBranchId");
    run.configuredModel=ModelStore.model("deepseek-v4-pro").put(DocumentOcr.ENABLED,false).put(DocumentOcr.IMAGE_ENABLED,true).put(DocumentOcr.IMAGE_PROVIDER,"reader").put(DocumentOcr.IMAGE_MODEL,"vision");
    run.history=new JSONArray().put(new JSONObject().put("role","user").put("_messageId","u").put("content",new JSONArray().put(new JSONObject().put("type","text").put("text","PRIVATE_QUESTION")).put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url","data:image/png;base64,AQID")))));
    screen.onActivity(activity->ChatGenerationService.launch(activity,run));long until=android.os.SystemClock.uptimeMillis()+10000;while(!run.finished&&android.os.SystemClock.uptimeMillis()<until)Thread.sleep(25);
    assertTrue(run.finished);assertFalse(run.partial.toString(),run.error);assertEquals(1,reader.calls);assertEquals(1,original.calls);assertTrue(original.wire.contains("recognized page 1"));
    assertEquals(1,run.toolEvents.length());assertEquals("image_ocr",run.toolEvents.getJSONObject(0).getString("tool"));assertEquals("image",message.getJSONArray("documentOcr").getJSONObject(0).getString("kind"));
   }
  }
 }
 @Test public void serviceSendsRecognitionBackToOriginalModelAndPersistsReusableCache()throws Exception{
  try(RegressionPreferences state=new RegressionPreferences()){
   File pdf=pdf();JSONObject attachment=AttachmentBatch.descriptor(context.getContentResolver(),FileProvider.getUriForFile(context,context.getPackageName()+".attachments",pdf));Reader reader=new Reader();Original original=new Original();String id="ocr-service";JSONObject message=new JSONObject().put("id","u").put("user",true).put("text","PRIVATE_QUESTION").put("attachments",new JSONArray().put(attachment));JSONObject session=new JSONObject().put("id",id).put("title","fixture").put("messages",new JSONArray().put(message));ConversationBranches.sync(session);
   state.prefs.edit().putString("providers",new JSONArray().put(provider("reader","vision")).toString()).putString("currentSessionId",id).commit();SessionRepository.get(state.prefs).replace(new JSONArray().put(session));
   try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(new Intent(state.context,MainActivity.class).putExtra("resumeGeneration",true))){
    for(int attempt=0;attempt<2;attempt++){
     ChatGenerationService.Run run=new ChatGenerationService.Run(id,"deepseek-v4-pro","default",System.currentTimeMillis());run.client=original;run.ocrClient=reader;run.base="https://api.deepseek.com";run.key="fixture";run.protocol=ChatApiClient.RESPONSES;run.configuredModel=configured();run.outputTokens="512";run.branchId=session.optString("activeBranchId");run.session=session;run.history=ChatGenerationService.history(session,"");
     screen.onActivity(activity->ChatGenerationService.launch(activity,run));long until=android.os.SystemClock.uptimeMillis()+10000;while(!run.finished&&android.os.SystemClock.uptimeMillis()<until)Thread.sleep(25);assertTrue(run.finished);assertFalse(run.partial.toString(),run.error);SessionRepository.get(state.prefs).flush();
     assertEquals(attempt+1,original.calls);assertEquals(2,reader.calls);assertTrue(original.wire.contains("recognized page 2"));assertTrue(original.wire.contains("PRIVATE_QUESTION"));assertFalse(original.wire.contains("input_file"));
     assertEquals(1,message.getJSONArray("documentOcr").length());
    }
   }finally{AttachmentSnapshots.discard(context,attachment);pdf.delete();}
  }
 }
}
