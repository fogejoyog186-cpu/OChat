package com.changyu.ochat;

import android.content.SharedPreferences;

/** Editable static home copy. Provider/model values and conversation content remain dynamic. */
final class HomeCopy {
    static final String[] KEYS={"headerTitle","headerSubtitle","welcomeSymbol","welcomeTitle","welcomeBody","composerHint","searchLabel","reasoningLabel","thinkingStatus","searchingStatus","historyTitle","historySubtitle","currentLabel"};
    static final String[] LABELS={"顶部标题","顶部副标题","欢迎符号","欢迎标题","欢迎正文","输入框提示","联网搜索标签","思考强度标签","思考中状态","搜索中状态","历史对话标题","历史对话说明","当前对话标签"};
    static final String[] DEFAULTS={"AI 助手","随时帮你思考与创作","✦","你好，我是你的 AI 助手","可以帮你搜索资料、整理思路或完成创作","输入消息…","联网搜索","思考：","思考中…","正在搜索 Tavily…","历史对话","保存在本机","当前 · "};
    static final String WELCOME_DISPLAY_MODE="welcomeDisplayMode";
    static boolean isIconMode(SharedPreferences prefs) {
        return "icon".equals(prefs.getString(WELCOME_DISPLAY_MODE,"text"));
    }
    static String get(SharedPreferences prefs, String key) {
        for(int i=0;i<KEYS.length;i++) if(KEYS[i].equals(key)) return prefs.getString("homeText_"+key,DEFAULTS[i]);
        throw new IllegalArgumentException("Unknown home copy key");
    }
}
