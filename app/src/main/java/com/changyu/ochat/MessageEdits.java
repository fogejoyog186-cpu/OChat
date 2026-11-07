package com.changyu.ochat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/** Mutates only the explicitly addressed message; preserves metadata, attachments and session names. */
final class MessageEdits {
    static boolean ensureIds(JSONArray sessions) throws JSONException {
        boolean changed=false;
        for(int i=0;i<sessions.length();i++) {
            JSONObject session=sessions.optJSONObject(i);
            JSONArray messages=session==null?null:session.optJSONArray("messages");
            if(messages==null)continue;
            for(int j=0;j<messages.length();j++) {
                JSONObject message=messages.optJSONObject(j);
                if(message!=null&&message.optString("id").isEmpty()) {
                    message.put("id",UUID.randomUUID().toString());changed=true;
                }
            }
        }
        return changed;
    }
    static JSONObject findSession(JSONArray sessions,String sessionId) {
        for(int i=0;i<sessions.length();i++) {
            JSONObject s=sessions.optJSONObject(i);
            if(s!=null&&sessionId.equals(s.optString("id")))return s;
        }
        return null;
    }
    static JSONObject apply(JSONArray sessions,String sessionId,String messageId,String text,boolean retry) throws JSONException {
        JSONObject session=findSession(sessions,sessionId);
        JSONArray messages=session==null?null:session.optJSONArray("messages");
        if(messages==null)return null;
        for(int i=0;i<messages.length();i++) {
            JSONObject message=messages.optJSONObject(i);
            if(message==null||!messageId.equals(message.optString("id")))continue;
            boolean user=message.optBoolean("user");
            if(text!=null){message.put("text",text);if(!user)message.remove("error");} // A manually corrected reply is valid context.
            if(retry) {
                // Direct assistant retry replaces this reply. Edited assistant retry keeps
                // the edit as context and regenerates only the downstream continuation.
                int keep=(user||text!=null)?i+1:i;
                JSONArray prefix=new JSONArray();
                for(int j=0;j<keep;j++)prefix.put(messages.get(j));
                session.put("messages",prefix);
            }
            return session;
        }
        return null;
    }
}
