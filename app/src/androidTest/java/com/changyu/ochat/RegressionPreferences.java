package com.changyu.ochat;
import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import static org.junit.Assert.*;

/** Preserve the emulator's existing app state around a settings/session regression. */
final class RegressionPreferences implements AutoCloseable {
 final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
 final SharedPreferences prefs=context.getSharedPreferences("chat",Context.MODE_PRIVATE);
 private final Map<String,?> backup;
 RegressionPreferences()throws Exception{assertEquals("do not interrupt an existing generation",0,ChatGenerationService.activeCount());SessionRepository.get(prefs).flush();backup=new HashMap<>(prefs.getAll());prefs.edit().clear().putString("providers","[]").commit();SessionLaunchPolicy.markTaskRemoved();}
 @Override public void close()throws Exception{
  try{SessionRepository.get(prefs).flush();}finally{SharedPreferences.Editor editor=prefs.edit().clear();
  for(Map.Entry<String,?> entry:backup.entrySet()){
   Object value=entry.getValue();String key=entry.getKey();
   if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
   else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Long)editor.putLong(key,(Long)value);else if(value instanceof Float)editor.putFloat(key,(Float)value);
   else if(value instanceof Set)editor.putStringSet(key,new HashSet<>((Set<String>)value));
  }
  editor.commit();SessionLaunchPolicy.markTaskRemoved();}
 }
}
