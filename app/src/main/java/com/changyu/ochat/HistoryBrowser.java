package com.changyu.ochat;

import android.content.Context;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Recycled history rows, debounced off-thread full-text search with exact message targets. */
final class HistoryBrowser {
 interface Open {void open(String sessionId,String messageId,String query);}
 private final Context context;private final ThemePalette palette;private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService search=Executors.newSingleThreadExecutor();
 private final List<Row> rows=new ArrayList<>();private final Map<String,Index> index=new HashMap<>();private final BaseAdapter adapter;private final ListView list;
 private int generation;private Runnable debounce;private String query="";private boolean archived;private JSONArray sessions=new JSONArray();private String current="";private final Open open;private final Consumer<String> actions;
 static final class Row {String id,title,snippet,message,group;boolean selected;}
 static final class Index {String signature,title;final List<String[]> messages=new ArrayList<>();}
 HistoryBrowser(Context context,Open open,Consumer<String> actions){
  this.context=context;this.open=open;this.actions=actions;palette=ThemePalette.resolve(context);list=new ListView(context);list.setDivider(null);list.setClipToPadding(false);list.setPadding(0,UiStyle.dp(context,8),0,UiStyle.dp(context,16));
  adapter=new BaseAdapter(){
   public int getCount(){return rows.size();}public Object getItem(int i){return rows.get(i);}public long getItemId(int i){return i;}
   public View getView(int position,View convert,ViewGroup parent){
    LinearLayout box;if(convert instanceof LinearLayout)box=(LinearLayout)convert;else{box=new LinearLayout(context);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(UiStyle.dp(context,14),0,UiStyle.dp(context,14),0);for(int size:new int[]{12,15,12})box.addView(UiStyle.text(context,"",size,palette.ink,false),new LinearLayout.LayoutParams(-1,-2));}
    Row row=rows.get(position);TextView group=(TextView)box.getChildAt(0),title=(TextView)box.getChildAt(1),snippet=(TextView)box.getChildAt(2);
    group.setText(row.group);group.setVisibility(row.group.isEmpty()?View.GONE:View.VISIBLE);group.setTextColor(palette.muted);group.setPadding(0,UiStyle.dp(context,14),0,UiStyle.dp(context,8));
    title.setText(row.title);title.setTextColor(row.selected?palette.primary:palette.ink);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);title.setMinHeight(UiStyle.dp(context,48));title.setGravity(Gravity.CENTER_VERTICAL);
    snippet.setText(highlight(row.snippet));snippet.setTextColor(palette.muted);snippet.setMaxLines(2);snippet.setEllipsize(TextUtils.TruncateAt.END);snippet.setPadding(0,0,0,UiStyle.dp(context,10));snippet.setVisibility(row.snippet.isEmpty()?View.GONE:View.VISIBLE);
    UiStyle.clickable(box,row.selected?androidx.core.graphics.ColorUtils.blendARGB(palette.page,palette.primary,.08f):palette.page,12,false);
    box.setContentDescription(row.title+(row.snippet.isEmpty()?"":"，"+row.snippet)+"，长按管理");return box;
   }
  };
  list.setAdapter(adapter);list.setOnItemClickListener((p,v,i,id)->{Row r=rows.get(i);open.open(r.id,r.message,query);});list.setOnItemLongClickListener((p,v,i,id)->{actions.accept(rows.get(i).id);return true;});
 }
 View view(){return list;}
 void filter(String query,boolean archived){this.query=query.trim().toLowerCase(Locale.ROOT);this.archived=archived;refresh(sessions,current);}
 void refresh(JSONArray all,String current){sessions=all;this.current=current;int ticket=++generation;if(debounce!=null)main.removeCallbacks(debounce);debounce=()->search.execute(()->build(all,current,query,archived,ticket));main.postDelayed(debounce,query.isEmpty()?0:250);}
 private void build(JSONArray all,String current,String query,boolean archived,int ticket){
  List<Row> found=new ArrayList<>();Map<String,Long> times=new HashMap<>();long now=System.currentTimeMillis();
  for(int i=0;i<all.length();i++){if(ticket!=generation)return;JSONObject s=all.optJSONObject(i);if(s==null||s.optBoolean("archived")!=archived)continue;String id=s.optString("id");JSONArray messages=s.optJSONArray("messages");String signature=s.optLong("updatedAt")+":"+s.optString("title")+":"+s.optString("activeBranchId")+":"+(messages==null?0:messages.length());
   Index doc=index.get(id);if(doc==null||!signature.equals(doc.signature)){doc=new Index();doc.signature=signature;doc.title=s.optString("title","新对话");if(messages!=null)for(int j=0;j<messages.length();j++){JSONObject m=messages.optJSONObject(j);if(m!=null)doc.messages.add(new String[]{m.optString("id"),m.optString("text"),m.optString("text").toLowerCase(Locale.ROOT)});}index.put(id,doc);}
   Row row=new Row();row.id=id;row.title=(s.optBoolean("pinned")?"置顶 · ":"")+doc.title;row.selected=id.equals(current);row.snippet="";row.message="";boolean match=query.isEmpty()||doc.title.toLowerCase(Locale.ROOT).contains(query);
   if(!query.isEmpty())for(String[] m:doc.messages){int at=m[2].indexOf(query);if(at>=0){match=true;row.message=m[0];int start=Math.max(0,at-22),end=Math.min(m[1].length(),at+query.length()+65);row.snippet=(start>0?"…":"")+m[1].substring(start,end).replace('\n',' ')+(end<m[1].length()?"…":"");break;}}
   if(match){row.group=s.optBoolean("pinned")?"置顶":HistoryDates.group(s.optLong("updatedAt",s.optLong("createdAt",HistoryDates.parseLegacy(s.optString("time"),now))),now);found.add(row);times.put(id,s.optLong("updatedAt",s.optLong("createdAt",0)));}
  }
  found.sort((a,b)->{boolean ap=a.group.equals("置顶"),bp=b.group.equals("置顶");if(ap!=bp)return ap?-1:1;return Long.compare(times.get(b.id),times.get(a.id));});
  String previous="";for(Row r:found){String group=r.group;r.group=group.equals(previous)?"":group;previous=group;}
  main.post(()->{if(ticket!=generation)return;rows.clear();rows.addAll(found);adapter.notifyDataSetChanged();});
 }
 private CharSequence highlight(String text){SpannableString value=new SpannableString(text);if(query.isEmpty())return value;int at=text.toLowerCase(Locale.ROOT).indexOf(query);if(at>=0)value.setSpan(new ForegroundColorSpan(palette.primary),at,Math.min(text.length(),at+query.length()),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return value;}
 void close(){generation++;main.removeCallbacksAndMessages(null);search.shutdownNow();index.clear();}
}
