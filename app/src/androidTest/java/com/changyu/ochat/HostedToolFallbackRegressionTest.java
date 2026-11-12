package com.changyu.ochat;

import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Exercise independent hosted tools through the real service, including failed external search. */
@RunWith(AndroidJUnit4.class)
public class HostedToolFallbackRegressionTest {
 private static final class Transport extends ChatApiClient {
  final boolean fail;final CountDownLatch received=new CountDownLatch(1);volatile SearchOptions options;int searches;
  Transport(boolean fail){this.fail=fail;}
  @Override public void tavilySources(String key,String query,int count,String depth,CallbackResult<SearchSources> callback){
   searches++;if(fail)callback.onError("fixture search failure");else callback.onSuccess(source("https://example.org/tavily"));
  }
  @Override public void streamChat(String base,String key,String model,JSONArray history,double temperature,int max,double top,String effort,String protocol,SearchOptions options,StreamCallback callback){
   this.options=options;callback.onSearchSources(source("https://example.org/native"),"网页读取完成");
   try{callback.onToolEvent(new JSONObject().put("tool","code_execution").put("status","completed").put("output","42"));}catch(JSONException e){throw new AssertionError(e);}
   callback.onDelta("fixture answer");callback.onDone();received.countDown();
  }
  private static SearchSources source(String url){try{return SearchSources.parse(new JSONArray().put(new JSONObject().put("url",url).put("title",url)));}catch(JSONException e){throw new AssertionError(e);}}
 }
 private void run(boolean configured,boolean failure)throws Exception{
  try(RegressionPreferences state=new RegressionPreferences()){
   JSONObject session=new JSONObject().put("id","hosted-fallback").put("title","fixture").put("messages",new JSONArray().put(new JSONObject().put("id","u").put("user",true).put("text","read and calculate")));
   ConversationBranches.sync(session);SessionRepository.get(state.prefs).replace(new JSONArray().put(session));state.prefs.edit().putString("currentSessionId","hosted-fallback").commit();
   ChatGenerationService.Run run=new ChatGenerationService.Run("hosted-fallback","fixture","default",System.currentTimeMillis());Transport transport=new Transport(failure);
   run.client=transport;run.base="https://unused.invalid";run.key="fixture";run.protocol=ChatApiClient.GEMINI;run.session=session;run.branchId=session.optString("activeBranchId");
   run.history=new JSONArray().put(new JSONObject().put("role","user").put("content","read and calculate"));run.query="read and calculate";run.outputTokens="1024";run.configuredModel=new JSONObject().put("contextLength","4096");
   run.search=new SearchOptions(true,SearchOptions.TAVILY,run.protocol,"fixture",configured?"fixture":"",5,"basic",true,true);
   try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(new Intent(state.context,MainActivity.class).putExtra("resumeGeneration",true))){
    try{
     screen.onActivity(activity->ChatGenerationService.launch(activity,run));assertTrue(transport.received.await(8,TimeUnit.SECONDS));
     long until=android.os.SystemClock.uptimeMillis()+5000;while(!run.finished&&android.os.SystemClock.uptimeMillis()<until)Thread.sleep(25);
     assertTrue(run.finished);assertFalse(run.error);assertEquals(configured?1:0,transport.searches);assertTrue(transport.options.urlContext);assertTrue(transport.options.codeExecution);assertFalse(transport.options.builtin);
     assertEquals(configured&&!failure,transport.options.tavily);SessionRepository.get(state.prefs).flush();
     JSONObject saved=MessageEdits.findSession(SessionRepository.get(state.prefs).read(),"hosted-fallback").getJSONArray("messages").getJSONObject(1);
     assertEquals("fixture answer",saved.getString("text"));assertEquals(configured&&!failure?2:1,saved.getJSONArray("sources").length());assertEquals(1,saved.getJSONArray("toolEvents").length());
     assertEquals("网页读取完成",saved.getString("searchStatus"));
    }finally{if(!run.finished)InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ChatGenerationService.stop(state.context,run));}
   }
  }
 }
 @Test public void missingSearchKeyPreservesHostedTools()throws Exception{run(false,false);}
 @Test public void failedExternalSearchPreservesHostedTools()throws Exception{run(true,true);}
 @Test public void nativeSourcesAreMergedWithExternalSearchSources()throws Exception{run(true,false);}
}
