package com.changyu.ochat;

import android.text.*;
import android.text.style.ClickableSpan;
import android.view.*;
import android.widget.TextView;

/** Observe, don't consume DOWN: native long-press selection remains owned by TextView. */
final class SelectableSpanTouch implements View.OnTouchListener {
 private ClickableSpan down; private float x,y; private long time; private boolean moved,selected;
 private ClickableSpan hit(TextView v,MotionEvent e){if(!(v.getText() instanceof Spanned)||v.getLayout()==null)return null;float px=e.getX()-v.getTotalPaddingLeft()+v.getScrollX();float py=e.getY()-v.getTotalPaddingTop()+v.getScrollY();Layout l=v.getLayout();if(py<0||py>=l.getHeight())return null;int line=l.getLineForVertical((int)py);if(px<l.getLineLeft(line)||px>l.getLineRight(line))return null;int offset=l.getOffsetForHorizontal(line,px);Spanned s=(Spanned)v.getText();for(ClickableSpan span:s.getSpans(offset,offset,ClickableSpan.class))if(offset>=s.getSpanStart(span)&&offset<s.getSpanEnd(span))return span;return null;}
 public boolean onTouch(View view,MotionEvent e){TextView v=(TextView)view;switch(e.getActionMasked()){
 case MotionEvent.ACTION_DOWN:down=hit(v,e);x=e.getX();y=e.getY();time=e.getEventTime();moved=false;selected=v.hasSelection();break;
 case MotionEvent.ACTION_MOVE:if(Math.hypot(e.getX()-x,e.getY()-y)>ViewConfiguration.get(v.getContext()).getScaledTouchSlop())moved=true;break;
 case MotionEvent.ACTION_UP:ClickableSpan action=down;down=null;if(action!=null&&!selected&&!moved&&e.getEventTime()-time<ViewConfiguration.getLongPressTimeout()&&hit(v,e)==action){action.onClick(v);return true;}break;
 case MotionEvent.ACTION_CANCEL:down=null;break;
 }return false;}
}
