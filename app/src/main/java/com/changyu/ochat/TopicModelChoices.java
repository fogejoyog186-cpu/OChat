package com.changyu.ochat;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Provider-scoped model choices; only configured model IDs are persisted. */
final class TopicModelChoices extends LinearLayout {
 private final SharedPreferences prefs;
 private final ChoiceSpinner provider,model;
 private final TextView notice;
 private final Map<String,String> remembered=new HashMap<>();
 private String[] providerIds=new String[0],providerNames=new String[0],modelIds=new String[0];
 private String selectedProvider;
 private boolean rebinding;
 TopicModelChoices(Context context,SharedPreferences prefs){
  super(context);this.prefs=prefs;setOrientation(VERTICAL);ThemePalette palette=ThemePalette.resolve(context);
  selectedProvider=prefs.getString("topicProviderId","").trim();
  if(selectedProvider.isEmpty()){
   String legacy=prefs.getString("topicProvider","");selectedProvider="__current__";
   if(!legacy.isEmpty()&&!"当前聊天供应商".equals(legacy)){
    selectedProvider=legacy;JSONArray all=ModelStore.read(prefs);
    for(int i=0;i<all.length();i++){JSONObject item=all.optJSONObject(i);if(item!=null&&(legacy.equals(item.optString("name"))||legacy.equals(item.optString("id")))){selectedProvider=item.optString("id");break;}}
   }
  }
  if(!"__current__".equals(selectedProvider)){
   JSONArray all=ModelStore.read(prefs);boolean found=false;for(int i=0;i<all.length();i++){JSONObject item=all.optJSONObject(i);if(item!=null&&selectedProvider.equals(item.optString("id")))found=true;}
   if(!found)for(int i=0;i<all.length();i++){JSONObject item=all.optJSONObject(i);if(item!=null&&selectedProvider.equals(item.optString("name"))){selectedProvider=item.optString("id");break;}}
  }
  remembered.put(selectedProvider,prefs.getString("topicModel","").trim());
  TextView providerLabel=UiStyle.text(context,"命名供应商",14,palette.ink,true);providerLabel.setPadding(0,UiStyle.dp(context,12),0,UiStyle.dp(context,8));addView(providerLabel);
  provider=new ChoiceSpinner(context,"命名供应商");addView(provider,new LayoutParams(-1,UiStyle.dp(context,56)));
  TextView modelLabel=UiStyle.text(context,"命名模型",14,palette.ink,true);modelLabel.setPadding(0,UiStyle.dp(context,16),0,UiStyle.dp(context,8));addView(modelLabel);
  model=new ChoiceSpinner(context,"命名模型");addView(model,new LayoutParams(-1,UiStyle.dp(context,56)));
  notice=UiStyle.text(context,"",12,palette.muted,false);notice.setPadding(UiStyle.dp(context,4),UiStyle.dp(context,8),UiStyle.dp(context,4),UiStyle.dp(context,8));addView(notice);
  provider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
   public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int index,long id){
    if(rebinding||index<0||index>=providerIds.length)return;selectedProvider=providerIds[index];renderModels();
   }
  });
  model.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
   public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int index,long id){
    if(!rebinding&&index>=0&&index<modelIds.length&&!modelIds[index].isEmpty())remembered.put(selectedProvider,modelIds[index]);
   }
  });
  refresh();
 }
 void refresh(){
  JSONArray all=ModelStore.read(prefs);List<String> ids=new ArrayList<>(),names=new ArrayList<>();
  ids.add("__current__");names.add("当前聊天供应商");
  for(int i=0;i<all.length();i++){JSONObject item=all.optJSONObject(i);if(item!=null){ids.add(item.optString("id"));names.add(item.optString("name","未命名供应商"));}}
  int index=ids.indexOf(selectedProvider);if(index<0){index=ids.size();ids.add(selectedProvider);names.add("供应商已删除，请重新选择");}
  providerIds=ids.toArray(new String[0]);providerNames=names.toArray(new String[0]);
  rebinding=true;provider.setAdapter(new ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,providerNames));provider.setSelection(index);rebinding=false;renderModels();
 }
 private JSONObject currentProvider(){
  String id="__current__".equals(selectedProvider)?prefs.getString("providerId",""):selectedProvider;JSONArray all=ModelStore.read(prefs);
  for(int i=0;i<all.length();i++){JSONObject item=all.optJSONObject(i);if(item!=null&&id.equals(item.optString("id")))return item;}
  return new JSONObject();
 }
 private void renderModels(){
  JSONObject owner=currentProvider();JSONArray all=owner.optJSONArray("models");List<String> ids=new ArrayList<>(),names=new ArrayList<>();
  ids.add("");names.add("请选择已配置模型");
  for(int i=0;all!=null&&i<all.length();i++){
   JSONObject item=all.optJSONObject(i);if(item==null||item.optString("id").trim().isEmpty())continue;
   String id=item.optString("id"),name=item.optString("displayName",id);if(name.trim().isEmpty())name=id;
   ids.add(id);names.add(name.equals(id)?id:name+" · "+id);
  }
  String wanted=remembered.get(selectedProvider);
  if(wanted==null||wanted.isEmpty()){wanted=owner.optString("selectedModelId");if(!ids.contains(wanted)&&ids.size()>1)wanted=ids.get(1);}
  int index=ids.indexOf(wanted);if(index<0)index=0;
  if(ids.size()==1)names.set(0,"暂无已配置模型");
  modelIds=ids.toArray(new String[0]);rebinding=true;
  model.setAdapter(new ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,names));model.setSelection(index);model.setEnabled(ids.size()>1);rebinding=false;
  if(index>0)remembered.put(selectedProvider,modelIds[index]);
  notice.setText(ids.size()==1?"此供应商还没有模型，请先在下方管理模型。":index==0?"原模型不在此供应商的配置中，请重新选择。":"可选择此供应商已配置的 "+(ids.size()-1)+" 个模型");
 }
 String providerId(){return selectedProvider;}
 String providerName(){int index=Arrays.asList(providerIds).indexOf(selectedProvider);return index>=0?providerNames[index]:"";}
 String modelId(){int index=model.getSelectedItemPosition();return index>=0&&index<modelIds.length?modelIds[index]:"";}
 boolean validSelection(){
  String selected=modelId();if(selected.isEmpty())return false;JSONArray models=currentProvider().optJSONArray("models");
  for(int i=0;models!=null&&i<models.length();i++){JSONObject item=models.optJSONObject(i);if(item!=null&&selected.equals(item.optString("id")))return true;}
  return false;
 }
}
