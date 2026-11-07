package com.changyu.ochat;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class CodeSyntaxTest {
 @Test public void xmlTokensKeepExactSourceAndSeparateColors(){
  String raw="<svg viewBox=\"0 0 20 20\"><!-- note --><path d=\"M0 0\"/></svg>";
  List<CodeSyntax.Token> tokens=CodeSyntax.tokens(raw);Set<Integer> kinds=new HashSet<>();int previous=0;
  for(CodeSyntax.Token token:tokens){assertTrue(token.start>=previous);assertTrue(token.end>token.start);assertTrue(token.end<=raw.length());previous=token.end;kinds.add(token.kind);}
  assertTrue(kinds.contains(CodeSyntax.TAG));assertTrue(kinds.contains(CodeSyntax.ATTRIBUTE));assertTrue(kinds.contains(CodeSyntax.STRING));assertTrue(kinds.contains(CodeSyntax.COMMENT));
  assertEquals("<svg viewBox=\"0 0 20 20\"><!-- note --><path d=\"M0 0\"/></svg>",raw);
 }
 @Test public void urlsInsideStringsAreNotComments(){
  String raw="const url = \"https://example.org/image.svg\"; // comment";
  List<CodeSyntax.Token> tokens=CodeSyntax.tokens(raw);int comments=0;
  for(CodeSyntax.Token token:tokens)if(token.kind==CodeSyntax.COMMENT){comments++;assertEquals("// comment",raw.substring(token.start,token.end));}
  assertEquals(1,comments);
 }
 @Test public void incompleteStringsAndUnicodeDoNotThrow(){
  String source="<svg title=\"鹅骑车\n"+String.join("",Collections.nCopies(1000,"路径"));
  for(int end=0;end<40;end++)CodeSyntax.tokens(source.substring(0,end));
 }
}
