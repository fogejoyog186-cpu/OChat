package com.changyu.ochat;

/** Converts complete math only; fenced/indented code and matched backtick spans stay literal. */
final class MathDelimiters {
 private MathDelimiters() {}
 static boolean[] codeRanges(String source) {
  // Mark protected source ranges before touching any delimiter, including unfinished fences.
  boolean[] code=new boolean[source.length()]; char fence=0; int fenceLength=0;
  for(int start=0;start<source.length();){
   int end=source.indexOf('\n',start);if(end<0)end=source.length();
   int p=start;while(p<end&&source.charAt(p)==' ')p++;
   char c=p<end?source.charAt(p):0;int run=0;
   if(c=='`'||c=='~')while(p+run<end&&source.charAt(p+run)==c)run++;
   boolean marker=p-start<=3&&run>=3;
   // CommonMark forbids backticks in the info string of a backtick opening fence.
   if(fence==0&&c=='`'&&source.indexOf('`',p+run)>=0&&source.indexOf('`',p+run)<end)marker=false;
   boolean protectedLine=fence!=0||marker||p-start>=4||(start<end&&source.charAt(start)=='\t');
   if(protectedLine)java.util.Arrays.fill(code,start,Math.min(source.length(),end+1),true);
   if(fence==0&&marker){fence=c;fenceLength=run;}
   else if(fence!=0&&marker&&c==fence&&run>=fenceLength&&source.substring(p+run,end).trim().isEmpty())fence=0;
   start=end+1;
  }
  for(int i=0;i<source.length();i++)if(!code[i]&&source.charAt(i)=='`'&&!escaped(source,i)){
   int n=1;while(i+n<source.length()&&source.charAt(i+n)=='`')n++;
   int close=i+n;
   while(close<source.length()){
    // Inline code cannot pair across a fenced/indented code block.
    if(code[close])break;
    if(source.charAt(close)=='`'){int r=1;while(close+r<source.length()&&source.charAt(close+r)=='`')r++;if(r==n)break;close+=r;}else close++;
   }
   if(close<source.length()&&!code[close]){java.util.Arrays.fill(code,i,close+n,true);i=close+n-1;}else i+=n-1;
  }
  return code;
 }
 static String normalize(String source) {
  if(source==null)return "";
  boolean[] code=codeRanges(source);
  StringBuilder out=new StringBuilder();
  for(int i=0;i<source.length();){
   if(code[i]){out.append(source.charAt(i++));continue;}
   String open=null,close=null;boolean block=false;
   if(!escaped(source,i)){
    if(source.startsWith("$$",i)){open=close="$$";block=true;}
    else if(source.charAt(i)=='$'){open=close="$";}
    else if(source.startsWith("\\(",i)){open="\\(";close="\\)";}
    else if(source.startsWith("\\[",i)){open="\\[";close="\\]";block=true;}
   }
   if(open==null){out.append(source.charAt(i++));continue;}
   int end=i+open.length();
   while(end<source.length()){
    if(code[end])break;
    if(source.startsWith(close,end)&&!escaped(source,end))break;
    if(!block&&source.charAt(end)=='\n')break;
    end++;
   }
   if(end<source.length()&&!code[end]&&source.startsWith(close,end)&&!escaped(source,end)&&!source.substring(i+open.length(),end).trim().isEmpty()){
    String math=source.substring(i+open.length(),end);
    if(block){out.append("\n\n$$\n").append(math.trim()).append("\n$$\n\n");}
    else out.append("$$").append(math).append("$$");
    i=end+close.length();
   }else{
    // Escaping dollars prevents Markwon's block parser consuming unfinished streamed math.
    if(open.charAt(0)=='$')for(int k=0;k<open.length();k++)out.append("\\$");
    else out.append("\\\\").append(open.charAt(1));
    i+=open.length();
   }
  }
  return out.toString();
 }
 private static boolean escaped(String s,int at){int count=0;while(at>0&&s.charAt(--at)=='\\')count++;return (count&1)!=0;}
}
