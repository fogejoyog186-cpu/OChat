package com.changyu.ochat;

import android.os.Handler;
import android.os.Looper;
import java.util.function.Consumer;

/** Lossless producer buffer; at most one pending UI render, never one task per token. */
final class StreamingMarkdown {
 private final Handler main=new Handler(Looper.getMainLooper());
 private final StringBuffer source;
 private final Consumer<String> render;
 private boolean pending,closed;
 private final Runnable flush=this::flush;
 StreamingMarkdown(StringBuffer source,Consumer<String> render){this.source=source;this.render=render;}
 synchronized void append(String delta){
  if(closed||delta.isEmpty())return;
  source.append(delta);
  if(!pending){pending=true;main.postDelayed(flush,renderDelay());}
 }
 synchronized void replace(String canonical){if(closed||canonical.contentEquals(source))return;source.setLength(0);source.append(canonical);if(!pending){pending=true;main.postDelayed(flush,renderDelay());}}
 private long renderDelay(){int length=source.length();return length<12000?64:length<40000?96:160;}
 private void flush(){
  String snapshot;
  synchronized(this){pending=false;if(closed)return;snapshot=source.toString();}
  render.accept(snapshot);
 }
 synchronized String seal(){closed=true;pending=false;main.removeCallbacks(flush);return source.toString();}
 /** Called on main before persistence, including stop/error/fallback. Cancels stale previews. */
 void finish(String canonical){
  if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Final render must run on main");
  synchronized(this){closed=true;pending=false;main.removeCallbacks(flush);}
  render.accept(canonical);
 }
}
