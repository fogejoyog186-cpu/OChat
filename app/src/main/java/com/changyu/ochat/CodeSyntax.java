package com.changyu.ochat;

import java.util.*;
import java.util.regex.*;

/** Token ranges refer to the original source; highlighting never rewrites code. */
final class CodeSyntax {
 static final int COMMENT=0,STRING=1,TAG=2,ATTRIBUTE=3,NUMBER=4,KEYWORD=5;
 static final class Token {final int start,end,kind;Token(int s,int e,int k){start=s;end=e;kind=k;}}
 private static final Pattern TOKENS=Pattern.compile(
  "(<!--.*?(?:-->|$)|/\\*.*?(?:\\*/|$)|//[^\\r\\n]*)"
  +"|(\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')"
  +"|(</?[a-zA-Z_][\\w:.-]*)"
  +"|([a-zA-Z_][\\w:.-]*(?=\\s*=))"
  +"|(\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b)"
  +"|(\\b(?:class|interface|fun|function|return|if|else|for|while|const|let|var|val|true|false|null|None|True|False|import|from|def|public|private|static|final|new|try|catch|throw|async|await|export|extends|void|int|double|String|boolean)\\b)",
  Pattern.DOTALL);
 static List<Token> tokens(String source){
  List<Token> result=new ArrayList<>();Matcher matcher=TOKENS.matcher(source);
  while(matcher.find())for(int group=1;group<=6;group++)if(matcher.start(group)>=0){result.add(new Token(matcher.start(),matcher.end(),group-1));break;}
  return result;
 }
 private CodeSyntax(){}
}
