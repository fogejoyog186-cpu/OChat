package com.changyu.ochat;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.widget.Button;
import android.widget.TextView;

/** Local platform dialog theme; all colors installed before window attachment. */
final class PaletteDialogs {
 static AlertDialog.Builder builder(Context context) {
  return new AlertDialog.Builder(EditorDialogs.themedContext(context)) {
   @Override public AlertDialog create() {
    AlertDialog dialog=super.create();
    prepare(dialog);
    return dialog;
   }
   @Override public AlertDialog show() {
    AlertDialog dialog=create();dialog.show();return dialog;
   }
  };
 }
 static void prepare(AlertDialog dialog) {
  // The builder has already created the AlertDialog before calling this hook. Calling
  // dialog.create() here re-enters the overridden builder create path and causes a
  // StackOverflowError when any menu action is tapped.
  ThemePalette p=ThemePalette.resolve(dialog.getContext());
  if(dialog.getWindow()!=null) {
   dialog.getWindow().setBackgroundDrawable(new ColorDrawable(p.surface));
   // Only framework title/message: never recolor custom controls or replace text buffers.
   for(int id:new int[]{android.R.id.message,dialog.getContext().getResources().getIdentifier("alertTitle","id","android")}) {
    TextView text=dialog.findViewById(id);if(text!=null)text.setTextColor(p.ink);
   }
  }
  for(int id:new int[]{AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_POSITIVE}) {
   Button button=dialog.getButton(id);
   if(button!=null) {
    button.setBackgroundTintList(null);
    if(button instanceof androidx.core.view.TintableBackgroundView)
     ((androidx.core.view.TintableBackgroundView)button).setSupportBackgroundTintList(null);
    button.setBackground(new ColorDrawable(p.surface));
    button.setTextColor(ThemePalette.readable(p.primary,p.surface));
   }
  }
 }
}
