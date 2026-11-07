package com.changyu.ochat;

import org.json.*;
import java.util.UUID;

/** Full downstream snapshots; the selected messages remain the legacy storage/API projection. */
final class ConversationBranches {
 static JSONArray copy(JSONArray a)throws JSONException{return new JSONArray(a.toString());}
 static void sync(JSONObject s)throws JSONException{JSONArray b=s.optJSONArray("branches");if(b==null)return;String id=s.optString("activeBranchId");for(int i=0;i<b.length();i++){JSONObject branch=b.getJSONObject(i);if(id.equals(branch.optString("id"))){branch.put("messages",copy(s.getJSONArray("messages")));return;}}}
 static JSONObject edit(JSONArray sessions,String sessionId,String messageId,String text,boolean retry)throws JSONException{
  JSONObject s=MessageEdits.findSession(sessions,sessionId);if(s==null)return null;JSONArray m=s.optJSONArray("messages");boolean found=false;for(int i=0;m!=null&&i<m.length();i++)if(messageId.equals(m.getJSONObject(i).optString("id")))found=true;if(!found)return null;
  JSONArray b=s.optJSONArray("branches");if(b==null){b=new JSONArray();String original=UUID.randomUUID().toString();b.put(new JSONObject().put("id",original).put("messages",copy(m)));s.put("branches",b).put("activeBranchId",original);}else sync(s);
  String newId=UUID.randomUUID().toString();JSONObject branch=new JSONObject().put("id",newId).put("anchorId",messageId).put("messages",copy(m));b.put(branch);s.put("activeBranchId",newId).put("revisionAnchorId",messageId).put("messages",copy(m));
  MessageEdits.apply(sessions,sessionId,messageId,text,retry);
  // Direct assistant retry removes the addressed reply: keep the switch on its retained parent.
  JSONArray retained=s.getJSONArray("messages");boolean anchorPresent=false;for(int i=0;i<retained.length();i++)if(messageId.equals(retained.getJSONObject(i).optString("id")))anchorPresent=true;
  if(!anchorPresent&&retained.length()>0)s.put("revisionAnchorId",retained.getJSONObject(retained.length()-1).getString("id"));
  sync(s);return s;
 }
 static boolean select(JSONObject s,int index)throws JSONException{JSONArray b=s.optJSONArray("branches");if(b==null||index<0||index>=b.length())return false;sync(s);JSONObject branch=b.getJSONObject(index);s.put("activeBranchId",branch.getString("id")).put("messages",copy(branch.getJSONArray("messages")));return true;}
 static int selected(JSONObject s){JSONArray b=s.optJSONArray("branches");if(b!=null)for(int i=0;i<b.length();i++)if(s.optString("activeBranchId").equals(b.optJSONObject(i).optString("id")))return i;return 0;}
 static String anchor(JSONObject s){String id=s.optString("revisionAnchorId");JSONArray m=s.optJSONArray("messages");for(int i=0;m!=null&&i<m.length();i++)if(id.equals(m.optJSONObject(i).optString("id")))return id;return m!=null&&m.length()>0?m.optJSONObject(m.length()-1).optString("id"):"";}
}
