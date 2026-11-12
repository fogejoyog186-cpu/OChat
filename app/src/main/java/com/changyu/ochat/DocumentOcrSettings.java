package com.changyu.ochat;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.*;
import androidx.appcompat.widget.SwitchCompat;
import org.json.*;
import java.util.*;

/** Separate PDF and image drafts; changes persist only when the model/provider editor is saved. */
final class DocumentOcrSettings {
 final SwitchCompat pdfEnabled,imageEnabled;
 final ChoiceSpinner pdfTarget,imageTarget;
 private final Section pdf,image;

 DocumentOcrSettings(Context context,SharedPreferences prefs,JSONObject provider,JSONObject model,LinearLayout body){
  JSONArray all=ModelStore.read(prefs);boolean included=false;
  for(int i=0;i<all.length();i++){
   JSONObject existing=all.optJSONObject(i);
   if(existing!=null&&provider.optString("id").equals(existing.optString("id"))){
    try{all.put(i,provider);}catch(JSONException ignored){}
    included=true;
   }
  }
  if(!included)all.put(provider);
  pdf=new Section(context,all,provider,model,body,true);
  image=new Section(context,all,provider,model,body,false);
  pdfEnabled=pdf.enabled;pdfTarget=pdf.target;imageEnabled=image.enabled;imageTarget=image.target;
  ThemePalette palette=ThemePalette.resolve(context);
  TextView hint=UiStyle.text(context,"两项分别开启、分别选择识别模型。开启的附件类型会先发送给对应识别服务，再将文字交给当前模型；未开启的类型按原方式发送。识别会产生调用费用，不会发送整段对话，结果会复用。",12,palette.muted,false);
  hint.setPadding(0,UiStyle.dp(context,8),0,UiStyle.dp(context,8));body.addView(hint);
 }

 private static final class Section {
  final boolean pdf;final SwitchCompat enabled;final ChoiceSpinner target;
  final List<String[]> choices=new ArrayList<>();

  Section(Context context,JSONArray all,JSONObject provider,JSONObject model,LinearLayout body,boolean pdf){
   this.pdf=pdf;ThemePalette palette=ThemePalette.resolve(context);String name=pdf?"PDF 转文字":"图片转文字";
   TextView title=UiStyle.text(context,name,17,palette.ink,true);
   title.setPadding(0,UiStyle.dp(context,18),0,UiStyle.dp(context,8));body.addView(title);
   enabled=new SwitchCompat(context);enabled.setText(name);enabled.setTextColor(palette.ink);
   enabled.setTag(pdf?"pdf-ocr-enabled":"image-ocr-enabled");enabled.setChecked(DocumentOcr.enabled(model,pdf));
   body.addView(enabled,new LinearLayout.LayoutParams(-1,UiStyle.dp(context,52)));
   target=new ChoiceSpinner(context,name+" · 识别模型");target.setTag(pdf?"pdf-ocr-target":"image-ocr-target");
   List<String> names=new ArrayList<>();names.add("请选择支持图像输入的模型");choices.add(new String[]{"",""});
   int selected=0;
   for(int i=0;i<all.length();i++){
    JSONObject p=all.optJSONObject(i);JSONArray models=p==null?null:p.optJSONArray("models");
    for(int j=0;models!=null&&j<models.length();j++){
     JSONObject m=models.optJSONObject(j);
     if(m==null||m.optString("id").isEmpty()||p.optString("id").equals(provider.optString("id"))&&m==model)continue;
     choices.add(new String[]{p.optString("id"),m.optString("id")});
     names.add(p.optString("name","供应商")+" · "+m.optString("displayName",m.optString("id")));
     if(p.optString("id").equals(model.optString(pdf?DocumentOcr.PROVIDER:DocumentOcr.IMAGE_PROVIDER))&&m.optString("id").equals(model.optString(pdf?DocumentOcr.MODEL:DocumentOcr.IMAGE_MODEL)))selected=choices.size()-1;
    }
   }
   target.setAdapter(new ArrayAdapter<>(context,android.R.layout.simple_spinner_dropdown_item,names));
   target.setSelection(selected);target.setEnabled(enabled.isChecked());
   enabled.setOnCheckedChangeListener((button,checked)->target.setEnabled(checked));
   body.addView(target,new LinearLayout.LayoutParams(-1,UiStyle.dp(context,56)));
  }
  String[] selected(){return choices.get(Math.max(0,target.getSelectedItemPosition()));}
  boolean valid(String providerId,String modelId){
   String[] selected=selected();
   if(enabled.isChecked()&&(selected[0].isEmpty()||providerId.equals(selected[0])&&modelId.equals(selected[1]))){
    Toast.makeText(enabled.getContext(),(pdf?"PDF 转文字":"图片转文字")+"：请选择另一个支持图像输入的模型",Toast.LENGTH_LONG).show();return false;
   }
   return true;
  }
  void save(JSONObject model)throws JSONException{
   String[] selected=selected();
   model.put(pdf?DocumentOcr.ENABLED:DocumentOcr.IMAGE_ENABLED,enabled.isChecked())
    .put(pdf?DocumentOcr.PROVIDER:DocumentOcr.IMAGE_PROVIDER,selected[0])
    .put(pdf?DocumentOcr.MODEL:DocumentOcr.IMAGE_MODEL,selected[1]);
  }
 }
 boolean save(JSONObject model,String providerId,String modelId)throws JSONException{
  if(!pdf.valid(providerId,modelId)||!image.valid(providerId,modelId))return false;
  pdf.save(model);image.save(model);return true;
 }
}
