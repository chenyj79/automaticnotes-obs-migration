package com.black.asr.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * ASR 准确率计算工具类
 * 提供 CER (Character Error Rate) 和 WER (Word Error Rate) 的计算方法
 * 以及标点符号 F1 值评估
 * 基于 Levenshtein 编辑距离算法实现
 */
public final class AsrAccuracyUtils {

    private AsrAccuracyUtils() {
    }

    /**
     * 带位置信息的标点标记
     * symbol: 标点符号本身
     * position: 该标点前面的非标点字符数（用于定位标点在文本中的位置）
     */
    private record PuncMark(String symbol, int position) {
    }

    /**
     * 文本解析结果，同时持有标点序列和非标点字符序列
     * puncMarks: 带位置信息的标点标记数组
     * chars: 非标点非空白字符的码点数组（用于文本对齐）
     */
    private record TextPuncData(PuncMark[] puncMarks, int[] chars) {
    }

    /**
     * 计算字符错误率 CER (Character Error Rate)
     * CER = levenshtein(hypothesis_chars, reference_chars) / len(reference_chars)
     * 适用于中文 ASR 评估，不依赖分词工具
     *
     * @param hypothesis ASR 转写结果
     * @param reference  人工标注的参考文本
     * @return CER 值，范围 [0, +∞)，0 表示完全匹配，>1 表示插入错误较多
     */
    public static double calculateCer(String hypothesis, String reference) {
        // 对于 CER，我们依然按码点拆分字符
        int[] hyp = toCodePointArray(normalizeForComparison(hypothesis));
        int[] ref = toCodePointArray(normalizeForComparison(reference));

        if (ref.length == 0) {
            return hyp.length == 0 ? 0.0 : 1.0;
        }
        int dist = levenshteinDistanceInts(hyp, ref);
        return (double) dist / ref.length;
    }

    /**
     * 计算词错误率 WER (Word Error Rate)
     * 对中文采用逐字切分（每个汉字视为一个 token），对英文按空格切分
     * WER = levenshtein(hypothesis_tokens, reference_tokens) / len(reference_tokens)
     *
     * @param hypothesis ASR 转写结果
     * @param reference  人工标注的参考文本
     * @return WER 值，范围 [0, +∞)
     */
    public static double calculateWer(String hypothesis, String reference) {
        String[] hypTokens = tokenize(normalizeForComparison(hypothesis));
        String[] refTokens = tokenize(normalizeForComparison(reference));
        if (refTokens.length == 0) {
            return hypTokens.length == 0 ? 0.0 : 1.0;
        }
        int dist = levenshteinDistanceTokens(hypTokens, refTokens);
        return (double) dist / refTokens.length;
    }

    /**
     * 计算标点符号 F1 值
     * 用于评估 ASR 输出中标点恢复的准确性
     * <p>
     * 采用基于文本对齐的位置映射策略：先对两段文本的非标点字符做 LCS 对齐，
     * 将 hypothesis 中标点的位置映射到 reference 的坐标系下，
     * 从而消除 ASR 转录文本字符错误对标点位置评估的干扰。
     *
     * @param hypothesis ASR 转写结果（含标点）
     * @param reference  人工标注的参考文本（含标点）
     * @return F1 值 [0, 1]
     */
    public static double calculatePunctuationF1(String hypothesis, String reference) {
        if (hypothesis == null || reference == null)
            return 0.0;

        TextPuncData hypData = parseText(hypothesis);
        TextPuncData refData = parseText(reference);

        PuncMark[] refPuncs = refData.puncMarks();
        PuncMark[] hypPuncs = hypData.puncMarks();

        if (refPuncs.length == 0) {
            return hypPuncs.length == 0 ? 1.0 : 0.0;
        }
        if (hypPuncs.length == 0) {
            return 0.0;
        }

        // 通过 LCS 对齐非标点字符，建立 hypothesis → reference 的位置映射
        int[] alignmentMap = buildAlignmentMap(hypData.chars(), refData.chars());

        // 将 hypothesis 标点位置映射到 reference 坐标系
        PuncMark[] remappedHypPuncs = remapPositions(hypPuncs, alignmentMap);

        // 使用 LCS 算法计算同时匹配符号和映射后位置的标点数
        int matches = countMatches(remappedHypPuncs, refPuncs);

        double precision = (double) matches / hypPuncs.length;
        double recall = (double) matches / refPuncs.length;

        if (precision + recall == 0)
            return 0.0;
        return 2 * precision * recall / (precision + recall);
    }

