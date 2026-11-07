package com.changyu.ochat;

import android.os.SystemClock;
import android.view.*;
import android.widget.ScrollView;
import java.util.function.Consumer;

/** One retargetable scroll loop; a user gesture detaches until explicit resume. */
final class FollowOutput {
 private final ScrollView scroll;private final Consumer<Boolean> detached;
 private boolean following=true,pending;private long lastFrame;
 private final Runnable tick=this::flush;
 FollowOutput(ScrollView scroll,Consumer<Boolean> detached){
  this.scroll=scroll;this.detached=detached;
  scroll.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN)detach();return false;});
  scroll.setOnGenericMotionListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_SCROLL)detach();return false;});
  scroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->changed());
  if(scroll.getChildCount()>0)scroll.getChildAt(0).addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->changed());
 }
 private void flush(){
  pending=false;if(!following)return;
  int distance=bottom()-scroll.getScrollY();if(distance==0){lastFrame=0;return;}
  long now=SystemClock.uptimeMillis();long elapsed=lastFrame==0?16:Math.max(1,Math.min(64,now-lastFrame));lastFrame=now;
  int step;
  if(Math.abs(distance)>Math.max(1,scroll.getHeight())*3)step=distance;
  else step=(int)Math.copySign(Math.max(1,Math.round(Math.abs(distance)*(1-Math.exp(-elapsed/48.0)))),distance);
  scroll.scrollTo(0,scroll.getScrollY()+step);
  if(following&&bottom()!=scroll.getScrollY())changed();else lastFrame=0;
 }
 int bottom(){return scroll.getChildCount()==0?0:Math.max(0,scroll.getChildAt(0).getHeight()-scroll.getHeight()+scroll.getPaddingTop()+scroll.getPaddingBottom());}
 boolean nearBottom(){return bottom()-scroll.getScrollY()<=48*scroll.getResources().getDisplayMetrics().density;}
 boolean following(){return following;}
 void begin(){if(nearBottom())resume();else detach();}
 void resume(){following=true;detached.accept(false);changed();}
 void detach(){if(!following)return;following=false;cancel();detached.accept(true);}
 void cancel(){scroll.removeCallbacks(tick);pending=false;lastFrame=0;}
 void changed(){if(following&&!pending){pending=true;scroll.postOnAnimation(tick);}}
}
