package com.changyu.ochat;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Uses the provider schema written by SettingsActivity, with legacy current-provider fallback only. */
final class TopicConfig {
    static final String DEFAULT_PROMPT="请根据用户的第一条消息生成一个简洁、准确的话题标题。只返回标题本身，不要引号、前缀、Markdown 或解释。标题不超过 20 个汉字。";
    final String base, key, model, prompt, protocol;
    TopicConfig(SharedPreferences prefs) throws Exception {
        this(prefs,prefs.getString("topicProviderId",""),prefs.getString("topicModel",""),prefs.getString("topicPrompt",DEFAULT_PROMPT));
    }
    TopicConfig(SharedPreferences prefs,String selectedId,String selectedModel,String selectedPrompt) throws Exception {
        String id=selectedId.trim();
        String name=prefs.getString("topicProvider","").trim();
        boolean current="__current__".equals(id)||(id.isEmpty()&&(name.isEmpty()||"当前聊天供应商".equals(name)));
        JSONObject provider=resolveProvider(prefs,current?prefs.getString("providerId",""):id,current?prefs.getString("provider",""):name);
        if(provider==null&&!current)throw new IllegalArgumentException("请选择有效的话题命名供应商");
        if(provider==null)provider=new JSONObject();
        protocol=provider.optString("protocol",ChatApiClient.CHAT_COMPLETIONS);
        base=nonEmpty(provider.optString("api",""),current?prefs.getString("api",""):"");
        key=nonEmpty(provider.optString("apiKey",""),current?prefs.getString("apiKey",""):"");
        model=nonEmpty(selectedModel,nonEmpty(provider.optString("modelId",""),nonEmpty(provider.optString("model",""),current?nonEmpty(prefs.getString("modelId",""),prefs.getString("model","")):"")));
        prompt=selectedPrompt.trim();
        if(base.isEmpty()||key.isEmpty()||model.isEmpty()||prompt.isEmpty())throw new IllegalArgumentException("话题命名已启用，请填写供应商 API 地址、API Key、模型 ID 和提示词");
        if(okhttp3.HttpUrl.parse(base)==null)throw new IllegalArgumentException("话题命名 API 地址必须是有效的 http:// 或 https:// 地址");
    }
    static JSONObject resolveProvider(SharedPreferences prefs,String id,String name) throws Exception {
        JSONArray all=new JSONArray(prefs.getString("providers","[]"));
        for(int i=0;i<all.length();i++){JSONObject p=all.optJSONObject(i);if(p!=null&&!id.isEmpty()&&id.equals(p.optString("id")))return p;}
        for(int i=0;i<all.length();i++){JSONObject p=all.optJSONObject(i);if(p!=null&&((!id.isEmpty()&&id.equals(p.optString("name")))||(id.isEmpty()&&!name.isEmpty()&&name.equals(p.optString("name")))))return p;}
        return null;
    }
    private static String nonEmpty(String value,String fallback){return value.trim().isEmpty()?fallback.trim():value.trim();}
}
