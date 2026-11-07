package com.changyu.ochat;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.SeekBar;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.core.graphics.ColorUtils;

/** Native discrete input with a shared animated position for the fill and thumb. */
final class ReasoningSlider extends AppCompatSeekBar {
 private static final PathInterpolator EASE=new PathInterpolator(.23f,1f,.32f,1f);
 private static final int PARTICLES=14;
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final RectF rail=new RectF();
 private final RectF streak=new RectF();
 private final Path railClip=new Path();
 private final Matrix shimmerMatrix=new Matrix();
 private final ValueAnimator positionAnimation=ValueAnimator.ofFloat(0,0);
 private final ValueAnimator pressAnimation=ValueAnimator.ofFloat(0,0);
 private final ReasoningParticleFlow flow=new ReasoningParticleFlow();
 private final float[] origins=new float[PARTICLES],lanes=new float[PARTICLES],
  scales=new float[PARTICLES],speeds=new float[PARTICLES],opacities=new float[PARTICLES];
 private final Runnable particleFrame=this::advanceParticles;
 private final float density;
 private int active,selectedTick;
 private final int inactive,remainingTick,outline;
 private final Knob knob;
 private LinearGradient shimmerShader;
 private OnSeekBarChangeListener changeListener;
 private float position,targetPosition,expansion,targetExpansion;
 private boolean attached,automatic,framePending,motionEnabled=true,firstStopDisabled=true;
 private long motionCheckedAt=-1;

 ReasoningSlider(Context context){this(context,ThemePalette.resolve(context));}
 ReasoningSlider(Context context,ThemePalette palette){
  super(context);
  density=getResources().getDisplayMetrics().density;
  active=palette.primary;
  inactive=ColorUtils.blendARGB(palette.page,palette.ink,palette.dark?.12f:.06f);
  selectedTick=ColorUtils.blendARGB(active,palette.onPrimary,.38f);
  remainingTick=ColorUtils.blendARGB(inactive,palette.muted,.52f);
  outline=ColorUtils.blendARGB(inactive,palette.ink,palette.dark?.10f:.05f);
  for(int i=0;i<PARTICLES;i++){
   origins[i]=(i+noise(i,3)*.6f)/PARTICLES;lanes[i]=.18f+noise(i,7)*.64f;
   scales[i]=.7f+noise(i,11)*.7f;speeds[i]=.8f+noise(i,17)*.4f;
   opacities[i]=.45f+noise(i,23)*.55f;
  }
  positionAnimation.setDuration(300);positionAnimation.setInterpolator(EASE);
  positionAnimation.addUpdateListener(a->{position=(Float)a.getAnimatedValue();invalidate();});
  pressAnimation.setDuration(220);pressAnimation.setInterpolator(EASE);
  pressAnimation.addUpdateListener(a->{expansion=(Float)a.getAnimatedValue();invalidate();});
  setLayoutDirection(LAYOUT_DIRECTION_LTR);setBackground(null);
  setProgressDrawable(new ColorDrawable(Color.TRANSPARENT));setThumbTintList(null);
  setTickMark(null);setSplitTrack(false);
  knob=new Knob(density,palette.primary);setThumb(knob);setThumbOffset(px(18));
  setPadding(px(18),0,px(18),0);setMinimumHeight(px(56));setKeyProgressIncrement(1);
  super.setOnSeekBarChangeListener(new OnSeekBarChangeListener(){
   public void onStartTrackingTouch(SeekBar bar){if(changeListener!=null)changeListener.onStartTrackingTouch(bar);}
   public void onStopTrackingTouch(SeekBar bar){if(changeListener!=null)changeListener.onStopTrackingTouch(bar);}
   public void onProgressChanged(SeekBar bar,int value,boolean user){
    moveToSelection();updateParticleScheduling();
    if(changeListener!=null)changeListener.onProgressChanged(bar,value,user);
   }
  });
 }

 @Override public void setOnSeekBarChangeListener(OnSeekBarChangeListener listener){changeListener=listener;}

