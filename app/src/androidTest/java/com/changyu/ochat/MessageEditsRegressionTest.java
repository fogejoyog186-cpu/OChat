package com.changyu.ochat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MessageEditsRegressionTest {
 private JSONArray sessions() throws Exception {
  return new JSONArray().put(new JSONObject().put("id","s").put("title","manual").put("titleManual",true).put("messages",new JSONArray()
   .put(new JSONObject().put("id","u").put("user",true).put("text","question").put("attachments",new JSONArray().put(new JSONObject().put("uri","content://test/file"))))
   .put(new JSONObject().put("id","a").put("user",false).put("text","answer").put("modelId","local").put("timestamp",123L))
   .put(new JSONObject().put("id","later").put("user",true).put("text","followup"))))
   .put(new JSONObject().put("id","other").put("messages",new JSONArray()));
 }
 @Test public void editOnlyPreservesFollowingMessagesAndMetadata() throws Exception {
  for(String id:new String[]{"u","a"}){JSONArray all=sessions();String other=all.getJSONObject(1).toString();JSONObject s=MessageEdits.apply(all,"s",id,"edited",false);JSONArray m=s.getJSONArray("messages");assertEquals(3,m.length());assertEquals("edited",m.getJSONObject(id.equals("u")?0:1).getString("text"));assertEquals("content://test/file",m.getJSONObject(0).getJSONArray("attachments").getJSONObject(0).getString("uri"));assertEquals(123L,m.getJSONObject(1).getLong("timestamp"));assertEquals("local",m.getJSONObject(1).getString("modelId"));assertEquals("manual",s.getString("title"));assertTrue(s.getBoolean("titleManual"));assertEquals(other,all.getJSONObject(1).toString());}
 }
 @Test public void directRetriesCutAtRoleAppropriateBoundary() throws Exception {
  JSONObject user=MessageEdits.apply(sessions(),"s","u",null,true);assertEquals(1,user.getJSONArray("messages").length());assertEquals("question",user.getJSONArray("messages").getJSONObject(0).getString("text"));
  JSONObject assistant=MessageEdits.apply(sessions(),"s","a",null,true);assertEquals(1,assistant.getJSONArray("messages").length());assertEquals("u",assistant.getJSONArray("messages").getJSONObject(0).getString("id"));
 }
 @Test public void editRetryRetainsEditedMessageIncludingAssistantContext() throws Exception {
  for(String id:new String[]{"u","a"}){JSONObject s=MessageEdits.apply(sessions(),"s",id,"edited",true);JSONArray m=s.getJSONArray("messages");assertEquals(id.equals("u")?1:2,m.length());JSONObject edited=m.getJSONObject(m.length()-1);assertEquals(id,edited.getString("id"));assertEquals("edited",edited.getString("text"));if(id.equals("a")){assertEquals("local",edited.getString("modelId"));assertEquals(123L,edited.getLong("timestamp"));}assertEquals(1,m.getJSONObject(0).getJSONArray("attachments").length());}
 }
 @Test public void correctedErrorReplyBecomesValidContext() throws Exception {
  JSONArray all=sessions();all.getJSONObject(0).getJSONArray("messages").getJSONObject(1).put("error",true);
  JSONObject s=MessageEdits.apply(all,"s","a","corrected",true);
  assertFalse(s.getJSONArray("messages").getJSONObject(1).optBoolean("error"));
  assertEquals("corrected",s.getJSONArray("messages").getJSONObject(1).getString("text"));
 }
 @Test public void missingTargetsDoNotMutateAnySession() throws Exception {
  JSONArray all=sessions();String before=all.toString();assertNull(MessageEdits.apply(all,"other","a","edited",true));assertNull(MessageEdits.apply(all,"missing","u",null,true));assertEquals(before,all.toString());
 }
}
