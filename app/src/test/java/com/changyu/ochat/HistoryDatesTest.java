package com.changyu.ochat;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class HistoryDatesTest {
 private long time(int year,int month,int day,int hour){
  Calendar c=Calendar.getInstance();c.clear();c.set(year,month-1,day,hour,0);return c.getTimeInMillis();
 }
 @Test public void groupsAcrossNewYear(){
  long now=time(2026,1,2,12);
  assertEquals("今天",HistoryDates.group(time(2026,1,2,0),now));
  assertEquals("昨天",HistoryDates.group(time(2026,1,1,23),now));
  assertEquals("7 天内",HistoryDates.group(time(2025,12,30,12),now));
  assertEquals("30 天内",HistoryDates.group(time(2025,12,12,12),now));
  assertEquals("更早",HistoryDates.group(time(2025,10,1,12),now));
  assertEquals("日期未知",HistoryDates.group(0,now));
 }
 @Test public void legacyDecemberBelongsToPreviousYearInJanuary(){
  assertEquals(time(2025,12,31,23),HistoryDates.parseLegacy("12-31 23:00",time(2026,1,2,12)));
 }
 @Test public void invalidLegacyDatesRemainUnknown(){
  assertEquals(0,HistoryDates.parseLegacy("02-31 12:00",time(2026,10,31,12)));
  assertEquals(0,HistoryDates.parseLegacy("10-31 12:00 trailing",time(2026,10,31,12)));
  assertEquals(0,HistoryDates.parseLegacy("",time(2026,10,31,12)));
 }
 @Test public void yesterdayUsesCalendarDaysAcrossDaylightSavings(){
  TimeZone previous=TimeZone.getDefault();try{TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
   assertEquals("昨天",HistoryDates.group(time(2026,3,8,0),time(2026,3,9,12)));
  }finally{TimeZone.setDefault(previous);}
 }
}
