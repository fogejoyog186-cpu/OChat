package com.changyu.ochat;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReasoningParticleFlowTest {
 @Test public void everyHigherStopMovesFurtherInTheSameTime(){
  double previous=-1;
  for(int stop=0;stop<=6;stop++){
   ReasoningParticleFlow flow=new ReasoningParticleFlow();float effort=stop/6f;
   for(int frame=0;frame<=60;frame++)flow.advance(frame*16L,effort);
   assertTrue(flow.distanceDp()>previous);previous=flow.distanceDp();
  }
 }
 @Test public void movementIsIndependentOfFrameRate(){
  ReasoningParticleFlow sixty=new ReasoningParticleFlow(),oneTwenty=new ReasoningParticleFlow();
  for(int frame=0;frame<=60;frame++)sixty.advance(frame*16L,.5f);
  for(int frame=0;frame<=120;frame++)oneTwenty.advance(frame*8L,.5f);
  assertEquals(sixty.distanceDp(),oneTwenty.distanceDp(),.0001);
 }
 @Test public void changingIntensityKeepsParticlesMovingFromTheirCurrentPosition(){
  ReasoningParticleFlow flow=new ReasoningParticleFlow();
  flow.advance(0,.2f);flow.advance(16,.2f);double before=flow.distanceDp();
  flow.advance(32,.9f);assertTrue(flow.distanceDp()>before);
  double fastStep=flow.distanceDp()-before;
  flow.advance(48,.2f);assertTrue(flow.distanceDp()-before-fastStep<fastStep);
 }
 @Test public void pauseDoesNotCatchUpTimeSpentInBackground(){
  ReasoningParticleFlow flow=new ReasoningParticleFlow();
  flow.advance(0,1);flow.advance(16,1);double before=flow.distanceDp();flow.pause();
  flow.advance(600000,1);assertEquals(before,flow.distanceDp(),0);
  flow.advance(600016,1);assertTrue(flow.distanceDp()>before);
 }
 @Test public void stalledFrameCannotSendParticlesJumpingAcrossTheTrack(){
  ReasoningParticleFlow flow=new ReasoningParticleFlow();
  flow.advance(0,1);flow.advance(10000,1);
  assertTrue(flow.distanceDp()<20);
 }
}
