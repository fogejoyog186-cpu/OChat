package com.changyu.ochat;

import java.text.SimpleDateFormat;
import java.text.ParsePosition;
import java.util.*;

/** Calendar boundaries use local midnight, including DST and year rollover. */
final class HistoryDates {
 static long parseLegacy(String value,long now){
  if(value==null||value.isEmpty())return 0;
  Calendar current=Calendar.getInstance();current.setTimeInMillis(now);
  SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT);format.setLenient(false);
  String input=current.get(Calendar.YEAR)+"-"+value;ParsePosition position=new ParsePosition(0);
  Date parsed=format.parse(input,position);
  if(parsed==null||position.getIndex()!=input.length())return 0;
  Calendar date=Calendar.getInstance();date.setTime(parsed);
  if(date.getTimeInMillis()>now+60000L){date.add(Calendar.YEAR,-1);}
  return date.getTimeInMillis();
 }
 static String group(long timestamp,long now){
  if(timestamp<=0)return "日期未知";
  Calendar boundary=Calendar.getInstance();boundary.setTimeInMillis(now);
  boundary.set(Calendar.HOUR_OF_DAY,0);boundary.set(Calendar.MINUTE,0);boundary.set(Calendar.SECOND,0);boundary.set(Calendar.MILLISECOND,0);
  if(timestamp>=boundary.getTimeInMillis())return "今天";
  boundary.add(Calendar.DAY_OF_YEAR,-1);if(timestamp>=boundary.getTimeInMillis())return "昨天";
  boundary.add(Calendar.DAY_OF_YEAR,-6);if(timestamp>=boundary.getTimeInMillis())return "7 天内";
  boundary.add(Calendar.DAY_OF_YEAR,-23);if(timestamp>=boundary.getTimeInMillis())return "30 天内";
  return "更早";
 }
 private HistoryDates(){}
}