 void setColor(int color){
  active=color|0xff000000;selectedTick=ColorUtils.blendARGB(active,Color.WHITE,.38f);knob.focusColor=active;invalidate();
 }
 void setAutomatic(boolean automatic){
  if(this.automatic==automatic)return;this.automatic=automatic;animate().cancel();
  float alpha=automatic?.48f:1f;
  if(canAnimate())animate().alpha(alpha).setDuration(180).setInterpolator(EASE).start();else setAlpha(alpha);
  updateParticleScheduling();invalidate();
 }
 void setFirstStopDisabled(boolean disabled){if(firstStopDisabled==disabled)return;firstStopDisabled=disabled;if(getWidth()>0)rebuildRail(getWidth(),getHeight());moveToSelection();updateParticleScheduling();}
 private boolean manualThinking(){return !automatic&&(!firstStopDisabled||getProgress()>0);}
 private float fraction(){return firstStopDisabled?(getMax()==0?0:getProgress()/(float)getMax()):(getProgress()+1f)/(getMax()+1f);}
 private void moveToSelection(){
  float target=fraction();if(target==targetPosition&&positionAnimation.isRunning())return;
  targetPosition=target;positionAnimation.cancel();
  if(canAnimate()&&Math.abs(position-target)>.0001f){positionAnimation.setFloatValues(position,target);positionAnimation.start();}
  else{position=target;invalidate();}
 }
 private void animatePress(){
  float target=isPressed()?1:0;if(target==targetExpansion)return;
  targetExpansion=target;pressAnimation.cancel();
  if(canAnimate()){pressAnimation.setFloatValues(expansion,target);pressAnimation.start();}
  else{expansion=target;invalidate();}
 }
 private boolean animationsEnabled(){
  long now=SystemClock.uptimeMillis();
  if(motionCheckedAt<0||now-motionCheckedAt>=250){
   motionCheckedAt=now;
   motionEnabled=Build.VERSION.SDK_INT>=26?ValueAnimator.areAnimatorsEnabled():
    Settings.Global.getFloat(getContext().getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1)>0;
  }
  return motionEnabled;
 }
 private boolean canAnimate(){return attached&&isShown()&&isEnabled()&&getWindowVisibility()==VISIBLE&&animationsEnabled();}
 private boolean particlesAllowed(){return manualThinking()&&rail.width()>0&&canAnimate();}
 private void updateParticleScheduling(){
  if(positionAnimation==null)return;
  if(particlesAllowed()){
   if(!framePending){framePending=true;postOnAnimation(particleFrame);}
  }else stopParticles();
 }
 private void stopParticles(){removeCallbacks(particleFrame);framePending=false;flow.pause();}
 private void advanceParticles(){
  framePending=false;
  if(!particlesAllowed()){flow.pause();if(!motionEnabled)settleVisuals();return;}
  flow.advance(SystemClock.uptimeMillis(),position);invalidate();updateParticleScheduling();
 }
 private void settleVisuals(){
  if(positionAnimation==null)return;
  positionAnimation.cancel();pressAnimation.cancel();animate().cancel();stopParticles();
  position=targetPosition=fraction();expansion=targetExpansion=isPressed()?1:0;
  setAlpha(automatic?.48f:1f);invalidate();
 }
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;settleVisuals();updateParticleScheduling();}
 @Override protected void onDetachedFromWindow(){attached=false;settleVisuals();super.onDetachedFromWindow();}
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);refreshVisibility();}
 @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);refreshVisibility();}
 private void refreshVisibility(){
  if(positionAnimation==null)return;
  if(canAnimate())updateParticleScheduling();else settleVisuals();
 }
 @Override protected void drawableStateChanged(){super.drawableStateChanged();if(pressAnimation!=null)animatePress();}
 @Override public void jumpDrawablesToCurrentState(){super.jumpDrawablesToCurrentState();settleVisuals();updateParticleScheduling();}

 @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){
  super.onSizeChanged(width,height,oldWidth,oldHeight);rebuildRail(width,height);
 }
 private void rebuildRail(int width,int height){
  float center=height/2f,left=px(18),right=Math.max(left,width-px(18));
  int inputLeft=Math.round(firstStopDisabled?left:left+(right-left)/(getMax()+1f));
  if(getPaddingLeft()!=inputLeft||getPaddingRight()!=px(18))setPadding(inputLeft,0,px(18),0);
  rail.set(left,center-px(12),right,center+px(12));
  railClip.reset();railClip.addRoundRect(rail,px(12),px(12),Path.Direction.CW);
  if(rail.width()>0){
   shimmerShader=new LinearGradient(0,0,px(80),0,new int[]{0x00FFFFFF,0x30FFFFFF,0x00FFFFFF},new float[]{0,.5f,1},Shader.TileMode.CLAMP);
  }
  updateParticleScheduling();
 }

 @Override protected synchronized void onDraw(Canvas canvas){
  if(rail.width()<=0)return;
  paint.setStyle(Paint.Style.FILL);paint.setShader(null);paint.setColor(inactive);
  canvas.drawRoundRect(rail,px(12),px(12),paint);
  int save=canvas.save();canvas.clipPath(railClip);
  float end=rail.left+rail.width()*position;
  paint.setColor(active);canvas.drawRect(rail.left,rail.top,end,rail.bottom,paint);
  if(manualThinking()&&motionEnabled){
   int fill=canvas.save();canvas.clipRect(rail.left,rail.top,end,rail.bottom);
   drawParticles(canvas,end);canvas.restoreToCount(fill);
  }
  for(int stop=0;stop<=getMax();stop++){
   float location=firstStopDisabled?(getMax()==0?0:stop/(float)getMax()):(stop+1f)/(getMax()+1f);
   float passed=Math.max(0,Math.min(1,(position-location)*(firstStopDisabled?getMax():getMax()+1)+.5f));
   int color=ColorUtils.blendARGB(remainingTick,selectedTick,passed);
   if(!automatic&&motionEnabled)color=ColorUtils.setAlphaComponent(color,Math.round(255*(1-passed*position*.55f)));
   paint.setColor(color);canvas.drawCircle(rail.left+rail.width()*location,rail.centerY(),2*density,paint);
  }
  canvas.restoreToCount(save);
  paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(density*.5f);paint.setColor(outline);
  canvas.drawRoundRect(rail,px(12),px(12),paint);paint.setStyle(Paint.Style.FILL);
  // Keep both visual positions on the same clock; native input still commits discrete values.
  knob.drawAt(canvas,rail.left+rail.width()*position,rail.centerY(),expansion);
 }
 private void drawParticles(Canvas canvas,float end){
  double travel=flow.distanceDp();float loop=rail.width()/density+40;
  if(position>.5f&&shimmerShader!=null){
   float glint=rail.left-px(80)+(float)((travel*.25)%(loop+120))*density;
   shimmerMatrix.setTranslate(glint,0);shimmerShader.setLocalMatrix(shimmerMatrix);
   paint.setColor(Color.WHITE);paint.setAlpha(Math.round((position-.5f)*2*255));paint.setShader(shimmerShader);
   canvas.drawRect(rail.left,rail.top,end,rail.bottom,paint);paint.setShader(null);paint.setAlpha(255);
  }
  for(int i=0;i<PARTICLES;i++){
   float x=rail.left+((float)((origins[i]*loop+travel*speeds[i])%loop)-20)*density;
   float edge=Math.max(0,Math.min(1,Math.min((x-rail.left)/px(12),(end-x)/px(12))));
   if(edge<=0)continue;
   float y=rail.top+rail.height()*lanes[i]+(float)Math.sin(travel*.018+i)*density*.45f;
   float radius=scales[i]*density,tail=(3+position*8)*density;
   int alpha=Math.round(210*opacities[i]*edge);
   paint.setColor(ColorUtils.setAlphaComponent(Color.WHITE,alpha/5));
   streak.set(x-tail,y-radius*.45f,x,y+radius*.45f);canvas.drawRoundRect(streak,radius,radius,paint);
   paint.setColor(ColorUtils.setAlphaComponent(Color.WHITE,alpha/8));canvas.drawCircle(x,y,radius*2.6f,paint);
   paint.setColor(ColorUtils.setAlphaComponent(Color.WHITE,alpha));canvas.drawCircle(x,y,radius,paint);
  }
 }
 private static float noise(int index,int salt){int value=(index+1)*1103515245+salt*12345;value^=value>>>16;return(value&0xffff)/65535f;}
 private int px(int value){return Math.round(value*density);}

 private static final class Knob extends Drawable {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float density;private int focusColor;private int alpha=255;
  Knob(float density,int focusColor){this.density=density;this.focusColor=focusColor;}
  @Override public int getIntrinsicWidth(){return Math.round(36*density);}
  @Override public int getIntrinsicHeight(){return getIntrinsicWidth();}
  @Override public boolean isStateful(){return true;}
  @Override protected boolean onStateChange(int[] states){invalidateSelf();return true;}
  private boolean hasState(int state){for(int value:getState())if(value==state)return true;return false;}
  private void color(int color){paint.setColor(ColorUtils.setAlphaComponent(color,Color.alpha(color)*alpha/255));}
  @Override public void draw(Canvas canvas){Rect bounds=getBounds();drawAt(canvas,bounds.exactCenterX(),bounds.exactCenterY(),hasState(android.R.attr.state_pressed)?1:0);}
  void drawAt(Canvas canvas,float x,float y,float expansion){
   float radius=(14+2*expansion)*density;
   if(hasState(android.R.attr.state_focused)){
    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2*density);color(focusColor);
    canvas.drawCircle(x,y,radius+2*density,paint);
   }
   paint.setStyle(Paint.Style.FILL);color(0x18000000);canvas.drawCircle(x,y+density,radius,paint);
   color(Color.WHITE);canvas.drawCircle(x,y,radius,paint);
   paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.75f*density);color(0x33000000);
   canvas.drawCircle(x,y,radius-.375f*density,paint);
  }
  @Override public void setAlpha(int alpha){this.alpha=alpha;invalidateSelf();}
  @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
  @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
