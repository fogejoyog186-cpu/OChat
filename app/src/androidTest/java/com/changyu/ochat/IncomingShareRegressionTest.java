package com.changyu.ochat;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class IncomingShareRegressionTest {
 private static Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
 private static Uri file(String name){return Uri.parse("content://com.changyu.ochat.test.attachments/"+name);}
 private static Intent shared(String name){Uri uri=file(name);Intent intent=new Intent(context(),MainActivity.class).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("file",uri));return intent;}
 private static Object value(MainActivity activity,String field){try{Field target=MainActivity.class.getDeclaredField(field);target.setAccessible(true);return target.get(activity);}catch(Exception error){throw new AssertionError(error);}}
 @SuppressWarnings("unchecked") private static List<JSONObject> pending(MainActivity activity){return (List<JSONObject>)value(activity,"pendingAttachments");}
 private static EditText composer(MainActivity activity){return (EditText)value(activity,"composer");}
 private static void await(ActivityScenario<MainActivity> scenario,Predicate<MainActivity> condition){
  long until=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<until){boolean[] ready={false};scenario.onActivity(activity->ready[0]=condition.test(activity));if(ready[0])return;SystemClock.sleep(30);}fail("Shared files were not prepared");
 }
 private static final class Preferences implements AutoCloseable {
  final SharedPreferences prefs=context().getSharedPreferences("chat",0);final Map<String,?> saved=new HashMap<>(prefs.getAll());
  Preferences(){prefs.edit().clear().putString("providers","[]").putBoolean("topicEnabled",false).putBoolean("webSearchEnabled",false).commit();}
  void model()throws Exception{
   JSONObject model=ModelStore.model("share-fixture").put("outputTokens","512");
   JSONObject provider=new JSONObject().put("id","share-provider").put("name","Fixture").put("protocol",ChatApiClient.CHAT_COMPLETIONS).put("api","https://unused.invalid/v1").put("apiKey","local-test-only").put("models",new JSONArray().put(model)).put("selectedModelId","share-fixture");
   ModelStore.activate(prefs,new JSONArray().put(provider),provider);
  }
  @SuppressWarnings("unchecked") public void close(){
   if(ChatGenerationService.active()!=null){ChatGenerationService.stop(context(),ChatGenerationService.active());long until=SystemClock.uptimeMillis()+3000;while(ChatGenerationService.active()!=null&&SystemClock.uptimeMillis()<until)SystemClock.sleep(20);}
   SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> entry:saved.entrySet()){Object v=entry.getValue();String k=entry.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set)edit.putStringSet(k,(Set<String>)v);}edit.commit();
  }
 }
 private static void rejected(Intent intent)throws Exception{try{IncomingShare.read(context(),intent);fail("Unsafe share was accepted");}catch(IOException expected){assertNotNull(expected.getMessage());}}
 @Test public void streamAndClipAreDeduplicatedAndOnlyDocumentDataIsRead()throws Exception{
  Intent intent=shared("share-collect").putExtra(Intent.EXTRA_TEXT,"Please read").putExtra("modelId","untrusted-model").putExtra("api","https://attacker.invalid").putExtra("resumeGeneration",true);
  IncomingShare.Request request=IncomingShare.read(context(),intent);
  assertEquals(1,request.uris.size());assertEquals(file("share-collect"),request.uris.get(0));assertEquals("Please read",request.text);assertEquals("",request.modelId);assertEquals("",request.providerId);assertFalse(request.autoSend);
  ArrayList<Uri> many=new ArrayList<>();many.add(file("share-a"));many.add(file("share-b"));
  Intent multiple=new Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM,many);multiple.setClipData(ClipData.newRawUri("same",file("share-a")));
  assertEquals(2,IncomingShare.read(context(),multiple).uris.size());
 }
 @Test public void privateFilesUnsupportedSchemesAndMissingGrantsAreRejected()throws Exception{
  for(Uri uri:new Uri[]{Uri.parse("file:///data/data/com.changyu.ochat/shared_prefs/chat.xml"),Uri.parse("https://example.org/file.pdf"),Uri.parse("content://com.changyu.ochat.attachments/imported_attachments/private.pdf"),Uri.parse("content://10@com.changyu.ochat.attachments/imported_attachments/private.pdf"),Uri.parse("content://unknown.invalid/file")}){
   rejected(new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM,uri));
  }
  ArrayList<Uri> tooMany=new ArrayList<>();for(int i=0;i<=AttachmentBatch.MAX_COUNT;i++)tooMany.add(file("share-limit-"+i));
  rejected(new Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM,tooMany));
  rejected(new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_INTENT,new Intent(Intent.ACTION_VIEW,Uri.parse("file:///secret"))));
 }
 @Test public void manifestOffersOChatForDocumentSharing(){
  Intent intent=new Intent(Intent.ACTION_SEND).setType("application/pdf").addCategory(Intent.CATEGORY_DEFAULT);
  boolean found=false;for(android.content.pm.ResolveInfo match:context().getPackageManager().queryIntentActivities(intent,0))if(MainActivity.class.getName().equals(match.activityInfo.name))found=true;
  assertTrue(found);
 }
 @Test public void coldShareIsRetainedWithoutAModelAndRecreationDoesNotRepeatIt()throws Exception{
  try(Preferences settings=new Preferences();ActivityScenario<MainActivity> scenario=ActivityScenario.launch(shared("share-cold"))){
   await(scenario,a->pending(a).size()==1&&!(Boolean)value(a,"attachmentLoading")&&value(a,"activeAttachmentImport")==null);
   scenario.onActivity(a->{assertEquals(context().getPackageName()+".attachments",Uri.parse(pending(a).get(0).optString("uri")).getAuthority());assertEquals(Intent.ACTION_SEND,a.getIntent().getAction());assertFalse(a.getIntent().hasExtra(Intent.EXTRA_STREAM));assertNull(a.getIntent().getClipData());assertEquals("",composer(a).getText().toString());});
   scenario.recreate();
   await(scenario,a->pending(a).size()==1&&!(Boolean)value(a,"attachmentLoading"));
   JSONArray sessions=new JSONArray(settings.prefs.getString("sessions","[]"));for(int i=0;i<sessions.length();i++)assertEquals(0,sessions.getJSONObject(i).getJSONArray("messages").length());
  }
 }
 @Test public void configuredColdShareWaitsForManualSendAfterRotation()throws Exception{
  try(Preferences settings=new Preferences()){
   settings.model();try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(shared("share-manual").putExtra(Intent.EXTRA_TEXT,"Read this file"))){
    await(scenario,a->pending(a).size()==1&&!(Boolean)value(a,"attachmentLoading")&&value(a,"activeAttachmentImport")==null);
    scenario.recreate();await(scenario,a->pending(a).size()==1&&!(Boolean)value(a,"attachmentLoading"));
    scenario.onActivity(a->assertEquals("Read this file",composer(a).getText().toString()));
    JSONArray sessions=SessionRepository.get(settings.prefs).read();for(int i=0;i<sessions.length();i++){JSONObject session=sessions.getJSONObject(i);assertEquals(0,session.getJSONArray("messages").length());JSONArray queue=session.optJSONArray("outbox");assertTrue(queue==null||queue.length()==0);}
   }
  }
 }
 @Test public void warmSharePreservesExistingDraftAndDoesNotSendIt()throws Exception{
  try(Preferences settings=new Preferences();ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->{composer(a).setText("private unsent draft");a.onNewIntent(shared("share-draft").putExtra(Intent.EXTRA_TEXT,"share instruction"));});
   await(scenario,a->pending(a).size()==1&&!(Boolean)value(a,"attachmentLoading")&&value(a,"activeAttachmentImport")==null);
   scenario.onActivity(a->assertEquals("private unsent draft\nshare instruction",composer(a).getText().toString()));
   scenario.recreate();await(scenario,a->pending(a).size()==1&&!(Boolean)value(a,"attachmentLoading"));
   scenario.onActivity(a->assertEquals("private unsent draft\nshare instruction",composer(a).getText().toString()));
   JSONArray sessions=new JSONArray(settings.prefs.getString("sessions","[]"));for(int i=0;i<sessions.length();i++)assertEquals(0,sessions.getJSONObject(i).getJSONArray("messages").length());
  }
 }
 @Test public void preparingAndRotatingKeepExistingLargeSnapshotReadable()throws Exception{
  char[] chars=new char[300000];java.util.Arrays.fill(chars,'x');String payload=new String(chars);
  JSONObject descriptor=new JSONObject().put("uri",file("share-snapshot").toString()).put("sourceUri",file("share-snapshot").toString()).put("displayName","large.txt").put("mime","text/plain");
  AttachmentSnapshots.capture(context(),descriptor,payload);String key=descriptor.getString("payloadSnapshotFile");
  try(Preferences settings=new Preferences();ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->{try{composer(a).setText("unsent");pending(a).add(descriptor);java.lang.reflect.Method prepare=MainActivity.class.getDeclaredMethod("preparePendingAttachments");prepare.setAccessible(true);prepare.invoke(a);}catch(Exception error){throw new AssertionError(error);}});
   await(scenario,a->!(Boolean)value(a,"attachmentLoading"));
   scenario.onActivity(a->assertEquals(key,pending(a).get(0).optString("payloadSnapshotFile")));
   assertEquals(payload,AttachmentSnapshots.read(context(),descriptor));
   scenario.recreate();await(scenario,a->!pending(a).isEmpty()&&!(Boolean)value(a,"attachmentLoading"));
   scenario.onActivity(a->assertEquals(key,pending(a).get(0).optString("payloadSnapshotFile")));
   assertEquals(payload,AttachmentSnapshots.read(context(),descriptor));
  }finally{AttachmentSnapshots.discard(context(),descriptor);}
 }
 private static final class Transport extends ChatApiClient {
  final CountDownLatch started=new CountDownLatch(1);int calls;
  @Override public void streamChat(String base,String key,String model,JSONArray history,double temperature,int max,double top,String effort,String protocol,SearchOptions search,StreamCallback callback){calls++;started.countDown();}
 }
 @Test public void coldShareCannotResumeOrQueueIntoAnotherSessionUsingExternalExtras()throws Exception{
  org.junit.Assume.assumeTrue(ChatGenerationService.active()==null);
  Field entered=SessionLaunchPolicy.class.getDeclaredField("entered");entered.setAccessible(true);boolean wasEntered=entered.getBoolean(null);
  try(Preferences settings=new Preferences()){
   Transport transport=new Transport();String[] originalId={""};
   try(ActivityScenario<MainActivity> original=ActivityScenario.launch(MainActivity.class)){
    original.onActivity(a->{try{
     originalId[0]=settings.prefs.getString("currentSessionId","");
     JSONArray sessions=SessionRepository.get(settings.prefs).read();JSONObject session=MessageEdits.findSession(sessions,originalId[0]);
     session.put("title","Existing background conversation").put("titleManual",true);
     session.getJSONArray("messages").put(new JSONObject().put("id","existing-share-message").put("user",true).put("text","Original question"));
     SessionRepository.get(settings.prefs).replace(sessions);
     ChatGenerationService.Run run=new ChatGenerationService.Run(originalId[0],"share-fixture","default",System.currentTimeMillis());
     run.client=transport;run.base="https://unused.invalid/v1";run.key="local-test-only";run.protocol=ChatApiClient.CHAT_COMPLETIONS;run.history=new JSONArray();run.session=session;run.outputTokens="512";
     ChatGenerationService.launch(a,run);
    }catch(Exception error){throw new AssertionError(error);}});
    assertTrue(transport.started.await(5,TimeUnit.SECONDS));
   }
   assertNotNull(ChatGenerationService.active(originalId[0]));
   SessionLaunchPolicy.markTaskRemoved();
   Intent intent=shared("share-cold-spoof").putExtra("resumeGeneration",true).putExtra("sessionId",originalId[0]);
   try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(intent)){
    await(scenario,a->pending(a).size()==1&&!(Boolean)value(a,"attachmentLoading")&&value(a,"activeAttachmentImport")==null);
    String freshId=settings.prefs.getString("currentSessionId","");
    assertNotEquals(originalId[0],freshId);
    JSONObject previous=MessageEdits.findSession(SessionRepository.get(settings.prefs).read(),originalId[0]);
    assertNotNull(previous);assertEquals(1,previous.getJSONArray("messages").length());
    assertTrue(previous.optJSONArray("outbox")==null||previous.optJSONArray("outbox").length()==0);
    scenario.onActivity(a->{assertEquals(Intent.ACTION_SEND,a.getIntent().getAction());assertFalse(a.getIntent().hasExtra("resumeGeneration"));assertFalse(a.getIntent().hasExtra("sessionId"));assertFalse(a.getIntent().hasExtra(Intent.EXTRA_STREAM));assertNull(a.getIntent().getClipData());});
    scenario.recreate();await(scenario,a->pending(a).size()==1&&!(Boolean)value(a,"attachmentLoading"));
    assertEquals(freshId,settings.prefs.getString("currentSessionId",""));assertEquals(1,transport.calls);
   }
  }finally{entered.setBoolean(null,wasEntered);}
 }
 @Test public void preparedShareWaitsForManualSendEvenWhenModelIsBusy()throws Exception{
  try(Preferences settings=new Preferences()){
   settings.model();Transport transport=new Transport();
   try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
    scenario.onActivity(a->{String id=settings.prefs.getString("currentSessionId","");JSONObject session=MessageEdits.findSession(SessionRepository.get(settings.prefs).read(),id);ChatGenerationService.Run run=new ChatGenerationService.Run(id,"share-fixture","default",System.currentTimeMillis());run.client=transport;run.base="https://unused.invalid/v1";run.key="local-test-only";run.protocol=ChatApiClient.CHAT_COMPLETIONS;run.history=new JSONArray();run.session=session;run.outputTokens="512";ChatGenerationService.launch(a,run);});
    assertTrue(transport.started.await(5,TimeUnit.SECONDS));
    scenario.onActivity(a->a.onNewIntent(shared("share-queued")));
    await(scenario,a->pending(a).size()==1&&!(Boolean)value(a,"attachmentLoading")&&value(a,"activeAttachmentImport")==null);
    scenario.onActivity(a->assertEquals("",composer(a).getText().toString()));
    scenario.recreate();InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    JSONObject session=MessageEdits.findSession(SessionRepository.get(settings.prefs).read(),settings.prefs.getString("currentSessionId",""));JSONArray queue=session.optJSONArray("outbox");assertTrue(queue==null||queue.length()==0);assertEquals(1,transport.calls);
    scenario.onActivity(a->{assertEquals(1,pending(a).size());assertTrue(AttachmentSnapshots.has(pending(a).get(0)));});
   }
  }
 }
}
