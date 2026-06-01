package com.nearby.justnow.util;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 通用多语言分词 + 停用词过滤。
 * CJK → jieba 词语级分词，拉丁 → 空格/标点分词，结果合并并过滤停用词。
 */
public class TextTokenizer {

    /** JiebaSegmenter 内部 WordDictionary 懒加载，预热后线程安全（JustNowApplication.onCreate() 已预热）。 */
    private static final JiebaSegmenter sSegmenter = new JiebaSegmenter();

    /** 停用词（中文虚词 + 常用英文 stop words） */
    private static final Set<String> sStopWords = new HashSet<>(Arrays.asList(
        // 中文虚词
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一",
        "一个", "上", "也", "很", "到", "说", "要", "去", "你", "会", "着",
        "没有", "看", "好", "自己", "这", "他", "她", "它", "们", "那", "些",
        "与", "及", "或", "而", "但", "且", "因", "为", "所", "以", "能",
        "被", "把", "从", "对", "向", "让", "用", "于", "之", "其",
        // 英文 stop words
        "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
        "have", "has", "had", "do", "does", "did", "will", "would", "could",
        "should", "may", "might", "can", "shall", "to", "of", "in", "for",
        "on", "with", "at", "by", "from", "as", "into", "through", "during",
        "before", "after", "above", "below", "between", "under", "again",
        "further", "then", "once", "here", "there", "when", "where", "why",
        "how", "all", "both", "each", "few", "more", "most", "other", "some",
        "such", "no", "nor", "not", "only", "own", "same", "so", "than",
        "too", "very", "just", "because", "about", "if", "or", "and", "but",
        "it", "its", "my", "your", "his", "her", "our", "their", "me", "you",
        "him", "us", "them", "what", "which", "who", "whom", "this", "that",
        "these", "those", "am", "i", "we", "they", "he", "she"
    ));

    private TextTokenizer() {}

    /**
     * 分词 + 停用词过滤，返回可用于搜索的关键词列表。
     * 空输入或过滤后无结果返回空列表。
     */
    public static List<String> tokenize(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String trimmed = input.trim();
        List<String> tokens = new ArrayList<>();

        // 收集 CJK 子串和拉丁子串
        StringBuilder cjkBuf = new StringBuilder();
        StringBuilder latinBuf = new StringBuilder();

        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            Character.UnicodeScript script = Character.UnicodeScript.of(ch);

            if (isCjk(script)) {
                flushLatin(latinBuf, tokens);
                cjkBuf.append(ch);
            } else if (isLatinOrCommon(script)) {
                flushCjk(cjkBuf, tokens);
                latinBuf.append(ch);
            } else {
                // 其他字符（标点、空格等）作为分隔符
                flushCjk(cjkBuf, tokens);
                flushLatin(latinBuf, tokens);
            }
        }
        flushCjk(cjkBuf, tokens);
        flushLatin(latinBuf, tokens);

        // 停用词过滤（若全部被过滤则保留原始词，避免单字输入无结果）
        List<String> result = new ArrayList<>();
        for (String token : tokens) {
            String lower = token.toLowerCase();
            if (!sStopWords.contains(lower)) {
                result.add(token);
            }
        }
        if (result.isEmpty() && !tokens.isEmpty()) {
            return tokens; // 全部是停用词时保留原始词
        }

        return result;
    }

    private static boolean isCjk(Character.UnicodeScript script) {
        return script == Character.UnicodeScript.HAN
            || script == Character.UnicodeScript.HIRAGANA
            || script == Character.UnicodeScript.KATAKANA;
    }

    private static boolean isLatinOrCommon(Character.UnicodeScript script) {
        return script == Character.UnicodeScript.LATIN
            || script == Character.UnicodeScript.COMMON;
    }

    /** 冲刷 CJK 缓冲区，用 jieba 分词 */
    private static void flushCjk(StringBuilder buf, List<String> tokens) {
        if (buf.length() == 0) return;
        String cjkText = buf.toString();
        buf.setLength(0);

        List<SegToken> segResult = sSegmenter.process(cjkText, JiebaSegmenter.SegMode.SEARCH);
        for (SegToken token : segResult) {
            String word = token.word.trim();
            if (!word.isEmpty()) {
                tokens.add(word);
            }
        }
    }

    /** 冲刷拉丁缓冲区，按空格/标点分词 */
    private static void flushLatin(StringBuilder buf, List<String> tokens) {
        if (buf.length() == 0) return;
        String latinText = buf.toString().trim();
        buf.setLength(0);

        if (!latinText.isEmpty()) {
            String[] words = latinText.split("[\\s,，。、；;:：!！?？()（）\\[\\]\"'——…]+");
            for (String word : words) {
                if (!word.isEmpty()) {
                    tokens.add(word);
                }
            }
        }
    }
}
