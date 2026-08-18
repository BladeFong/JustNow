package com.nearby.justnow.server;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.repository.TaskPhotoRepository;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 基于标准 ServerSocket 的轻量嵌入式 HTTP 服务，专用于任务扫码传图（100% Android 原生零依赖）
 */
public class TaskPhotoHttpServer {

    public interface OnHeartbeatListener {
        void onHeartbeatReceived(long taskId);
    }

    public interface OnUploadCompleteListener {
        void onUploadSuccess(long taskId, int savedCount);
        void onUploadFailure(long taskId, String errorMsg);
    }

    private final Context mContext;
    private final TaskPhotoRepository mPhotoRepository;
    private final TaskEntity mTask;
    private final String mToken;
    private final int mMaxCount;
    private final String mThemeColor;
    private final Handler mMainHandler;

    private ServerSocket mServerSocket;
    private ExecutorService mThreadPool;
    private volatile boolean mIsRunning = false;
    private int mBoundPort = -1;
    private OnHeartbeatListener mHeartbeatListener;
    private OnUploadCompleteListener mUploadCompleteListener;

    public TaskPhotoHttpServer(@NonNull Context context,
                               @NonNull TaskPhotoRepository photoRepository,
                               @NonNull TaskEntity task,
                               @NonNull String token,
                               int maxCount,
                               @NonNull String themeColor) {
        this.mContext = context.getApplicationContext();
        this.mPhotoRepository = photoRepository;
        this.mTask = task;
        this.mToken = token;
        this.mMaxCount = maxCount;
        this.mThemeColor = themeColor;
        this.mMainHandler = new Handler(Looper.getMainLooper());
    }

    public void setHeartbeatListener(OnHeartbeatListener listener) {
        this.mHeartbeatListener = listener;
    }

    public void setUploadCompleteListener(OnUploadCompleteListener listener) {
        this.mUploadCompleteListener = listener;
    }

    /**
     * 启动 HTTP 服务，自动在 8888~8899 探测可用端口
     *
     * @param preferredPort 优先尝试端口
     * @return 实际绑定的端口号，若启动失败返回 -1
     */
    public synchronized int start(int preferredPort) {
        if (mIsRunning) {
            return mBoundPort;
        }

        int port = preferredPort;
        for (int i = 0; i < 12; i++) {
            try {
                mServerSocket = new ServerSocket(port);
                mBoundPort = port;
                break;
            } catch (IOException e) {
                port++;
            }
        }

        if (mServerSocket == null) {
            return -1;
        }

        mIsRunning = true;
        mThreadPool = Executors.newFixedThreadPool(4);

        Thread listenThread = new Thread(() -> {
            while (mIsRunning) {
                try {
                    Socket socket = mServerSocket.accept();
                    if (!mIsRunning) {
                        try { socket.close(); } catch (Exception ignored) {}
                        break;
                    }
                    mThreadPool.execute(() -> handleClientSocket(socket));
                } catch (SocketException se) {
                    // ServerSocket closed normally
                    break;
                } catch (Exception e) {
                    if (!mIsRunning) break;
                }
            }
        }, "TaskPhotoHttpServer-Listen");
        listenThread.start();

        return mBoundPort;
    }