    /**
     * 文本归一化：全角转半角、去除标点、多余空白，统一为小写
     * 让 CER/WER 聚焦于内容差异，而非格式/标点差异
     */
    static String normalizeForComparison(String text) {
        if (text == null)
            return "";
        // 1. 全角转半角
        String halfWidth = toHalfWidth(text);
        // 2. 去除所有标点符号（使用 \p{P} 匹配所有 Unicode 标点，\p{S} 匹配符号）
        String noPunc = halfWidth.replaceAll("[\\p{P}\\p{S}\\u3000-\\u303F\\uFF00-\\uFFEF]", "");
        // 3. 连续空白合并为单个空格
        String collapsed = noPunc.replaceAll("\\s+", " ").trim();
        // 4. 英文统一小写
        return collapsed.toLowerCase();
    }

    /**
     * 全角字符转半角
     */
    private static String toHalfWidth(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '\u3000') {
                chars[i] = ' ';
            } else if (chars[i] >= '\uFF01' && chars[i] <= '\uFF5E') {
                chars[i] = (char) (chars[i] - 0xFEE0);
            }
        }
        return new String(chars);
    }

    /**
     * 解析文本，同时提取标点符号序列（带位置信息）和非标点字符序列
     * position 定义为该标点符号前面的非标点字符数
     * chars 为去除标点和空白后的字符码点序列，用于后续文本对齐
     */
    private static TextPuncData parseText(String text) {
        List<PuncMark> puncs = new ArrayList<>();
        List<Integer> chars = new ArrayList<>();
        String normalized = toHalfWidth(text);
        Pattern puncPattern = Pattern.compile("[\\p{P}\\p{S}]");
        int nonPuncCount = 0;
        for (int i = 0; i < normalized.length(); ) {
            int codePoint = normalized.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            String ch = normalized.substring(i, i + charCount);
            if (puncPattern.matcher(ch).matches()) {
                puncs.add(new PuncMark(ch, nonPuncCount));
            } else if (!Character.isWhitespace(codePoint)) {
                // 统一小写后加入字符序列，确保对齐时大小写不影响结果
                chars.add(Character.toLowerCase(codePoint));
                nonPuncCount++;
            }
            i += charCount;
        }
        int[] charArray = chars.stream().mapToInt(Integer::intValue).toArray();
        return new TextPuncData(puncs.toArray(new PuncMark[0]), charArray);
    }

    /**
     * 基于 LCS 回溯建立 hypothesis → reference 的字符位置映射
     * 返回 int[] 数组，长度为 hypChars.length，
     * 其中 result[i] 表示 hypChars[i] 在 refChars 中对齐到的下标，-1 表示未对齐
     */
    private static int[] buildAlignmentMap(int[] hypChars, int[] refChars) {
        int m = hypChars.length;
        int n = refChars.length;
        int[] map = new int[m];
        Arrays.fill(map, -1);

        if (m == 0 || n == 0) {
            return map;
        }

        // 构建完整 LCS DP 表用于回溯
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (hypChars[i - 1] == refChars[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        // 回溯提取对齐关系
        int i = m, j = n;
        while (i > 0 && j > 0) {
            if (hypChars[i - 1] == refChars[j - 1]) {
                map[i - 1] = j - 1;
                i--;
                j--;
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                i--;
            } else {
                j--;
            }
        }
        return map;
    }

    /**
     * 将 hypothesis 标点位置通过对齐映射转换到 reference 坐标系
     * <p>
     * 映射逻辑：标点位于 position 处表示它前面有 position 个非标点字符，
     * 即标点紧跟在第 (position-1) 个字符之后。通过对齐映射找到该字符在 reference 中的
     * 对应位置，映射后的 position = 对齐位置 + 1。
     * 若该字符未对齐，则向前搜索最近的已对齐字符。
     */
    private static PuncMark[] remapPositions(PuncMark[] hypPuncs, int[] alignmentMap) {
        PuncMark[] remapped = new PuncMark[hypPuncs.length];
        for (int k = 0; k < hypPuncs.length; k++) {
            int pos = hypPuncs[k].position();
            int mappedPos = 0;
            if (pos > 0) {
                // 从 pos-1 向前找最近的已对齐字符
                for (int idx = Math.min(pos - 1, alignmentMap.length - 1); idx >= 0; idx--) {
                    if (alignmentMap[idx] != -1) {
                        mappedPos = alignmentMap[idx] + 1;
                        break;
                    }
                }
            }
            remapped[k] = new PuncMark(hypPuncs[k].symbol(), mappedPos);
        }
        return remapped;
    }

    /**
     * 剥离文本中的所有标点符号
     * 用于孤立评估标点恢复模型的效果
     */
    public static String stripPunctuation(String text) {
        if (text == null) return "";
        // 归一化为半角
        String normalized = toHalfWidth(text);
        // 替换掉所有标点 (\p{P}) 和符号 (\p{S})
        return normalized.replaceAll("[\\p{P}\\p{S}]", "");
    }

    /**
     * 计算两个标点序列之间的匹配数（LCS，一维滚动数组优化）
     * 只有当标点符号相同且位置也相同时才算匹配
     */
    private static int countMatches(PuncMark[] s1, PuncMark[] s2) {
        // 确保 s2 是较短方，减少空间开销
        if (s1.length < s2.length) {
            PuncMark[] tmp = s1;
            s1 = s2;
            s2 = tmp;
        }
        int n = s2.length;
        int[] dp = new int[n + 1];

        for (PuncMark mark1 : s1) {
            int prev = 0;
            for (int j = 1; j <= n; j++) {
                int tmp = dp[j];
                if (mark1.symbol().equals(s2[j - 1].symbol())
                        && mark1.position() == s2[j - 1].position()) {
                    dp[j] = prev + 1;
                } else {
                    dp[j] = Math.max(dp[j], dp[j - 1]);
                }
                prev = tmp;
            }
        }
        return dp[n];
    }

    /**
     * 中英文混合分词（支持 Unicode 增补平面）
     */
    static String[] tokenize(String text) {
        if (text == null || text.isEmpty())
            return new String[0];

        List<String> tokens = new ArrayList<>();
        StringBuilder currentWord = new StringBuilder();

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int charCount = Character.charCount(codePoint);

            if (isCjkCharacter(codePoint)) {
                if (!currentWord.isEmpty()) {
                    tokens.add(currentWord.toString());
                    currentWord.setLength(0);
                }
                tokens.add(new String(Character.toChars(codePoint)));
            } else if (Character.isWhitespace(codePoint)) {
                // 空白分隔英文 word
                if (!currentWord.isEmpty()) {
                    tokens.add(currentWord.toString());
                    currentWord.setLength(0);
                }
            } else {
                // 英文字母、数字等累积
                currentWord.append(text, i, i + charCount);
            }
            i += charCount;
        }

        if (!currentWord.isEmpty()) {
            tokens.add(currentWord.toString());
        }

        return tokens.toArray(new String[0]);
    }

    /**
     * 将字符串转换为码点数组
     */
    private static int[] toCodePointArray(String text) {
        return text.codePoints().toArray();
    }

    /**
     * 判断码点是否为 CJK 汉字
     * 使用 isIdeographic 和 UnicodeScript.HAN 能够自动覆盖所有 CJK 扩展区（A-I 及未来版本）
     */
    private static boolean isCjkCharacter(int codePoint) {
        return Character.isIdeographic(codePoint) ||
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN;
    }

    /**
     * 码点级 Levenshtein 编辑距离
     */
    private static int levenshteinDistanceInts(int[] s1, int[] s2) {
        // 确保 s2 是较短方，减少空间开销
        if (s1.length < s2.length) {
            int[] tmp = s1;
            s1 = s2;
            s2 = tmp;
        }
        int n = s2.length;
        int[] dp = new int[n + 1];

        for (int j = 0; j <= n; j++) {
            dp[j] = j;
        }

        for (int val1 : s1) {
            int prev = dp[0];
            dp[0]++;
            for (int j = 1; j <= n; j++) {
                int tmp = dp[j];
                int cost = (val1 == s2[j - 1]) ? 0 : 1;
                dp[j] = Math.min(Math.min(dp[j] + 1, dp[j - 1] + 1), prev + cost);
                prev = tmp;
            }
        }
        return dp[n];
    }

    /**
     * Token 级 Levenshtein 编辑距离
     */
    private static int levenshteinDistanceTokens(String[] s1, String[] s2) {
        // 确保 s2 是较短方，减少空间开销
        if (s1.length < s2.length) {
            String[] tmp = s1;
            s1 = s2;
            s2 = tmp;
        }
        int n = s2.length;
        int[] dp = new int[n + 1];

        for (int j = 0; j <= n; j++) {
            dp[j] = j;
        }

        for (String token1 : s1) {
            int prev = dp[0];
            dp[0]++;
            for (int j = 1; j <= n; j++) {
                int tmp = dp[j];
                int cost = token1.equals(s2[j - 1]) ? 0 : 1;
                dp[j] = Math.min(Math.min(dp[j] + 1, dp[j - 1] + 1), prev + cost);
                prev = tmp;
            }
        }
        return dp[n];
    }
}
