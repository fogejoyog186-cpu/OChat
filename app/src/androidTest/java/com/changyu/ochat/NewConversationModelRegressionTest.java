package com.changyu.ochat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NewConversationModelRegressionTest {
 private JSONObject config(String provider,String model)throws Exception{return new JSONObject().put("providerId",provider).put("provider",provider).put("modelId",model).put("model",model).put("reasoningEffort","high").put("systemPrompt","previous session prompt");}
 private JSONObject session(String id,String provider,String model,boolean empty)throws Exception{return new JSONObject().put("id",id).put("title",empty?"新对话":id).put("config",config(provider,model)).put("messages",empty?new JSONArray():new JSONArray().put(new JSONObject().put("id","m-"+id).put("user",true).put("text","saved question")));}
 private void setup(RegressionPreferences state)throws Exception{
  JSONArray providers=new JSONArray().put(new JSONObject().put("id","provider-a").put("name","Provider A").put("protocol",ChatApiClient.RESPONSES).put("api","https://unused.invalid/v1").put("models",new JSONArray().put(ModelStore.model("model-a").put("outputTokens","1000"))).put("selectedModelId","model-a"))
   .put(new JSONObject().put("id","provider-b").put("name","Provider B").put("protocol",ChatApiClient.GEMINI).put("api","https://unused.invalid").put("models",new JSONArray().put(ModelStore.model("model-b").put("outputTokens","2000"))).put("selectedModelId","model-b"));
  JSONArray sessions=new JSONArray().put(session("last-b","provider-b","model-b",false)).put(session("placeholder","provider-a","model-a",true)).put(session("saved-a","provider-a","model-a",false));
  state.prefs.edit().putString("providers",providers.toString()).putString("providerId","provider-a").putString("modelId","model-a").putString("provider","Provider A").putString("model","model-a").putString("systemPrompt","new global prompt").putString("sessions",sessions.toString()).putString("currentSessionId","last-b").commit();
 }
 private JSONObject current(RegressionPreferences state){return MessageEdits.findSession(SessionRepository.get(state.prefs).read(),state.prefs.getString("currentSessionId",""));}
 private void model(RegressionPreferences state,String provider,String model)throws Exception{
  JSONObject config=current(state).getJSONObject("config");assertEquals(provider,config.getString("providerId"));assertEquals(model,config.getString("modelId"));assertEquals("default",config.getString("reasoningEffort"));assertEquals("new global prompt",config.getString("systemPrompt"));assertFalse(config.has("apiKey"));
 }
 private void invoke(MainActivity activity,String method,JSONObject session)throws Exception{
  Method action=session==null?MainActivity.class.getDeclaredMethod(method):MainActivity.class.getDeclaredMethod(method,JSONObject.class);action.setAccessible(true);if(session==null)action.invoke(activity);else action.invoke(activity,session);
 }
 @Test public void coldEntryReusesEmptySessionWithLastConversationModel()throws Exception{
  try(RegressionPreferences state=new RegressionPreferences()){
   setup(state);
   try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(MainActivity.class)){
    screen.onActivity(activity->{try{assertEquals("placeholder",state.prefs.getString("currentSessionId",""));model(state,"provider-b","model-b");assertEquals("2000",current(state).getJSONObject("config").getString("outputTokens"));assertEquals("model-a",state.prefs.getString("modelId",""));}catch(Exception error){throw new AssertionError(error);}});
    screen.recreate();screen.onActivity(activity->{try{model(state,"provider-b","model-b");}catch(Exception error){throw new AssertionError(error);}});
   }
  }
 }
 @Test public void newConversationFollowsWhicheverSavedConversationWasLastViewed()throws Exception{
  try(RegressionPreferences state=new RegressionPreferences()){
   setup(state);
   try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(MainActivity.class)){
    screen.onActivity(activity->{try{
     invoke(activity,"restoreSession",MessageEdits.findSession(SessionRepository.get(state.prefs).read(),"saved-a"));invoke(activity,"resetConversation",null);model(state,"provider-a","model-a");
     invoke(activity,"restoreSession",MessageEdits.findSession(SessionRepository.get(state.prefs).read(),"last-b"));invoke(activity,"resetConversation",null);model(state,"provider-b","model-b");
     assertEquals("previous session prompt",MessageEdits.findSession(SessionRepository.get(state.prefs).read(),"last-b").getJSONObject("config").getString("systemPrompt"));
    }catch(Exception error){throw new AssertionError(error);}});
   }
  }
 }
 @Test public void deletedModelDoesNotBecomeTheNextConversationDefault()throws Exception{
  try(RegressionPreferences state=new RegressionPreferences()){
   setup(state);JSONArray providers=ModelStore.read(state.prefs);JSONObject provider=providers.getJSONObject(1);provider.put("models",new JSONArray().put(ModelStore.model("replacement-b")));ModelStore.fallback(provider);state.prefs.edit().putString("providers",providers.toString()).commit();
   JSONObject previous=MessageEdits.findSession(SessionRepository.get(state.prefs).read(),"last-b");JSONObject next=SessionConfig.captureForNew(state.prefs,previous);assertEquals("replacement-b",next.getString("modelId"));assertEquals("provider-b",next.getString("providerId"));
  }
 }
}
