package com.changyu.ochat;

import android.content.SharedPreferences;
import org.json.*;

/** Immutable per-reply routing. Search follows the chat switch; other hosted tools are opt-in. */
final class SearchOptions {
 static final String PREF="searchMode",AUTO="automatic",BUILTIN="builtin",RESPONSES="responses",TAVILY="tavily";
 static final String URL_PREF="builtinUrlContext",CODE_PREF="builtinCodeExecution";
 static final SearchOptions OFF=new SearchOptions(false,AUTO,ChatApiClient.CHAT_COMPLETIONS,"",5,"basic");
 final boolean builtin,tavily,urlContext,codeExecution;final String error,key,depth;final int count;
 SearchOptions(boolean enabled,String mode,String protocol,String key,int count,String depth){this(enabled,mode,protocol,"",key,count,depth,false,false);}
 SearchOptions(boolean enabled,String mode,String protocol,String model,String key,int count,String depth,boolean url,boolean code){this(enabled,mode,protocol,model,key,count,depth,url,code,"");}
 SearchOptions(boolean enabled,String mode,String protocol,String model,String key,int count,String depth,boolean url,boolean code,String base){
  this.key=key;this.count=count;this.depth=depth;
  boolean nativeApi=ChatApiClient.ANTHROPIC.equals(protocol)||ChatApiClient.GEMINI.equals(protocol),responses=ChatApiClient.RESPONSES.equals(protocol);
  boolean deepseek=DeepSeekInputPolicy.applies(base,protocol),deepseekSearch=!deepseek||ModelCapabilities.canonical(model).equals("deepseek-v4-pro")&&(responses||ChatApiClient.ANTHROPIC.equals(protocol));
  builtin=enabled&&(BUILTIN.equals(mode)||RESPONSES.equals(mode)||AUTO.equals(mode)&&deepseekSearch&&(responses||nativeApi||chatSearchModel(model)));
  tavily=enabled&&!builtin;
  urlContext=url&&nativeApi&&!deepseek;codeExecution=code&&(nativeApi||responses)&&!deepseek;
  error=builtin&&!deepseekSearch?"此 DeepSeek 官方模型 / 协议不支持内置搜索。请使用 deepseek-v4-pro 的 Responses / Anthropic 协议，或改用 Tavily。":builtin&&!nativeApi&&!responses&&!ChatApiClient.CHAT_COMPLETIONS.equals(protocol)?"当前供应商协议不支持内置搜索，请选择正确协议或使用 Tavily。":"";
 }
 static boolean chatSearchModel(String model){
  String id=ModelCapabilities.canonical(model);
  return id.equals("gpt-5-search-api")||id.equals("gpt-4o-search-preview")||id.equals("gpt-4o-mini-search-preview");
 }
 /** A failed external search must not disable separately enabled hosted tools. */
 SearchOptions withoutSearch(){return new SearchOptions(this);}
 private SearchOptions(SearchOptions source){key=source.key;count=source.count;depth=source.depth;builtin=false;tavily=false;urlContext=source.urlContext;codeExecution=source.codeExecution;error="";}
 boolean hasTools(){return builtin||urlContext||codeExecution;}
 boolean grounded(){return builtin||urlContext;}
 static SearchOptions capture(SharedPreferences p,String protocol){return capture(p,protocol,p.getString("modelId",""));}
 static SearchOptions capture(SharedPreferences p,String protocol,String model){
  String mode=p.getString(PREF,AUTO);if(RESPONSES.equals(mode))mode=BUILTIN;if(!BUILTIN.equals(mode)&&!TAVILY.equals(mode))mode=AUTO;
  int count;try{count=Integer.parseInt(p.getString("tavilyCount","5"));}catch(Exception e){count=-1;}
  String base=p.getString("api","");JSONArray providers=ModelStore.read(p);for(int i=0;i<providers.length();i++){JSONObject provider=providers.optJSONObject(i);if(provider!=null&&p.getString("providerId","").equals(provider.optString("id"))){base=provider.optString("api");break;}}
  return new SearchOptions(p.getBoolean("webSearchEnabled",true),mode,protocol,model,p.getString("tavilyKey","").trim(),count,p.getString("tavilyDepth","basic").trim(),p.getBoolean(URL_PREF,false),p.getBoolean(CODE_PREF,false),base);
 }
}
