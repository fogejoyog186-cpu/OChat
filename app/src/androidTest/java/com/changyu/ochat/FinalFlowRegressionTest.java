package com.changyu.ochat;

import android.content.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FinalFlowRegressionTest {
 Context context;SharedPreferences prefs;Map<String,?> backup;
 @Before public void before()throws Exception{
  context=InstrumentationRegistry.getInstrumentation().getTargetContext();prefs=context.getSharedPreferences("chat",0);SessionRepository.get(prefs).flush();backup=new HashMap<>(prefs.getAll());prefs.edit().clear().commit();
  SessionLaunchPolicy.markTaskRemoved();
 }
 @After public void after()throws Exception{
  for(int i=0;i<5&&ChatGenerationService.active()!=null;i++){ChatGenerationService.Run run=ChatGenerationService.active();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ChatGenerationService.stop(context,run));Thread.sleep(100);}
  SessionRepository.get(prefs).flush();SharedPreferences.Editor edit=prefs.edit().clear();for(Map.Entry<String,?> e:backup.entrySet()){Object v=e.getValue();String k=e.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set)edit.putStringSet(k,(Set<String>)v);}edit.commit();
 }
 JSONObject provider()throws Exception{return new JSONObject().put("id","p").put("name","测试供应商").put("api","https://unused.invalid/v1").put("apiKey","test-key").put("protocol",ChatApiClient.RESPONSES).put("selectedModelId","gpt-6.1-sol").put("models",new JSONArray().put(ModelStore.model("gpt-6.1-sol").put("displayName","好用的模型")));}
 JSONObject session(String id,int count)throws Exception{
  JSONArray m=new JSONArray();for(int i=0;i<count;i++)m.put(new JSONObject().put("id",id+"-m"+i).put("text","消息 "+i+(i==5?" unique-search-term":"")).put("user",i%2==0).put("timestamp",i+1));
  JSONObject s=new JSONObject().put("id",id).put("title",id).put("messages",m).put("createdAt",1).put("updatedAt",2);ConversationBranches.sync(s);return s;
 }
 void install(JSONArray sessions)throws Exception{JSONObject p=provider();ModelStore.activate(prefs,new JSONArray().put(p),p);prefs.edit().putString("sessions",sessions.toString()).putString("currentSessionId",sessions.getJSONObject(0).getString("id")).putBoolean("webSearchEnabled",false).commit();}
 ActivityScenario<MainActivity> screen(){return ActivityScenario.launch(new Intent(context,MainActivity.class).putExtra("resumeGeneration",true));}
 Object field(Object target,String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 Object invoke(Object target,String name,Class<?>[] types,Object...args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}
 @Test public void preflightKeepsInputAndDoesNotSaveFailedUserTurn()throws Exception{
  install(new JSONArray().put(session("a",0)));prefs.edit().putString("providers","[]").commit();
  try(ActivityScenario<MainActivity> screen=screen()){screen.onActivity(a->{try{EditText input=(EditText)field(a,"composer");input.setText("must remain");invoke(a,"sendMessage",new Class<?>[]{});assertEquals("must remain",input.getText().toString());assertEquals(0,SessionRepository.get(prefs).read().getJSONObject(0).getJSONArray("messages").length());assertNull(ChatGenerationService.active());}catch(Exception e){throw new AssertionError(e);}});}
 }
 @Test public void conversationConfigIsIsolatedAndContainsNoCredentials()throws Exception{
  install(new JSONArray().put(session("a",0)).put(session("b",0)));JSONArray all=SessionRepository.get(prefs).read();SessionConfig a=new SessionConfig(prefs,all.getJSONObject(0)),b=new SessionConfig(prefs,all.getJSONObject(1));
  a.edit().putString("reasoningEffort","high").putString("systemPrompt","only a").putBoolean("webSearchEnabled",true).apply();
  assertEquals("high",a.getString("reasoningEffort","default"));assertEquals("default",b.getString("reasoningEffort","default"));assertEquals("",b.getString("systemPrompt",""));assertFalse(b.getBoolean("webSearchEnabled",true));assertFalse(a.values.toString().contains("test-key"));
  prefs.edit().putString("reasoningEffort","low").commit();assertEquals("high",a.getString("reasoningEffort","default"));
 }
 @Test public void homeUsesFriendlyNameAndLocalizesEffort()throws Exception{
  install(new JSONArray().put(session("a",0)));prefs.edit().putString("reasoningEffort","medium").commit();
  try(ActivityScenario<MainActivity> screen=screen()){screen.onActivity(a->{try{assertTrue(((TextView)field(a,"modelSelector")).getText().toString().contains("好用的模型"));assertFalse(((TextView)field(a,"modelSelector")).getText().toString().contains("gpt-"));assertTrue(((TextView)field(a,"modelLabel")).getText().toString().contains("Auto"));}catch(Exception e){throw new AssertionError(e);}});}
 }
 @Test public void longConversationRendersBoundedWindowAndSearchJumpsToEarlierMessage()throws Exception{
  install(new JSONArray().put(session("long",1000)));
  try(ActivityScenario<MainActivity> screen=screen()){screen.onActivity(a->{try{LinearLayout list=(LinearLayout)field(a,"messageList");assertTrue("must not inflate 1000 rows",list.getChildCount()<45);assertNull(list.findViewWithTag("message:long-m5"));invoke(a,"openSearchResult",new Class<?>[]{String.class,String.class,String.class},"long","long-m5","unique-search-term");assertNotNull(list.findViewWithTag("message:long-m5"));assertTrue(list.getChildCount()<45);}catch(Exception e){throw new AssertionError(e);}});}
 }
 static final class Transport extends ChatApiClient {
  final CountDownLatch started=new CountDownLatch(1);volatile StreamCallback callback;
  @Override public void streamChat(String b,String k,String m,JSONArray h,double t,int max,double top,String e,String p,SearchOptions search,StreamCallback cb){callback=cb;started.countDown();}
 }
 ChatGenerationService.Run run(JSONObject session,Transport transport)throws Exception{
  ChatGenerationService.Run run=new ChatGenerationService.Run(session.getString("id"),"mock","default",1234);run.base="https://unused.invalid";run.key="test";run.protocol=ChatApiClient.CHAT_COMPLETIONS;run.branchId=session.optString("activeBranchId");run.history=ChatGenerationService.history(session,"");run.session=session;run.client=transport;return run;
 }
 @Test public void twoConversationsGenerateInParallelAndPersistIntoTheirOwnSessions()throws Exception{
  JSONObject a=session("a",1),b=session("b",1);install(new JSONArray().put(a).put(b));Transport one=new Transport(),two=new Transport();ChatGenerationService.Run r1=run(a,one),r2=run(b,two);
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{ChatGenerationService.launch(context,r1);ChatGenerationService.launch(context,r2);});
  assertTrue(one.started.await(8,TimeUnit.SECONDS));assertTrue(two.started.await(8,TimeUnit.SECONDS));assertEquals(2,ChatGenerationService.activeCount());
  one.callback.onDelta("answer a");two.callback.onDelta("answer b");one.callback.onFinished(GenerationResult.of("length",new JSONObject().put("input",12).put("output",4)));two.callback.onDone();
  await(()->ChatGenerationService.activeCount()==0);SessionRepository.get(prefs).flush();JSONArray all=SessionRepository.get(prefs).read();
  JSONObject saved=MessageEdits.findSession(all,"a").getJSONArray("messages").getJSONObject(1);assertEquals("answer a",saved.getString("text"));assertEquals("truncated",saved.getString("completionState"));assertEquals(12,saved.getJSONObject("usage").getInt("input"));
  assertEquals("answer b",MessageEdits.findSession(all,"b").getJSONArray("messages").getJSONObject(1).getString("text"));
 }
 @Test public void canSwitchToOtherConversationDuringGeneration()throws Exception{
  JSONObject a=session("a",1),b=session("b",1);install(new JSONArray().put(a).put(b));Transport transport=new Transport();ChatGenerationService.Run run=run(a,transport);
  try(ActivityScenario<MainActivity> screen=screen()){
   screen.onActivity(activity->ChatGenerationService.launch(activity,run));assertTrue(transport.started.await(8,TimeUnit.SECONDS));
   screen.onActivity(activity->{try{invoke(activity,"restoreSession",new Class<?>[]{JSONObject.class},MessageEdits.findSession(SessionRepository.get(prefs).read(),"b"));assertEquals("b",prefs.getString("currentSessionId",""));assertFalse((Boolean)field(activity,"sending"));assertFalse(run.cancel.isCancelled());}catch(Exception e){throw new AssertionError(e);}});
   transport.callback.onDelta("still a");transport.callback.onDone();await(()->ChatGenerationService.activeCount()==0);
  }
 }
 @Test public void queuePersistsSeparatelyFromActiveConversationUntilPromoted()throws Exception{
  JSONObject a=session("a",1);install(new JSONArray().put(a));JSONObject stored=SessionRepository.get(prefs).read().getJSONObject(0);
  JSONObject message=new JSONObject().put("id","queued").put("text","next").put("user",true);JSONObject queued=new JSONObject().put("message",message).put("config",SessionConfig.capture(prefs));
  ChatGenerationService.enqueue(context,"a",queued);assertEquals(1,stored.getJSONArray("messages").length());assertEquals(1,stored.getJSONArray("outbox").length());SessionRepository.get(prefs).flush();assertEquals("next",new JSONArray(prefs.getString("sessions","[]")).getJSONObject(0).getJSONArray("outbox").getJSONObject(0).getJSONObject("message").getString("text"));
 }
 @Test public void repositoryCheckpointDoesNotRewriteCompleteConversation()throws Exception{
  install(new JSONArray().put(session("a",100)));SessionRepository repo=SessionRepository.get(prefs);repo.read();String before=prefs.getString("sessions","[]");repo.checkpoint("a",new JSONObject().put("message",new JSONObject().put("text","partial")));repo.flush();assertEquals(before,prefs.getString("sessions","[]"));assertTrue(prefs.getString("draft_a","").contains("partial"));
 }
 @Test public void zipBackupRestoresAttachmentsAndRemovesSecrets()throws Exception{
  JSONObject a=session("backup",1);JSONObject descriptor=new JSONObject().put("uri","content://missing.invalid/sample").put("mime","text/plain").put("displayName","note.txt").put("type","text").put("payloadSnapshot","hello backup");
  a.getJSONArray("messages").getJSONObject(0).put("attachments",new JSONArray().put(descriptor));a.put("config",new JSONObject().put("apiKey","do-not-export").put("modelId","mock"));ConversationBranches.edit(new JSONArray().put(a),"backup","backup-m0","edited backup",false);
  File folder=new File(context.getFilesDir(),"imported_attachments");folder.mkdirs();File target=new File(folder,"regression-backup.zip");Uri uri=FileProvider.getUriForFile(context,context.getPackageName()+".attachments",target);
  try{ConversationBackup.exportTo(context,uri,new JSONArray().put(a).toString());JSONArray restored=ConversationBackup.importFrom(context,uri);assertEquals(1,restored.length());assertFalse(restored.toString().contains("do-not-export"));assertNotEquals("backup",restored.getJSONObject(0).getString("id"));
   JSONObject attachment=restored.getJSONObject(0).getJSONArray("messages").getJSONObject(0).getJSONArray("attachments").getJSONObject(0);
   try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(attachment.getString("uri")))){assertEquals("hello backup",new String(ConversationBackup.read(in,100),java.nio.charset.StandardCharsets.UTF_8));}
   assertTrue(restored.getJSONObject(0).getJSONArray("branches").length()>0);
  }finally{target.delete();}
 }
 @Test public void finalScreensRemainCompactAndPreviewExplainsStaticMode()throws Exception{
  JSONObject s=session("ui-final",0).put("title","代码预览与长会话优化");StringBuilder code=new StringBuilder("<!doctype html>\n<html lang=\"zh-CN\">\n<head>\n  <meta charset=\"UTF-8\">\n  <style>\n    body { background: #edf8fb; color: #16353f; }\n    .card { border-radius: 24px; padding: 32px; }\n  </style>\n</head>\n<body>\n  <main class=\"card\">\n    <h1>你好，OChat</h1>\n    <p>简约、清晰，专注对话。</p>\n  </main>\n</body>\n</html>");
  s.getJSONArray("messages").put(new JSONObject().put("id","ui-user").put("user",true).put("text","做一个简洁的 HTML 预览示例"));
  s.getJSONArray("messages").put(new JSONObject().put("id","ui-answer").put("user",false).put("text","下面是可直接保存的页面。\n\n```html\n"+code+"\n```").put("modelId","gpt-6.1-sol").put("completionState","complete"));
  JSONArray all=new JSONArray().put(s);for(int i=0;i<12;i++)all.put(session("设计讨论 "+i,1).put("updatedAt",System.currentTimeMillis()-i*43200000L));install(all);prefs.edit().putString("themeMode","light").commit();
  try(ActivityScenario<MainActivity> screen=screen()){
   screen.onActivity(a->{try{invoke(a,"restoreSession",new Class<?>[]{JSONObject.class},MessageEdits.findSession(SessionRepository.get(prefs).read(),"ui-final"));}catch(Exception e){throw new AssertionError(e);}});
   capture("final-home");
   screen.onActivity(a->{try{invoke(a,"openHistory",new Class<?>[]{});}catch(Exception e){throw new AssertionError(e);}});
   capture("final-history");
   final PanelDialog[] preview={null};screen.onActivity(a->{try{invoke(a,"closeHistory",new Class<?>[]{});preview[0]=CodePreview.show(a,code.toString(),"html");}catch(Exception e){throw new AssertionError(e);}});
   await(()->{final boolean[] ready={false};screen.onActivity(a->{android.webkit.WebView web=findWeb(preview[0].getWindow().getDecorView());ready[0]=web!=null&&"preview-ready".equals(web.getTag())&&web.getHeight()>0;});return ready[0];});
   capture("final-html-preview");android.app.UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();automation.injectInputEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK),true);automation.injectInputEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK),true);
  }
 }
 private android.webkit.WebView findWeb(View view){if(view instanceof android.webkit.WebView)return (android.webkit.WebView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){android.webkit.WebView web=findWeb(((ViewGroup)view).getChildAt(i));if(web!=null)return web;}return null;}
 private void capture(String name)throws Exception{
  android.os.SystemClock.sleep(500);InstrumentationRegistry.getInstrumentation().waitForIdleSync();android.graphics.Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
  File folder=context.getExternalFilesDir("screenshots");assertNotNull(folder);try(OutputStream out=new FileOutputStream(new File(folder,name+".png"))){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}
 }
 interface Ready{boolean get()throws Exception;}
 void await(Ready ready)throws Exception{long deadline=android.os.SystemClock.uptimeMillis()+8000;while(!ready.get()&&android.os.SystemClock.uptimeMillis()<deadline)Thread.sleep(30);assertTrue(ready.get());InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
}
