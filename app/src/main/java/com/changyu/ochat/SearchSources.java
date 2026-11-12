package com.changyu.ochat;

import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Only provider-returned, validated sources. Never infer citations from answer text. */
final class SearchSources {
 final JSONArray items=new JSONArray();
 static SearchSources parse(JSONArray array){
  SearchSources out=new SearchSources();Map<String,JSONObject> seen=new LinkedHashMap<>();
  if(array!=null)for(int i=0;i<array.length();i++){
   JSONObject x=array.optJSONObject(i);if(x==null)continue;String url=x.optString("url").trim();if(!MarkdownRenderer.isSafeLink(url))continue;
   String host=android.net.Uri.parse(url).getHost(),title=x.optString("title","").trim(),snippet=x.optString("content",x.optString("snippet"));
   try{
    JSONObject source=seen.get(url);
    if(source!=null){if(!title.isEmpty()&&source.optString("title").equals(host))source.put("title",title);if(source.optString("snippet").isEmpty()&&!snippet.isEmpty())source.put("snippet",snippet);}
    else{source=new JSONObject().put("title",title.isEmpty()?host:title).put("url",url).put("snippet",snippet);seen.put(url,source);out.items.put(source);}
    if(!x.optString("searchSuggestionsHtml").isEmpty())source.put("searchSuggestionsHtml",x.optString("searchSuggestionsHtml"));
   }catch(JSONException ignored){}
  }
  return out;
 }
 void collect(JSONObject value){
  if(value==null)return;JSONArray combined=new JSONArray();for(int i=0;i<items.length();i++)combined.put(items.opt(i));collectValue(value,combined,0);
  SearchSources parsed=parse(combined);String suggestions=suggestions(value,0);
  if(!suggestions.isEmpty()&&parsed.items.length()>0)try{parsed.items.getJSONObject(0).put("searchSuggestionsHtml",suggestions);}catch(JSONException ignored){}
  for(int i=0;i<parsed.items.length();i++)try{items.put(i,parsed.items.get(i));}catch(JSONException ignored){}
 }
 private static final String[] FIELDS={"output","content","annotations","part","item","response","annotation","citation","citations","delta","message","choices","candidates","groundingMetadata","groundingChunks","action","sources","urlContextMetadata","urlMetadata"};
 private static void collectValue(Object value,JSONArray out,int depth){
  if(depth>32)return;
  if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)collectValue(a.opt(i),out,depth+1);}
  else if(value instanceof JSONObject){
   JSONObject o=(JSONObject)value;String type=o.optString("type");
   if(type.equals("url_citation")||type.equals("web_search_result")||type.equals("web_search_result_location")||type.equals("web_fetch_result")){
    try{JSONObject citation=o.optJSONObject("url_citation");if(citation==null)citation=o;String title=citation.optString("title");if(title.isEmpty()&&citation.optJSONObject("content")!=null)title=citation.optJSONObject("content").optString("title");out.put(new JSONObject().put("url",citation.optString("url")).put("title",title));}catch(JSONException ignored){}
   }
   JSONObject web=o.optJSONObject("web");if(web!=null)try{out.put(new JSONObject().put("url",web.optString("uri")).put("title",web.optString("title")));}catch(JSONException ignored){}
   if(o.has("retrievedUrl")&&o.optString("urlRetrievalStatus").contains("SUCCESS"))try{out.put(new JSONObject().put("url",o.optString("retrievedUrl")).put("title",o.optString("retrievedUrl")));}catch(JSONException ignored){}
   if(type.equals("web_search_call")){JSONObject action=o.optJSONObject("action");JSONArray a=action==null?null:action.optJSONArray("sources");if(a!=null)for(int i=0;i<a.length();i++)out.put(a.opt(i));}
   for(String key:FIELDS)collectValue(o.opt(key),out,depth+1);
  }
 }
 private static String suggestions(Object value,int depth){
  if(depth>12)return "";
  if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++){String html=suggestions(a.opt(i),depth+1);if(!html.isEmpty())return html;}}
  else if(value instanceof JSONObject){JSONObject o=(JSONObject)value,entry=o.optJSONObject("searchEntryPoint");if(entry!=null)return entry.optString("renderedContent");for(String key:new String[]{"candidates","groundingMetadata","response"}){String html=suggestions(o.opt(key),depth+1);if(!html.isEmpty())return html;}}
  return "";
 }
 static SearchSources fromResponse(JSONObject response){SearchSources s=new SearchSources();s.collect(response);return s;}
 static SearchSources legacy(String text){JSONArray a=new JSONArray();if(text!=null&&!text.trim().startsWith("{")&&!text.trim().startsWith("[")){Matcher m=Pattern.compile("https?://[^\\s<>\\)]+",Pattern.CASE_INSENSITIVE).matcher(text);while(m.find())try{a.put(new JSONObject().put("url",m.group()).put("title",android.net.Uri.parse(m.group()).getHost()));}catch(JSONException ignored){}}return parse(a);}
 String grounding(){return grounding(SearchGrounding.MAX_CHARACTERS);}
 String grounding(int maxCharacters){try{return SearchGrounding.data(items,maxCharacters);}catch(JSONException invalid){return "";}}
}
