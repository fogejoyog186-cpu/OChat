package com.changyu.ochat;

import java.util.Arrays;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Node;
import org.commonmark.node.Text;

/** Narrow LLM extension: same-line isolated paired ** with padded or Unicode punctuation
 * edges. Ordinary CommonMark, triple/long star runs and unfinished pairs are unchanged.
 * Private-use guards make the existing parser eligible; removed from AST text before rendering.
 * No HTML, source persistence mutation, or rewriting inside code/math/link syntax.
 */
final class BoldCompatibility {
 static final char GUARD='\uE000';
 private BoldCompatibility() {}
 static boolean escaped(String s,int at){int n=0;while(at>0&&s.charAt(--at)=='\\')n++;return (n&1)!=0;}
 static boolean space(char c){return Character.isWhitespace(c)||Character.isSpaceChar(c);}
 static boolean punctuation(char c){int t=Character.getType(c);return t==Character.CONNECTOR_PUNCTUATION||t==Character.DASH_PUNCTUATION||t==Character.START_PUNCTUATION||t==Character.END_PUNCTUATION||t==Character.INITIAL_QUOTE_PUNCTUATION||t==Character.FINAL_QUOTE_PUNCTUATION||t==Character.OTHER_PUNCTUATION||"!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~".indexOf(c)>=0;}
 static String normalize(String s){
  if(s==null)return "";
  // A literal guard is never ours. Renderer uses the same check before removing guards.
  if(s.indexOf(GUARD)>=0)return s;
  boolean[] protectedRange=MathDelimiters.codeRanges(s);
  // Ambiguous escaped-star prose is deliberately not extended anywhere on that line.
  for(int i=0;i<s.length()-1;i++)if(s.startsWith("**",i)&&escaped(s,i)){
   int start=s.lastIndexOf('\n',i)+1,end=s.indexOf('\n',i);if(end<0)end=s.length();
   Arrays.fill(protectedRange,start,end,true);
  }
  for(int i=0;i<s.length();i++){
   if(protectedRange[i]||escaped(s,i))continue;
   String open=null,close=null;
   if(s.startsWith("$$",i))open=close="$$";
   else if(s.charAt(i)=='$')open=close="$";
   else if(s.startsWith("\\(",i)){open="\\(";close="\\)";}
   else if(s.startsWith("\\[",i)){open="\\[";close="\\]";}
   // Conservatively shield entire bracket constructs (labels too), reference definitions,
   // and angle constructs. A missing close shields the remaining line during streaming.
   else if(s.charAt(i)=='['){open="[";close="]";}
   else if(s.charAt(i)=='<'){open="<";close=">";}
   if(open==null)continue;
   int end=i+open.length();
   boolean blockMath=open.equals("$$")||open.equals("\\[");
   while(end<s.length()&&(blockMath||s.charAt(end)!='\n')&&!(s.startsWith(close,end)&&!escaped(s,end)))end++;
   if(end<s.length()&&s.startsWith(close,end))end+=close.length();
   if(open.equals("[")){
    if(end<s.length()&&s.charAt(end)=='('){int depth=1;end++;while(end<s.length()&&s.charAt(end)!='\n'&&depth>0){if(!escaped(s,end)){if(s.charAt(end)=='(')depth++;if(s.charAt(end)==')')depth--;}end++;}}
    else if(end<s.length()&&s.charAt(end)==':'){while(end<s.length()&&s.charAt(end)!='\n')end++;}
   }
   Arrays.fill(protectedRange,i,end,true);i=end-1;
  }
  StringBuilder out=new StringBuilder();int copied=0,opening=-1;
  for(int i=0;i<s.length();i++){
   if(s.charAt(i)=='\n'){opening=-1;continue;}
   if(s.charAt(i)!='*')continue;
   int end=i;while(end<s.length()&&s.charAt(end)=='*')end++;
   if(end-i!=2||protectedRange[i]||escaped(s,i)){opening=-1;i=end-1;continue;}
   if(opening<0){opening=i;i=end-1;continue;}
   int a=opening+2,b=i;while(a<b&&space(s.charAt(a)))a++;while(b>a&&space(s.charAt(b-1)))b--;
   if(a<b){
    char before=opening==0?'\n':s.charAt(opening-1),after=end==s.length()?'\n':s.charAt(end);
    boolean eligibleOpen=!punctuation(s.charAt(a))||space(before)||punctuation(before);
    boolean eligibleClose=!punctuation(s.charAt(b-1))||space(after)||punctuation(after);
    if(a!=opening+2||b!=i||!eligibleOpen||!eligibleClose){
     out.append(s,copied,opening).append("**");
     if(!eligibleOpen||!eligibleClose)out.append(GUARD);
     out.append(s,a,b);
     if(!eligibleOpen||!eligibleClose)out.append(GUARD);
     out.append("**");copied=end;
    }
   }
   opening=-1;i=end-1;
  }
  return out.append(s,copied,s.length()).toString();
 }
 static void removeGuards(Node node){node.accept(new AbstractVisitor(){@Override public void visit(Text text){text.setLiteral(text.getLiteral().replace(String.valueOf(GUARD),""));}});}
}
