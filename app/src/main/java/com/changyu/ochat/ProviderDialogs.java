package com.changyu.ochat;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.*;
import org.json.*;

final class ProviderDialogs {
 static void confirmDelete(Context context,String id,Runnable deleted){
  SharedPreferences prefs=context.getSharedPreferences("chat",Context.MODE_PRIVATE);JSONObject target=null;JSONArray all=ModelStore.read(prefs);
  for(int i=0;i<all.length();i++){JSONObject item=all.optJSONObject(i);if(item!=null&&id.equals(item.optString("id"))){target=item;break;}}
  if(target==null)return;
  ThemePalette palette=ThemePalette.resolve(context);
  TextView body=UiStyle.text(context,"删除此供应商及其模型配置？历史对话会保留。",15,palette.ink,false);body.setPadding(UiStyle.dp(context,8),UiStyle.dp(context,12),UiStyle.dp(context,8),UiStyle.dp(context,12));
  PanelDialog dialog=new PanelDialog(context,"删除供应商",target.optString("name","未命名供应商"),body).compact();
  dialog.action(AlertDialog.BUTTON_NEGATIVE,"取消",null).action(AlertDialog.BUTTON_POSITIVE,"删除",null);dialog.show();
  dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   try{ModelStore.deleteProvider(prefs,id);dialog.dismiss();deleted.run();}
   catch(JSONException failure){Toast.makeText(context,"删除失败，请重试",Toast.LENGTH_SHORT).show();}
  });
 }
 private ProviderDialogs(){}
}
