package com.changyu.ochat;

import org.json.*;
import java.util.Locale;
import java.util.function.Consumer;

/** Reads only provider-supplied readable text; opaque signatures/encrypted blocks stay opaque. */
final class ReasoningText {
 static String delta(JSONObject value){
  if(value==null)return "";
  for(String key:new String[]{"reasoning_content","reasoning","reasoning_details"}){
   String text=read(value.opt(key));if(!text.isEmpty())return text;
  }
  return "";
 }
 static String read(Object value){
  if(value instanceof String)return(String)value;
  if(value instanceof JSONArray){StringBuilder out=new StringBuilder();JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)out.append(read(a.opt(i)));return out.toString();}
  if(!(value instanceof JSONObject))return "";
  JSONObject p=(JSONObject)value;String type=p.optString("type");
  if(!type.isEmpty()&&!java.util.Arrays.asList("text","summary_text","thinking","reasoning","reasoning.text","reasoning.summary").contains(type))return "";
  for(String key:new String[]{"text","thinking","summary"}){Object text=p.opt(key);if(text instanceof String||text instanceof JSONArray){String result=read(text);if(!result.isEmpty())return result;}}
  return "";
 }

 /** Some compatible servers put one leading <think> block inside content, even across deltas. */
 static final class Tags {
  private static final String OPEN="<think>",CLOSE="</think>";
  private final Consumer<String> answer,reasoning;
  private final StringBuilder pending=new StringBuilder();
  private int phase; // undecided, thinking, answer
  Tags(Consumer<String> answer,Consumer<String> reasoning){this.answer=answer;this.reasoning=reasoning;}
  void append(String text){
   if(text==null||text.isEmpty())return;if(phase==2){answer.accept(text);return;}pending.append(text);
   if(phase==0){
    int first=0;while(first<pending.length()&&Character.isWhitespace(pending.charAt(first)))first++;
    String start=pending.substring(first).toLowerCase(Locale.ROOT);
    if(start.isEmpty()||start.length()<OPEN.length()&&OPEN.startsWith(start))return;
    if(!start.startsWith(OPEN)){answer.accept(pending.toString());pending.setLength(0);phase=2;return;}
    pending.delete(0,first+OPEN.length());phase=1;
   }
   String current=pending.toString(),lower=current.toLowerCase(Locale.ROOT);int close=lower.indexOf(CLOSE);
   if(close>=0){
    if(close>0)reasoning.accept(current.substring(0,close));String tail=current.substring(close+CLOSE.length());pending.setLength(0);phase=2;if(!tail.isEmpty())answer.accept(tail);return;
   }
   int hold=0;
   for(int size=Math.min(CLOSE.length()-1,current.length());size>0;size--)if(CLOSE.startsWith(lower.substring(lower.length()-size))){hold=size;break;}
   int emit=current.length()-hold;if(emit>0){reasoning.accept(current.substring(0,emit));pending.delete(0,emit);}
  }
  void finish(){if(pending.length()>0){(phase==1?reasoning:answer).accept(pending.toString());pending.setLength(0);}}
 }
 private ReasoningText(){}
}
