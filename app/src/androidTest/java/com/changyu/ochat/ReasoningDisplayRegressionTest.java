package com.changyu.ochat;

import android.content.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import androidx.lifecycle.Lifecycle;
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

/** UI, foreground service and saved messages use the same provider reasoning. No live model API. */
@RunWith(AndroidJUnit4.class)
public class ReasoningDisplayRegressionTest {
 Context context;SharedPreferences prefs;Map<String,?> backup;
 static final String THOUGHT="先比较两个方案，再检查边界条件。";
 static final class Transport extends ChatApiClient {
  final CountDownLatch started=new CountDownLatch(1);volatile StreamCallback callback;
  @Override public void streamChat(String b,String k,String m,JSONArray h,double t,int max,double top,String e,String p,SearchOptions search,StreamCallback cb){callback=cb;started.countDown();}
 }
 @Before public void before()throws Exception{
  context=InstrumentationRegistry.getInstrumentation().getTargetContext();prefs=context.getSharedPreferences("chat",0);SessionRepository.get(prefs).flush();backup=new HashMap<>(prefs.getAll());prefs.edit().clear().commit();SessionLaunchPolicy.markTaskRemoved();
 }
 @After public void after()throws Exception{
  for(int i=0;i<5&&ChatGenerationService.active()!=null;i++){ChatGenerationService.Run run=ChatGenerationService.active();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ChatGenerationService.stop(context,run));Thread.sleep(100);}
  SessionRepository.get(prefs).flush();SharedPreferences.Editor edit=prefs.edit().clear();
  for(Map.Entry<String,?> e:backup.entrySet()){Object v=e.getValue();String k=e.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set)edit.putStringSet(k,(Set<String>)v);}edit.commit();
 }
 JSONObject session(String id)throws Exception{
  JSONObject s=new JSONObject().put("id",id).put("title","思考内容测试").put("messages",new JSONArray().put(new JSONObject().put("id",id+"-user").put("user",true).put("text","比较这两个方案，给出结论。").put("timestamp",1)));ConversationBranches.sync(s);return s;
 }
 void install(JSONObject... sessions)throws Exception{
  JSONObject p=new JSONObject().put("id","p").put("name","本地测试").put("api","https://unused.invalid/v1").put("apiKey","test").put("protocol",ChatApiClient.CHAT_COMPLETIONS).put("selectedModelId","deepseek-reasoner").put("models",new JSONArray().put(ModelStore.model("deepseek-reasoner")));
  ModelStore.activate(prefs,new JSONArray().put(p),p);JSONArray all=new JSONArray();for(JSONObject s:sessions)all.put(s);prefs.edit().putString("sessions",all.toString()).putString("currentSessionId",sessions[0].getString("id")).commit();
 }
 ActivityScenario<MainActivity> screen(){return ActivityScenario.launch(new Intent(context,MainActivity.class).putExtra("resumeGeneration",true));}
 ChatGenerationService.Run launch(ActivityScenario<MainActivity> screen,JSONObject session,Transport transport,String protocol)throws Exception{
  ChatGenerationService.Run run=new ChatGenerationService.Run(session.getString("id"),"deepseek-reasoner","default",System.currentTimeMillis());run.protocol=protocol;run.base="https://unused.invalid";run.key="test";run.session=session;run.branchId=session.optString("activeBranchId");run.history=ChatGenerationService.history(session,"");run.client=transport;
  screen.onActivity(a->ChatGenerationService.launch(a,run));assertTrue(transport.started.await(8,TimeUnit.SECONDS));return run;
 }
 View root(MainActivity a){return a.getWindow().getDecorView();}
 ReasoningView reasoning(MainActivity a){return root(a).findViewWithTag("reasoning-view");}
 String plain(View view){if(view instanceof TextView)return((TextView)view).getText().toString();StringBuilder text=new StringBuilder();if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)text.append(plain(group.getChildAt(i)));}return text.toString();}
 void waitUi()throws Exception{Thread.sleep(240);InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
 void finish(Transport transport)throws Exception{transport.callback.onFinished(GenerationResult.of("stop",new JSONObject()));long until=android.os.SystemClock.uptimeMillis()+5000;while(ChatGenerationService.active()!=null&&android.os.SystemClock.uptimeMillis()<until)Thread.sleep(25);assertNull(ChatGenerationService.active());waitUi();}
 void restore(MainActivity a,String id){try{Method m=MainActivity.class.getDeclaredMethod("restoreSession",JSONObject.class);m.setAccessible(true);m.invoke(a,MessageEdits.findSession(SessionRepository.get(prefs).read(),id));}catch(Exception e){throw new AssertionError(e);}}
 JSONObject saved(String id)throws Exception{SessionRepository.get(prefs).flush();JSONArray messages=MessageEdits.findSession(SessionRepository.get(prefs).read(),id).getJSONArray("messages");return messages.getJSONObject(messages.length()-1);}
 @Test public void thinkingStreamsBeforeAnswerAndExpandedStateSurvivesRecreation()throws Exception{
  JSONObject session=session("live");install(session);Transport transport=new Transport();
  try(ActivityScenario<MainActivity> screen=screen()){
   launch(screen,session,transport,ChatApiClient.CHAT_COMPLETIONS);transport.callback.onReasoning(THOUGHT);waitUi();
   screen.onActivity(a->{ReasoningView view=reasoning(a);assertNotNull("first thinking token must publish UI",view);assertEquals(View.VISIBLE,view.getVisibility());assertEquals(View.GONE,view.findViewWithTag("reasoning-markdown").getVisibility());assertTrue(((TextView)view.getChildAt(0)).getText().toString().contains("正在思考"));view.getChildAt(0).performClick();assertEquals(THOUGHT,plain(view.findViewWithTag("reasoning-markdown")));});
   screen.moveToState(Lifecycle.State.CREATED);transport.callback.onReasoning("\n后台继续思考。");screen.moveToState(Lifecycle.State.RESUMED);screen.recreate();waitUi();
   screen.onActivity(a->{ReasoningView view=reasoning(a);assertNotNull(view);assertEquals(View.VISIBLE,view.findViewWithTag("reasoning-markdown").getVisibility());assertTrue(plain(view.findViewWithTag("reasoning-markdown")).contains(THOUGHT));assertTrue(plain(view.findViewWithTag("reasoning-markdown")).contains("后台继续思考。"));});
   transport.callback.onDelta("结论：选择方案 A。");finish(transport);
   screen.onActivity(a->{ReasoningView view=reasoning(a);assertTrue(((TextView)view.getChildAt(0)).getText().toString().contains("思考过程"));assertEquals(View.VISIBLE,view.findViewWithTag("reasoning-markdown").getVisibility());ViewGroup bubble=(ViewGroup)view.getParent();assertEquals("结论：选择方案 A。",plain(bubble.findViewWithTag("markdown-message")));view.getChildAt(0).performClick();assertEquals(View.GONE,view.findViewWithTag("reasoning-markdown").getVisibility());});
   JSONObject saved=saved("live");assertEquals(THOUGHT+"\n后台继续思考。",saved.getString("reasoningContent"));assertEquals("结论：选择方案 A。",saved.getString("text"));
  }
 }
 @Test public void canonicalSummaryReplacesDraftAndRestoresAcrossConversationSwitch()throws Exception{
  JSONObject one=session("summary"),two=session("other");install(one,two);Transport transport=new Transport();
  try(ActivityScenario<MainActivity> screen=screen()){
   launch(screen,one,transport,ChatApiClient.RESPONSES);transport.callback.onReasoning("draft");transport.callback.onReasoningSnapshot("修正后的思考摘要");transport.callback.onDelta("最终答案");finish(transport);
   assertEquals("修正后的思考摘要",saved("summary").getString("reasoningContent"));assertEquals("summary",saved("summary").getString("reasoningKind"));
   screen.onActivity(a->{restore(a,"other");assertNull(reasoning(a));restore(a,"summary");ReasoningView view=reasoning(a);assertNotNull(view);assertTrue(((TextView)view.getChildAt(0)).getText().toString().contains("思考摘要"));view.getChildAt(0).performClick();assertEquals("修正后的思考摘要",plain(view.findViewWithTag("reasoning-markdown")));});
  }
 }
 @Test public void stopDuringThinkingKeepsReasoningOnlyMessage()throws Exception{
  JSONObject session=session("stop");install(session);Transport transport=new Transport();
  try(ActivityScenario<MainActivity> screen=screen()){
   ChatGenerationService.Run run=launch(screen,session,transport,ChatApiClient.CHAT_COMPLETIONS);transport.callback.onReasoning(THOUGHT);waitUi();screen.onActivity(a->ChatGenerationService.stop(a,run));waitUi();
   JSONObject saved=saved("stop");assertEquals(THOUGHT,saved.getString("reasoningContent"));assertEquals("",saved.getString("text"));assertTrue(saved.getBoolean("interrupted"));
   screen.onActivity(a->{ReasoningView view=reasoning(a);assertNotNull(view);assertTrue(((TextView)view.getChildAt(0)).getText().toString().contains("思考已中断"));assertEquals(View.GONE,((ViewGroup)view.getParent()).findViewWithTag("markdown-message").getVisibility());});
  }
 }
 @Test public void thinkingOnlyTokenLimitKeepsSummaryAndExplainsMissingAnswer()throws Exception{
  JSONObject session=session("limit");install(session);Transport transport=new Transport();
  try(ActivityScenario<MainActivity> screen=screen()){
   launch(screen,session,transport,ChatApiClient.RESPONSES);transport.callback.onReasoningSnapshot(THOUGHT);transport.callback.onFinished(GenerationResult.of("max_output_tokens",new JSONObject()));long until=android.os.SystemClock.uptimeMillis()+5000;while(ChatGenerationService.active()!=null&&android.os.SystemClock.uptimeMillis()<until)Thread.sleep(25);waitUi();
   JSONObject message=saved("limit");assertEquals(THOUGHT,message.getString("reasoningContent"));assertEquals("truncated",message.getString("completionState"));assertFalse(message.getBoolean("error"));assertTrue(message.getString("text").contains("尚未生成最终回答"));
   screen.onActivity(a->{ReasoningView view=reasoning(a);assertNotNull(view);assertTrue(((TextView)view.getChildAt(0)).getText().toString().contains("思考摘要"));});
  }
 }
 @Test public void ordinaryAnswerDoesNotShowEmptyReasoningPanel()throws Exception{
  JSONObject session=session("plain");install(session);Transport transport=new Transport();
  try(ActivityScenario<MainActivity> screen=screen()){
   launch(screen,session,transport,ChatApiClient.CHAT_COMPLETIONS);transport.callback.onDelta("普通回答");finish(transport);screen.onActivity(a->assertNull(reasoning(a)));assertEquals("",saved("plain").getString("reasoningContent"));
  }
 }
 @Test public void historyReasoningUsesThemeAndCompactCollapsedHeader()throws Exception{
  JSONObject session=session("history");session.getJSONArray("messages").put(new JSONObject().put("id","history-answer").put("user",false).put("text","建议先完成接收、保存和显示，再验证后台恢复。").put("reasoningContent","### 比较方案\n\n- 保留服务端返回的思考内容。\n- 与最终回答分开显示。\n- 默认折叠，避免占满聊天页面。").put("timestamp",2));ConversationBranches.sync(session);install(session);
  try(ActivityScenario<MainActivity> screen=screen()){
   screen.onActivity(a->{ReasoningView view=reasoning(a);assertNotNull(view);view.getChildAt(0).performClick();});waitUi();
   Bitmap screenshot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(screenshot);
   try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"reasoning-display.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();
  }
 }
}
