package com.changyu.ochat;

import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Model IDs drive capabilities; display aliases never change the request dialect. */
final class ModelCapabilities {
 static final String AUTO="default",EFFORT="effort",TOGGLE="toggle",FIXED="fixed",
  CLAUDE_ADAPTIVE="claude_adaptive",CLAUDE_BUDGET="claude_budget",CLAUDE_LEGACY_EFFORT="claude_legacy_effort",
  GEMINI_LEVEL="gemini_level",GEMINI_BUDGET="gemini_budget";
 static final Set<String> VALUES=new HashSet<>(Arrays.asList("default","none","enabled","minimal","low","medium","high","xhigh","max","ultra"));
 final String id,protocol,family,control;final boolean sampling,custom;final String[] efforts;
 ModelCapabilities(String id,String protocol,JSONObject configured){
  this.id=id==null?"":id;this.protocol=protocol;String n=canonical(this.id),f="unknown",mode=FIXED,levels="";boolean sample=true;
  if(n.startsWith("gpt-")||n.matches("o[134](?:-.*)?")){
   f="gpt";
   if(n.startsWith("gpt-6")){mode=EFFORT;levels=n.startsWith("gpt-6-sol")||n.startsWith("gpt-6-luna")?"none,low,medium,high,xhigh,max":"low,medium,high,xhigh,max";}
   else if(n.startsWith("gpt-5")){
    mode=EFFORT;
    if(n.contains("chat")||n.contains("search")){mode=FIXED;}
    else if(n.contains("pro"))levels=n.startsWith("gpt-5-pro")?"high":"medium,high,xhigh";
    else if(n.contains("codex"))levels=n.startsWith("gpt-5-codex")?"low,medium,high":"low,medium,high,xhigh";
    else if(version(n,"gpt-")>=5.6)levels="none,low,medium,high,xhigh,max";
    else if(version(n,"gpt-")>=5.2)levels="none,low,medium,high,xhigh";
    else if(version(n,"gpt-")>=5.1)levels="none,low,medium,high";
    else levels="minimal,low,medium,high";
   }else if(!n.startsWith("o1-mini")&&!n.startsWith("o1-preview")&&n.matches("o[134](?:-.*)?")){mode=EFFORT;levels="low,medium,high";}
   sample=mode.equals(FIXED)&&!n.startsWith("gpt-5")&&!n.startsWith("gpt-6");
  }else if(n.startsWith("claude-")){
   f="claude";double v=claudeVersion(n);
   if(v>=4.6){mode=CLAUDE_ADAPTIVE;boolean forced=n.contains("fable")||n.contains("mythos")||n.contains("opus")&&v>=5.5;
    levels=(forced?"":"none,")+"low,medium,high";
    if(v>=4.7)levels+=",xhigh";levels+=",max";sample=false;
   }else if(n.contains("opus")&&v>=4.5){mode=CLAUDE_LEGACY_EFFORT;levels="none,low,medium,high";sample=false;}
   else if(v>=3.7){mode=CLAUDE_BUDGET;levels="none,enabled";sample=false;}
  }else if(n.startsWith("deepseek-")){
   f="deepseek";sample=false;
   if(n.contains("v4")||n.equals("deepseek-flash")||n.equals("deepseek-pro")){mode=EFFORT;levels="none,low,high,max";}
   else if(n.contains("v3.1")||n.contains("v3.2")||n.contains("v3-1")||n.contains("v3-2")){mode=TOGGLE;levels="none,enabled";}
  }else if(n.startsWith("gemini-")){
   f="gemini";double v=version(n,"gemini-");
   if(!n.contains("live")&&!n.contains("embedding")){
    if(v>=3){mode=GEMINI_LEVEL;levels="low,medium,high";
     if(n.contains("3.1-flash-lite-image"))levels="minimal,high";
     else if(n.contains("flash")&&v<3.7)levels="minimal,low,medium,high";
     else if(v<3.1&&n.contains("pro"))levels="low,high";
    }else if(v>=2.5){mode=GEMINI_BUDGET;levels=(n.contains("pro")?"":"none,")+"low,medium,high";}
   }
  }else if(n.startsWith("grok-")||n.startsWith("gork-")){
   f="grok";double v=version(n,n.startsWith("gork-")?"gork-":"grok-");
   if(v>=4.6){mode=EFFORT;levels="low,medium,high,xhigh";}
   else if(v>=4.5){mode=EFFORT;levels="low,medium,high";}
   else if(v>=4.3){mode=EFFORT;levels="none,low,medium,high";}
   else if(n.startsWith("grok-3-mini")||n.startsWith("gork-3-mini")){mode=EFFORT;levels="low,high";}
  }else if(n.startsWith("kimi-")||n.startsWith("moonshot-")){
   f="kimi";sample=false;
   if(n.startsWith("kimi-k3")){mode=EFFORT;levels="low,high,max";}
   else if(n.startsWith("kimi-k2.5")||n.startsWith("kimi-k2.6")||n.startsWith("kimi-k2-5")||n.startsWith("kimi-k2-6")){mode=TOGGLE;levels="none,enabled";}
  }else if(n.startsWith("glm-")){
   f="glm";double v=version(n,"glm-");
   if(v>=5.3){mode=EFFORT;levels="low,high,max";}
   else if(v>=5.2){mode=EFFORT;levels="none,high,max";}
   else if(v>=4.5){mode=TOGGLE;levels="none,enabled";}
  }
  family=f;control=mode;
  custom="custom".equals(configured.optString("capabilityMode"))||(f.equals("unknown")&&configured.has("reasoningLevels"));
  sampling=custom?configured.optBoolean("supportsSampling",sample):sample;
  // Native protocols with no documented control use the server default.
  if(!custom&&((ChatApiClient.ANTHROPIC.equals(protocol)&&!f.equals("claude")&&!f.equals("deepseek"))
    ||(ChatApiClient.GEMINI.equals(protocol)&&!f.equals("gemini"))
    ||(ChatApiClient.RESPONSES.equals(protocol)&&(mode.equals(TOGGLE)||mode.equals(CLAUDE_BUDGET)))))levels="";
  if(custom)levels=configured.optString("reasoningLevels",levels);
  ArrayList<String> choices=new ArrayList<>();choices.add(AUTO);
  for(String value:levels.split(",")){value=value.trim().toLowerCase(Locale.ROOT);if(VALUES.contains(value)&&!choices.contains(value))choices.add(value);}
  efforts=choices.toArray(new String[0]);
 }
 static String canonical(String id){
  String n=id==null?"":id.trim().toLowerCase(Locale.ROOT);
  // Provider prefixes, ModelScope/Hugging Face namespaces and router names are common.
  Matcher m=Pattern.compile("(?:^|[/\\s:])((?:gpt-|claude-|deepseek-|gemini-|grok-|gork-|kimi-|glm-|moonshot-|o[134](?:-|$)).*)").matcher(n);
  if(m.find())n=m.group(1);n=n.replaceFirst(":(?:free|online|nitro)$","");
  return n;
 }
 private static double version(String n,String prefix){Matcher m=Pattern.compile(Pattern.quote(prefix)+"(\\d+)(?:[.-](\\d+))?").matcher(n);if(!m.find())return 0;int major=Integer.parseInt(m.group(1));String minor=m.group(2);return major+(minor==null||minor.length()>2?0:Integer.parseInt(minor)/Math.pow(10,minor.length()));}
 private static double claudeVersion(String n){
  if(n.contains("mythos-preview"))return 4.6;
  Matcher before=Pattern.compile("claude-([1-9])[.-]([0-9])(?:-|$)").matcher(n);if(before.find())return Integer.parseInt(before.group(1))+Integer.parseInt(before.group(2))/10d;
  // Date suffixes such as opus-20240229 are not model versions.
  Matcher after=Pattern.compile("(?:opus|sonnet|haiku|fable|mythos)-([1-9])(?:[.-]([0-9])(?=-|$))?(?=-|$)").matcher(n);
  if(after.find())return Integer.parseInt(after.group(1))+(after.group(2)==null?0:Integer.parseInt(after.group(2))/10d);
  Matcher major=Pattern.compile("claude-([1-9])(?:-|$)").matcher(n);return major.find()?Integer.parseInt(major.group(1)):0;
 }
 boolean supports(String value){return Arrays.asList(efforts).contains(value);}
 String normalize(String value){return supports(value)?value:AUTO;}
 boolean toggleOnly(){return control.equals(TOGGLE)||control.equals(CLAUDE_BUDGET);}
 boolean needsBudget(){return control.equals(CLAUDE_BUDGET)||control.equals(CLAUDE_LEGACY_EFFORT);}
 boolean preserveReasoning(){String n=canonical(id);return family.equals("kimi")&&(n.startsWith("kimi-k3")||n.startsWith("kimi-k2.7")||n.startsWith("kimi-k2-7"));}
 String outputTokenKey(){String n=canonical(id);return family.equals("gpt")&&(n.startsWith("gpt-5")||n.startsWith("gpt-6")||n.matches("o[134](?:-.*)?"))?"max_completion_tokens":"max_tokens";}
 String description(){if(efforts.length==1)return "此模型或接口使用服务端默认思考设置。";if(toggleOnly())return "此模型支持开启或关闭思考，由模型决定具体推理深度。";if(control.equals(GEMINI_BUDGET))return "思考强度映射为 Gemini 2.5 的推理预算，自动使用服务端默认值。";return "仅显示当前模型支持的档位，自动使用服务端默认值。";}
 static String label(String value){switch(value==null?AUTO:value){case "none":return "关闭";case "enabled":return "开启";case "minimal":return "极低";case "low":return "低";case "medium":return "中";case "high":return "高";case "xhigh":return "很高";case "max":return "最高";case "ultra":return "极致";default:return "自动";}}
 static JSONObject find(JSONObject provider,String id){JSONArray a=provider.optJSONArray("models");if(a!=null)for(int i=0;i<a.length();i++){JSONObject m=a.optJSONObject(i);if(m!=null&&id.equals(m.optString("id")))return m;}return new JSONObject();}
 static int positive(JSONObject m,String key,int fallback){try{int n=Integer.parseInt(m.optString(key,""));return n>0?n:fallback;}catch(Exception ignored){return fallback;}}
}
