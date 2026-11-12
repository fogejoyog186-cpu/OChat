package com.changyu.ochat;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProtocolAddressTest {
 @Test public void nativeExamplesAreRootsAndExpandToTheirRealApiPaths(){
  assertEquals("https://api.anthropic.com",ProtocolAddress.example(ChatApiClient.ANTHROPIC));
  assertEquals("https://api.anthropic.com/v1/messages",NativeProtocols.endpoint(ProtocolAddress.example(ChatApiClient.ANTHROPIC),ChatApiClient.ANTHROPIC,"claude-fixture",false));
  assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-fixture:generateContent",NativeProtocols.endpoint(ProtocolAddress.example(ChatApiClient.GEMINI),ChatApiClient.GEMINI,"gemini-fixture",false));
  assertTrue(ProtocolAddress.hint(ChatApiClient.ANTHROPIC).contains("无需手动添加"));
 }
 @Test public void openAiExamplesIncludeTheRequiredVersionPrefix(){
  assertEquals("https://api.openai.com/v1/chat/completions",ChatApiClient.endpoint(ProtocolAddress.example(ChatApiClient.CHAT_COMPLETIONS),ChatApiClient.CHAT_COMPLETIONS));
  assertEquals("https://api.openai.com/v1/responses",ChatApiClient.endpoint(ProtocolAddress.example(ChatApiClient.RESPONSES),ChatApiClient.RESPONSES));
  assertTrue(ProtocolAddress.hint(ChatApiClient.RESPONSES).contains("/responses"));
 }
 @Test public void customProxyAndDeepSeekAddressesAreNotReplacedAsExamples(){
  assertFalse(ProtocolAddress.isDefault("https://proxy.example.org/v1"));
  assertFalse(ProtocolAddress.isDefault("https://api.deepseek.com/anthropic"));
  assertTrue(ProtocolAddress.isDefault("https://api.anthropic.com/v1/"));
  assertTrue(ProtocolAddress.isDefault("https://generativelanguage.googleapis.com"));
 }
}
