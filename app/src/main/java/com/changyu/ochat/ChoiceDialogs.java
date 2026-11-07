package com.changyu.ochat;

import android.app.AlertDialog;
import android.content.Context;
import android.view.*;
import android.widget.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Choice changes are local until the explicit confirm action. Back and cancel discard them. */
final class ChoiceDialogs {
 static PanelDialog single(Context context,String title,String[] labels,int selected,IntConsumer confirmed){
  int[] pending={selected};boolean[] checked=new boolean[labels.length];if(selected>=0&&selected<labels.length)checked[selected]=true;
  return show(context,title,labels,checked,false,()->{for(int i=0;i<checked.length;i++)if(checked[i]){pending[0]=i;break;}confirmed.accept(pending[0]);});
 }
 static PanelDialog multiple(Context context,String title,String[] labels,boolean[] initial,Consumer<boolean[]> confirmed){
  boolean[] pending=initial.clone();return show(context,title,labels,pending,true,()->confirmed.accept(pending.clone()));
 }
 private static PanelDialog show(Context context,String title,String[] labels,boolean[] checked,boolean multiple,Runnable confirmed){
  ThemePalette palette=ThemePalette.resolve(context);LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);
  ListView list=new ListView(context);list.setDivider(null);list.setChoiceMode(ListView.CHOICE_MODE_NONE);list.setSelector(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
  BaseAdapter adapter=new BaseAdapter(){
   public int getCount(){return labels.length;}public Object getItem(int i){return labels[i];}public long getItemId(int i){return i;}
   public View getView(int i,View convert,ViewGroup parent){
    LinearLayout row;
    if(convert instanceof LinearLayout)row=(LinearLayout)convert;else{
     row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(context,14),dp(context,10),dp(context,12),dp(context,10));row.setMinimumHeight(dp(context,56));
     TextView label=UiStyle.text(context,"",15,palette.ink,false);label.setMaxLines(3);label.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
     CompoundButton mark=multiple?new androidx.appcompat.widget.AppCompatCheckBox(context):new androidx.appcompat.widget.AppCompatRadioButton(context);
     mark.setButtonTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{palette.primary,palette.muted}));
     mark.setPadding(0,0,0,0);mark.setClickable(false);mark.setFocusable(false);mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(mark,new LinearLayout.LayoutParams(dp(context,36),dp(context,36)));
    }
    UiStyle.textIfChanged((TextView)row.getChildAt(0),labels[i]);((CompoundButton)row.getChildAt(1)).setChecked(checked[i]);
    row.setSelected(false);row.setActivated(checked[i]);
    row.setContentDescription(labels[i]+(checked[i]?"，已选择":""));
    UiStyle.clickable(row,checked[i]?androidx.core.graphics.ColorUtils.blendARGB(palette.surface,palette.primary,.08f):palette.surface,14,false);
    return row;
   }
  };
  list.setAdapter(adapter);
  body.addView(list,new LinearLayout.LayoutParams(-1,labels.length<=4?dp(context,Math.max(1,labels.length)*64):-1));
  if(labels.length==0){TextView empty=UiStyle.text(context,"暂无可选项",14,palette.muted,false);empty.setPadding(dp(context,12),dp(context,16),dp(context,12),dp(context,16));body.removeView(list);body.addView(empty);}
  PanelDialog dialog=new PanelDialog(context,title,"选择后点击确认应用",body);if(labels.length<=4)dialog.compact();
  dialog.action(AlertDialog.BUTTON_NEGATIVE,"取消",null).action(AlertDialog.BUTTON_POSITIVE,"确认",confirmed);
  list.setOnItemClickListener((parent,view,index,id)->{if(multiple)checked[index]=!checked[index];else{java.util.Arrays.fill(checked,false);checked[index]=true;}adapter.notifyDataSetChanged();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);});
  dialog.show();boolean any=multiple;for(boolean value:checked)any|=value;dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(labels.length>0&&any);return dialog;
 }
 private static int dp(Context context,int value){return UiStyle.dp(context,value);}
 private ChoiceDialogs(){}
}