    /**
     * 即刻停止服务并释放端口与线程资源
     */
    public synchronized void stop() {
        mIsRunning = false;
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (Exception ignored) {}
            mServerSocket = null;
        }
        if (mThreadPool != null) {
            mThreadPool.shutdownNow();
            mThreadPool = null;
        }
        mBoundPort = -1;
    }

    public int getBoundPort() {
        return mBoundPort;
    }

    private void handleClientSocket(Socket socket) {
        try {
            socket.setSoTimeout(10000); // 10秒超时，防止异常断网挂起线程
        } catch (SocketException ignored) {}

        try (BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            // 1. 解析请求行 (Request-Line)
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendErrorResponse(out, 400, "Bad Request");
                return;
            }

            String method = parts[0].toUpperCase();
            String rawUri = parts[1];

            String path = rawUri;
            String query = null;
            int qIdx = rawUri.indexOf('?');
            if (qIdx != -1) {
                path = rawUri.substring(0, qIdx);
                query = rawUri.substring(qIdx + 1);
            }

            Map<String, String> queryParams = parseQueryParams(query);

            // 2. 解析 Headers
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            while ((headerLine = readLine(in)) != null && !headerLine.isEmpty()) {
                int colonIdx = headerLine.indexOf(':');
                if (colonIdx != -1) {
                    String name = headerLine.substring(0, colonIdx).trim().toLowerCase();
                    String value = headerLine.substring(colonIdx + 1).trim();
                    headers.put(name, value);
                }
            }

            // 3. 路由分发
            if ("/upload".equals(path)) {
                handlePage(out, method, queryParams);
            } else if ("/api/ping".equals(path)) {
                handlePing(out, method, queryParams);
            } else if ("/api/upload".equals(path)) {
                handleUpload(in, out, method, headers);
            } else {
                sendErrorResponse(out, 404, "Not Found");
            }
        } catch (Exception ignored) {
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }

    private void handlePage(OutputStream out, String method, Map<String, String> params) throws IOException {
        if (!"GET".equals(method)) {
            sendErrorResponse(out, 405, "Method Not Allowed");
            return;
        }

        String token = params.get("token");
        if (token == null || !token.equals(mToken)) {
            sendErrorResponse(out, 403, "Forbidden: Invalid Token");
            return;
        }

        String htmlTemplate = loadAssetString("web/upload.html");
        if (htmlTemplate == null) {
            sendErrorResponse(out, 500, "Template not found");
            return;
        }

        int currentPhotos = mPhotoRepository.getPhotoCountForTask(mTask.id);
        int remaining = Math.max(0, mMaxCount - currentPhotos);

        String html = htmlTemplate
                .replace("{{TASK_ID}}", String.valueOf(mTask.id))
                .replace("{{TOKEN}}", mToken)
                .replace("{{TASK_TITLE}}", escapeHtml(mTask.content != null ? mTask.content : "打卡任务"))
                .replace("{{TASK_ICON}}", mTask.iconName != null ? mTask.iconName : "ic_launcher")
                .replace("{{REMAINING_COUNT}}", String.valueOf(remaining))
                .replace("{{MAX_COUNT}}", String.valueOf(mMaxCount))
                .replace("{{THEME_COLOR}}", mThemeColor);

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        writeHttpResponse(out, 200, "OK", "text/html; charset=UTF-8", bytes);
    }

    private void handlePing(OutputStream out, String method, Map<String, String> params) throws IOException {
        if (!"GET".equals(method)) {
            sendErrorResponse(out, 405, "Method Not Allowed");
            return;
        }

        String token = params.get("token");
        if (token == null || !token.equals(mToken)) {
            sendErrorResponse(out, 403, "Forbidden");
            return;
        }

        if (mHeartbeatListener != null) {
            mMainHandler.post(() -> mHeartbeatListener.onHeartbeatReceived(mTask.id));
        }

        byte[] bytes = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        writeHttpResponse(out, 200, "OK", "application/json; charset=UTF-8", bytes);
    }

    private void handleUpload(InputStream in, OutputStream out, String method, Map<String, String> headers) throws IOException {
        if (!"POST".equals(method)) {
            sendErrorResponse(out, 405, "Method Not Allowed");
            return;
        }

        String contentType = headers.get("content-type");
        if (contentType == null || !contentType.contains("multipart/form-data")) {
            sendErrorResponse(out, 400, "Bad Request: Multipart expected");
            return;
        }

        File picturesDir = mContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (picturesDir == null) {
            picturesDir = new File(mContext.getFilesDir(), "pictures");
        }
        if (!picturesDir.exists()) {
            picturesDir.mkdirs();
        }

        try {
            MultipartStreamParser.ParseResult result = MultipartStreamParser.parse(
                    in, contentType, picturesDir, "photo_task_" + mTask.id);

            String token = result.fields.get("token");
            if (token == null || !token.equals(mToken)) {
                for (File f : result.savedFiles) f.delete();
                sendJsonResponse(out, 403, 403, "Forbidden: Invalid Token", 0);
                return;
            }

            int currentCount = mPhotoRepository.getPhotoCountForTask(mTask.id);
            int allowedCount = Math.max(0, mMaxCount - currentCount);

            List<File> files = result.savedFiles;
            int savedCount = 0;

            for (int i = 0; i < files.size(); i++) {
                File file = files.get(i);
                if (i < allowedCount) {
                    Uri uri = Uri.fromFile(file);
                    mPhotoRepository.bindPhotoToTask(mTask.id, uri.toString());
                    savedCount++;
                } else {
                    file.delete();
                }
            }

            final int finalSavedCount = savedCount;
            if (mUploadCompleteListener != null) {
                mMainHandler.post(() -> mUploadCompleteListener.onUploadSuccess(mTask.id, finalSavedCount));
            }

            sendJsonResponse(out, 200, 200, "success", savedCount);
        } catch (Exception e) {
            if (mUploadCompleteListener != null) {
                mMainHandler.post(() -> mUploadCompleteListener.onUploadFailure(mTask.id, e.getMessage()));
            }
            sendJsonResponse(out, 500, 500, "Upload failed: " + e.getMessage(), 0);
        }
    }

    private void writeHttpResponse(OutputStream out, int statusCode, String statusText,
                                   String contentType, byte[] body) throws IOException {
        String responseHeader = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        out.write(responseHeader.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private void sendErrorResponse(OutputStream out, int statusCode, String message) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        writeHttpResponse(out, statusCode, message, "text/plain; charset=UTF-8", body);
    }

    private void sendJsonResponse(OutputStream out, int httpStatus, int code, String message, int count) throws IOException {
        String json = "{\"code\":" + code + ",\"message\":\"" + escapeJson(message) + "\",\"savedCount\":" + count + "}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        writeHttpResponse(out, httpStatus, httpStatus == 200 ? "OK" : "Error", "application/json; charset=UTF-8", bytes);
    }

    private String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            } else if (c != '\r') {
                sb.append((char) c);
            }
        }
        return sb.length() > 0 || c != -1 ? sb.toString() : null;
    }

    @Nullable
    private String loadAssetString(String path) {
        try (InputStream is = mContext.getAssets().open(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0 && idx < pair.length() - 1) {
                    params.put(pair.substring(0, idx), pair.substring(idx + 1));
                }
            }
        }
        return params;
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
