package com.changyu.ochat;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.*;
import androidx.appcompat.widget.SwitchCompat;
import org.json.*;
import java.util.*;

/** A per-model draft; no preferences or provider records change until the editor is saved. */
final class DocumentOcrSettings {
 final SwitchCompat enabled;final ChoiceSpinner target;final List<String[]> choices=new ArrayList<>();
 DocumentOcrSettings(Context context,SharedPreferences prefs,JSONObject provider,JSONObject model,LinearLayout body){
  ThemePalette palette=ThemePalette.resolve(context);
  TextView title=UiStyle.text(context,"文档识别（PDF OCR）",17,palette.ink,true);title.setPadding(0,UiStyle.dp(context,18),0,UiStyle.dp(context,8));body.addView(title);
  enabled=new SwitchCompat(context);enabled.setText("先将 PDF / 图片转为文字");enabled.setTextColor(palette.ink);enabled.setTag("document-ocr-enabled");enabled.setChecked(DocumentOcr.enabled(model));body.addView(enabled,new LinearLayout.LayoutParams(-1,UiStyle.dp(context,52)));
  target=new ChoiceSpinner(context,"识别模型");target.setTag("document-ocr-target");
  List<String> names=new ArrayList<>();names.add("请选择支持图像输入的模型");choices.add(new String[]{"",""});
  JSONArray all=ModelStore.read(prefs);boolean included=false;for(int i=0;i<all.length();i++)if(provider.optString("id").equals(all.optJSONObject(i).optString("id"))){try{all.put(i,provider);}catch(JSONException ignored){}included=true;}
  if(!included)all.put(provider);
  int selected=0;for(int i=0;i<all.length();i++){
   JSONObject p=all.optJSONObject(i);JSONArray models=p==null?null:p.optJSONArray("models");
   for(int j=0;models!=null&&j<models.length();j++){
    JSONObject m=models.optJSONObject(j);if(m==null||m.optString("id").isEmpty()||p.optString("id").equals(provider.optString("id"))&&m==model)continue;
    choices.add(new String[]{p.optString("id"),m.optString("id")});names.add(p.optString("name","供应商")+" · "+m.optString("displayName",m.optString("id")));
    if(p.optString("id").equals(model.optString(DocumentOcr.PROVIDER))&&m.optString("id").equals(model.optString(DocumentOcr.MODEL)))selected=choices.size()-1;
   }
  }
  target.setAdapter(new ArrayAdapter<>(context,android.R.layout.simple_spinner_dropdown_item,names));target.setSelection(selected);target.setEnabled(enabled.isChecked());
  enabled.setOnCheckedChangeListener((button,checked)->target.setEnabled(checked));body.addView(target,new LinearLayout.LayoutParams(-1,UiStyle.dp(context,56)));
  TextView hint=UiStyle.text(context,"开启后附件先发送给指定识别服务，再将文字交给当前模型。不会发送整段对话；识别会产生该服务的调用费用。PDF 逐页识别，结果会复用。",12,palette.muted,false);hint.setPadding(0,UiStyle.dp(context,8),0,UiStyle.dp(context,8));body.addView(hint);
 }
 boolean save(JSONObject model,String providerId,String modelId)throws JSONException{
  int index=target.getSelectedItemPosition();String[] selected=choices.get(Math.max(0,index));
  if(enabled.isChecked()&&(selected[0].isEmpty()||providerId.equals(selected[0])&&modelId.equals(selected[1]))){Toast.makeText(enabled.getContext(),"请选择另一个支持图像输入的识别模型",Toast.LENGTH_LONG).show();return false;}
  model.put(DocumentOcr.ENABLED,enabled.isChecked()).put(DocumentOcr.PROVIDER,selected[0]).put(DocumentOcr.MODEL,selected[1]);return true;
 }
}
