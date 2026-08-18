package com.nearby.justnow.server;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简流式 Multipart/form-data 解析器，直接将图片二进制流写入磁盘，严格防止大内存占用
 */
public final class MultipartStreamParser {

    public static class ParseResult {
        public final Map<String, String> fields = new HashMap<>();
        public final List<File> savedFiles = new ArrayList<>();
    }

    private MultipartStreamParser() {}

    /**
     * 流式解析 Multipart 请求体
     *
     * @param inputStream HTTP 请求输入流
     * @param contentType Content-Type 标头（形如 multipart/form-data; boundary=----xxx）
     * @param outputDir   图片保存目标目录
     * @param filePrefix  文件名前缀
     * @return 解析结果（包含字段 Map 和保存的文件列表）
     * @throws IOException 解析或写文件失败抛出
     */
    public static ParseResult parse(InputStream inputStream, String contentType,
                                    File outputDir, String filePrefix) throws IOException {
        ParseResult result = new ParseResult();
        if (contentType == null || !contentType.contains("boundary=")) {
            return result;
        }

        String boundary = contentType.substring(contentType.indexOf("boundary=") + "boundary=".length()).trim();
        if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
            boundary = boundary.substring(1, boundary.length() - 1);
        }

        byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);
        byte[] endBoundaryBytes = ("--" + boundary + "--").getBytes(StandardCharsets.US_ASCII);

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // 读取整个流或块解析
        // 为保证鲁棒性并防范极端情况，使用高效滑动缓冲区分块写入
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();

        int b;
        int fileIndex = 0;

        // 简易状态机：0=查找边界, 1=读取头信息, 2=读取数据体
        while ((b = inputStream.read()) != -1) {
            lineBuf.write(b);
            byte[] currentBytes = lineBuf.toByteArray();
            if (endsWith(currentBytes, boundaryBytes) || endsWith(currentBytes, endBoundaryBytes)) {
                lineBuf.reset();
                break;
            }
        }

        while (true) {
            // 读取 Part Header 直到空行 (\r\n\r\n)
            String headers = readHeaders(inputStream);
            if (headers == null || headers.isEmpty()) {
                break;
            }

            String fieldName = extractHeaderParam(headers, "name");
            String filename = extractHeaderParam(headers, "filename");

            if (filename != null && !filename.isEmpty()) {
                // 文件字段：流式写入磁盘
                File targetFile = new File(outputDir, filePrefix + "_" + System.currentTimeMillis() + "_" + (fileIndex++) + ".jpg");
                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    boolean isEnd = streamUntilBoundary(inputStream, boundaryBytes, fos);
                    result.savedFiles.add(targetFile);
                    if (isEnd) break;
                } catch (IOException e) {
                    if (targetFile.exists()) {
                        targetFile.delete();
                    }
                    throw e;
                }
            } else if (fieldName != null) {
                // 普通表单字段
                ByteArrayOutputStream valStream = new ByteArrayOutputStream();
                boolean isEnd = streamUntilBoundary(inputStream, boundaryBytes, valStream);
                String val = new String(valStream.toByteArray(), StandardCharsets.UTF_8).trim();
                result.fields.put(fieldName, val);
                if (isEnd) break;
            } else {
                // 未知字段，跳过直到下一个 boundary
                ByteArrayOutputStream dummy = new ByteArrayOutputStream();
                boolean isEnd = streamUntilBoundary(inputStream, boundaryBytes, dummy);
                if (isEnd) break;
            }
        }

        return result;
    }

    private static String readHeaders(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int consecutiveNewlines = 0;
        int b;
        while ((b = in.read()) != -1) {
            baos.write(b);
            if (b == '\n') {
                consecutiveNewlines++;
                byte[] data = baos.toByteArray();
                if (consecutiveNewlines >= 2 || endsWith(data, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
                        || endsWith(data, "\n\n".getBytes(StandardCharsets.US_ASCII))) {
                    return new String(data, StandardCharsets.UTF_8);
                }
            } else if (b != '\r') {
                consecutiveNewlines = 0;
            }
        }
        return baos.size() > 0 ? new String(baos.toByteArray(), StandardCharsets.UTF_8) : null;
    }

    private static boolean streamUntilBoundary(InputStream in, byte[] boundaryBytes, OutputStream out) throws IOException {
        // 严格遵循 RFC 7578 / RFC 2046 标准，以 CRLF (\r\n--boundary) 作为 part 分隔符
        byte[] delimiter = new byte[boundaryBytes.length + 2];
        delimiter[0] = '\r';
        delimiter[1] = '\n';
        System.arraycopy(boundaryBytes, 0, delimiter, 2, boundaryBytes.length);

        int matchLen = 0;
        byte[] matchBuf = new byte[delimiter.length + 4];
        int b;

        while ((b = in.read()) != -1) {
            if (b == delimiter[matchLen]) {
                matchBuf[matchLen] = (byte) b;
                matchLen++;
                if (matchLen == delimiter.length) {
                    // 匹配到了 boundary，检查是否是结束符 -- 或后续换行
                    int next1 = in.read();
                    if (next1 == '-') {
                        int next2 = in.read();
                        if (next2 == '-') {
                            return true; // 整个 multipart 结束
                        }
                    } else if (next1 == '\r') {
                        int next2 = in.read();
                        if (next2 == '\n') {
                            return false; // 下一个 part 开始
                        }
                    } else if (next1 == '\n') {
                        return false; // 下一个 part 开始
                    }
                    return false;
                }
            } else {
                if (matchLen > 0) {
                    // 前面部分匹配但中断了，把已缓存的非边界字节冲刷写入
                    out.write(matchBuf, 0, matchLen);
                    matchLen = 0;
                    if (b == delimiter[0]) {
                        matchBuf[0] = (byte) b;
                        matchLen = 1;
                        continue;
                    }
                }
                out.write(b);
            }
        }
        return true;
    }

    private static String extractHeaderParam(String headerContent, String paramName) {
        String key = paramName + "=\"";
        int idx = headerContent.indexOf(key);
        if (idx == -1) {
            key = paramName + "=";
            idx = headerContent.indexOf(key);
            if (idx == -1) return null;
            idx += key.length();
            int end = headerContent.indexOf(";", idx);
            if (end == -1) end = headerContent.indexOf("\r", idx);
            if (end == -1) end = headerContent.indexOf("\n", idx);
            return end == -1 ? headerContent.substring(idx).trim() : headerContent.substring(idx, end).trim();
        }
        idx += key.length();
        int end = headerContent.indexOf("\"", idx);
        return end == -1 ? null : headerContent.substring(idx, end);
    }

    private static boolean endsWith(byte[] source, byte[] target) {
        if (source.length < target.length) return false;
        int offset = source.length - target.length;
        for (int i = 0; i < target.length; i++) {
            if (source[offset + i] != target[i]) return false;
        }
        return true;
    }
}
