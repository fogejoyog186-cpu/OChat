package com.changyu.ochat;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

/** Explicit local theme, inflated and colored before the window can become visible. */
final class EditorDialogs {
 static Context themedContext(Context context){
  return new ContextThemeWrapper(context,ThemePalette.resolve(context).dark?R.style.EditorDialogTheme_Dark:R.style.EditorDialogTheme_Light);
 }
 static EditText editor(Context themed){
  EditText editor=(EditText)LayoutInflater.from(themed).inflate(R.layout.dialog_editor,null,false);
  ThemePalette p=ThemePalette.resolve(themed);
  editor.setTextColor(p.ink);editor.setHintTextColor(p.muted);editor.setBackgroundColor(p.input);
  return editor;
 }
 static void prepare(AlertDialog dialog,EditText editor){
  // The caller has already created the dialog. Calling create() here is unsafe: dialogs
  // returned by PaletteDialogs.builder() use an overridden create() hook, so re-entering
  // it from preparation can recurse while the dialog is being shown.
  ThemePalette p=ThemePalette.resolve(editor.getContext());
  Window window=dialog.getWindow();
  if(window!=null){
   window.setBackgroundDrawable(new ColorDrawable(p.surface));
   window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
   androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,true);
   color(window.getDecorView(),editor,p);
  }
  // Run last: recursive title coloring must not overwrite action foregrounds.
  PaletteDialogs.prepare(dialog);
 }
 private static void color(View view,EditText editor,ThemePalette p){
  if(view==editor || view instanceof android.widget.Button)return;
  if(view instanceof TextView)((TextView)view).setTextColor(p.ink);
  if(view instanceof ViewGroup){view.setBackgroundColor(p.surface);ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)color(g.getChildAt(i),editor,p);}
 }
}
