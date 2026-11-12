package com.changyu.ochat;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.*;
import androidx.appcompat.widget.SwitchCompat;
import org.json.*;
import java.util.*;

final class DocumentOcrSettings {
 DocumentOcrSettings(Context context,SharedPreferences prefs,JSONObject provider,JSONObject model,LinearLayout body){
  ThemePalette palette=ThemePalette.resolve(context);
   List<String> names=new ArrayList<>();names.add("请选择支持图像输入的模型");choices.add(new String[]{"",""});
    JSONObject p=all.optJSONObject(i);JSONArray models=p==null?null:p.optJSONArray("models");
    for(int j=0;models!=null&&j<models.length();j++){
   }
  }
 }
 boolean save(JSONObject model,String providerId,String modelId)throws JSONException{
 }
}
