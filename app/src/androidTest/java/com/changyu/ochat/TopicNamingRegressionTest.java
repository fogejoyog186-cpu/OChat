package com.changyu.ochat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.io.IOException;
import static org.junit.Assert.*;

/** Uses Android's real org.json rather than the local JVM android.jar stubs. */
@RunWith(AndroidJUnit4.class)
public class TopicNamingRegressionTest {
    private String parse(String json) throws Exception {return ChatResponseParser.parse(new JSONObject(json),true);}
    private void fails(String json,String code) throws Exception {try{parse(json);fail("Expected "+code);}catch(IOException e){assertTrue(e.getMessage(),e.getMessage().startsWith(code));}}
    @Test public void stringsArraysAndValueBlocks() throws Exception {
        assertEquals("旅行计划",parse("{\"choices\":[{\"message\":{\"content\":\"标题： **旅行计划**\"}}]}"));
        assertEquals("旅行计划",parse("{\"choices\":[{\"message\":{\"content\":[{\"type\":\"text\",\"text\":{\"value\":\"旅行\"}},{\"text\":\"计划\"}]}}]}"));
        assertEquals("标题",parse("{\"choices\":[{\"message\":{\"content\":[{\"value\":\"标题\"},null]}}]}"));
    }
    @Test public void responsesShapes() throws Exception {
        assertEquals("旅行",parse("{\"output_text\":\"旅行\"}"));
        assertEquals("旅行",parse("{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"旅行\"}]}]}"));
        assertEquals("旅行",parse("{\"content\":[{\"type\":\"text\",\"text\":\"旅行\"}]}"));
    }
    @Test public void reasoningIsFallbackOnlyAndCleaned() throws Exception {
        assertEquals("正常标题",parse("{\"choices\":[{\"message\":{\"content\":\"正常标题\",\"reasoning_content\":\"不要显示\"}}]}"));
        assertEquals("旅行",parse("{\"choices\":[{\"message\":{\"content\":null,\"reasoning_content\":\"<think>分析</think>标题：旅行\"}}]}"));
        fails("{\"choices\":[{\"message\":{\"content\":null,\"reasoning_content\":\"分析一\\n分析二\"}}]}","reasoning_only");
    }
    @Test public void nullWhitespaceRefusalAndUnsupportedAreErrors() throws Exception {
        fails("{\"choices\":[{\"message\":{\"content\":null}}]}","no_text");
        fails("{\"choices\":[{\"message\":{\"content\":\"   \",\"reasoning_content\":null}}]}","no_text");
        fails("{\"choices\":[{\"message\":{\"refusal\":\"secret refusal\"}}]}","refusal");
        fails("{\"choices\":[{\"message\":{\"content\":[{\"type\":\"image\",\"value\":\"not text\"}]}}]}","unsupported_content");
        fails("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":null}}]}","token_limit");
        fails("{\"choices\":[{\"delta\":{\"content\":\"not non-stream\"}}]}","unexpected_delta");
        fails("{\"error\":{\"message\":\"API KEY secret\"}}","provider_error");
        fails("{\"output\":[{\"content\":[{\"type\":\"refusal\",\"refusal\":\"secret\"}]}]}","refusal");
    }
}
