package com.changyu.ochat;

import android.content.SharedPreferences;

/** Immutable per-reply routing; the chat switch is the only enable authority. */
final class SearchOptions {
 static final String PREF="searchMode", AUTO="automatic", RESPONSES="responses", TAVILY="tavily";
 static final SearchOptions OFF=new SearchOptions(false,AUTO,ChatApiClient.CHAT_COMPLETIONS,"",5,"basic");
 final boolean builtin,tavily;final String error,key,depth;final int count;
 SearchOptions(boolean enabled,String mode,String protocol,String key,int count,String depth){
  this.key=key;this.count=count;this.depth=depth;
  builtin=enabled&&(RESPONSES.equals(mode)||(AUTO.equals(mode)&&ChatApiClient.RESPONSES.equals(protocol)));
  tavily=enabled&&!builtin;
  error=builtin&&!ChatApiClient.RESPONSES.equals(protocol)?"联网搜索配置无效：Responses 内置搜索需要供应商协议 Responses；请在供应商设置切换协议，或将搜索模式改为自动 / Tavily。":"";
 }
 static SearchOptions capture(SharedPreferences p,String protocol){String mode=p.getString(PREF,AUTO);if(!RESPONSES.equals(mode)&&!TAVILY.equals(mode))mode=AUTO;int count;try{count=Integer.parseInt(p.getString("tavilyCount","5"));}catch(Exception e){count=-1;}return new SearchOptions(p.getBoolean("webSearchEnabled",true),mode,protocol,p.getString("tavilyKey","").trim(),count,p.getString("tavilyDepth","basic").trim());}
}
