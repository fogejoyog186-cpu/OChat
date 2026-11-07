package com.changyu.ochat;

import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Only provider-returned, validated sources. Structured storage is separate from model grounding. */
final class SearchSources {
 final JSONArray items=new JSONArray();
 static SearchSources parse(JSONArray array){SearchSources out=new SearchSources();Map<String,JSONObject> seen=new LinkedHashMap<>();if(array!=null)for(int i=0;i<array.length();i++){JSONObject x=array.optJSONObject(i);if(x==null)continue;String url=x.optString("url").trim();if(!MarkdownRenderer.isSafeLink(url))continue;String host=android.net.Uri.parse(url).getHost(),title=x.optString("title","").trim(),snippet=x.optString("content",x.optString("snippet"));try{JSONObject prior=seen.get(url);if(prior!=null){if(!title.isEmpty()&&prior.optString("title").equals(host))prior.put("title",title);if(prior.optString("snippet").isEmpty()&&!snippet.isEmpty())prior.put("snippet",snippet);continue;}JSONObject source=new JSONObject().put("title",title.isEmpty()?host:title).put("url",url).put("snippet",snippet);seen.put(url,source);out.items.put(source);}catch(JSONException ignored){}}return out;}
 void collect(JSONObject value){if(value==null)return;JSONArray combined=new JSONArray();for(int i=0;i<items.length();i++)combined.put(items.opt(i));collectValue(value,combined);SearchSources parsed=parse(combined);for(int i=0;i<parsed.items.length();i++){if(i<items.length())try{items.put(i,parsed.items.get(i));}catch(JSONException ignored){}else items.put(parsed.items.opt(i));}}
 private static void collectValue(Object value,JSONArray out){if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)collectValue(a.opt(i),out);}else if(value instanceof JSONObject){JSONObject o=(JSONObject)value;if("url_citation".equals(o.optString("type")))out.put(o);if("web_search_call".equals(o.optString("type"))){JSONObject action=o.optJSONObject("action");JSONArray a=action==null?null:action.optJSONArray("sources");if(a!=null)for(int i=0;i<a.length();i++)out.put(a.opt(i));}for(String k:new String[]{"output","content","annotations","part","item","response","annotation"})collectValue(o.opt(k),out);}}
 static SearchSources fromResponse(JSONObject response){SearchSources s=new SearchSources();s.collect(response);return s;}
 static SearchSources legacy(String text){JSONArray a=new JSONArray();if(text!=null&&!text.trim().startsWith("{")&&!text.trim().startsWith("[")){Matcher m=Pattern.compile("https?://[^\\s<>\\)]+",Pattern.CASE_INSENSITIVE).matcher(text);while(m.find())try{a.put(new JSONObject().put("url",m.group()).put("title",android.net.Uri.parse(m.group()).getHost()));}catch(JSONException ignored){}}return parse(a);}
 String grounding(){return grounding(SearchGrounding.MAX_CHARACTERS);}
 String grounding(int maxCharacters){try{return SearchGrounding.data(items,maxCharacters);}catch(JSONException invalid){return "";}}
}
