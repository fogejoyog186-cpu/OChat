package com.changyu.ochat;

/** Distance-based particle clock: changing intensity never resets the particles' phase. */
final class ReasoningParticleFlow {
 private long lastFrame=-1;
 private double distanceDp;
 static double speedDpPerSecond(float effort){
  return 36+220*Math.max(0,Math.min(1,effort));
 }
 void advance(long now,float effort){
  if(lastFrame>=0){
   long elapsed=Math.max(0,Math.min(64,now-lastFrame));
   distanceDp+=speedDpPerSecond(effort)*elapsed/1000.0;
  }
  lastFrame=now;
 }
 double distanceDp(){return distanceDp;}
 void pause(){lastFrame=-1;}
}
