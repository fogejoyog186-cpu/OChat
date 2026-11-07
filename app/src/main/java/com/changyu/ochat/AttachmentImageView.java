package com.changyu.ochat;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.*;
import androidx.appcompat.widget.AppCompatImageView;

/** Fit the whole picture initially; gestures only change the picture, never the surrounding panel. */
final class AttachmentImageView extends AppCompatImageView {
 private final Matrix transform=new Matrix();
 private final RectF imageBounds=new RectF();
 private final ScaleGestureDetector scaling;
 private final GestureDetector gestures;
 private float zoom=1;private boolean clicked;

 AttachmentImageView(Context context){
  super(context);setScaleType(ScaleType.MATRIX);setContentDescription("图片，可双指缩放");setFocusable(true);
  scaling=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
   @Override public boolean onScale(ScaleGestureDetector detector){
    float next=Math.max(1,Math.min(5,zoom*detector.getScaleFactor()));
    transform.postScale(next/zoom,next/zoom,detector.getFocusX(),detector.getFocusY());zoom=next;constrain();return true;
   }
  });
  gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
   @Override public boolean onDown(MotionEvent event){return true;}
   @Override public boolean onSingleTapUp(MotionEvent event){clicked=true;return true;}
   @Override public boolean onDoubleTap(MotionEvent event){
    if(zoom>1.01f)fit();else{transform.postScale(2.5f,2.5f,event.getX(),event.getY());zoom=2.5f;constrain();}return true;
   }
   @Override public boolean onScroll(MotionEvent first,MotionEvent current,float dx,float dy){
    if(!scaling.isInProgress()&&zoom>1){transform.postTranslate(-dx,-dy);constrain();}return true;
   }
  });
 }
 void setPicture(android.graphics.Bitmap bitmap){setImageBitmap(bitmap);fit();}
 private void fit(){
  if(getDrawable()==null||getWidth()==0||getHeight()==0)return;
  float width=getDrawable().getIntrinsicWidth(),height=getDrawable().getIntrinsicHeight();if(width<=0||height<=0)return;
  float scale=Math.min(getWidth()/width,getHeight()/height);
  transform.reset();transform.postScale(scale,scale);transform.postTranslate((getWidth()-width*scale)/2,(getHeight()-height*scale)/2);
  zoom=1;setImageMatrix(transform);
 }
 private void constrain(){
  if(getDrawable()==null)return;
  imageBounds.set(0,0,getDrawable().getIntrinsicWidth(),getDrawable().getIntrinsicHeight());transform.mapRect(imageBounds);
  float dx=imageBounds.width()<=getWidth()?(getWidth()-imageBounds.width())/2-imageBounds.left:imageBounds.left>0?-imageBounds.left:imageBounds.right<getWidth()?getWidth()-imageBounds.right:0;
  float dy=imageBounds.height()<=getHeight()?(getHeight()-imageBounds.height())/2-imageBounds.top:imageBounds.top>0?-imageBounds.top:imageBounds.bottom<getHeight()?getHeight()-imageBounds.bottom:0;
  transform.postTranslate(dx,dy);setImageMatrix(transform);
 }
 @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){super.onSizeChanged(width,height,oldWidth,oldHeight);if(transform!=null)fit();}
 @Override public boolean onTouchEvent(MotionEvent event){
  clicked=false;scaling.onTouchEvent(event);gestures.onTouchEvent(event);if(clicked)performClick();
  if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);return true;
 }
 @Override public boolean performClick(){super.performClick();return true;}
}
