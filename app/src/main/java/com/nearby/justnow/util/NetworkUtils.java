package com.nearby.justnow.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;

import androidx.annotation.Nullable;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * 局域网与网络状态工具类，用于提取平板局域网/热点 IP 地址及生成访问 URL
 */
public final class NetworkUtils {

    private NetworkUtils() {}

    /**
     * 获取当前活动的局域网 IPv4 地址（优先 wlan/ap/eth 网卡）
     *
     * @param context 上下文（可为 null，纯 Socket 探测兜底）
     * @return IPv4 字符串，若无可用局域网则返回 null
     */
    @Nullable
    public static String getLocalIpAddress(@Nullable Context context) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            List<String> candidateIps = new ArrayList<>();
            String preferredIp = null;

            for (NetworkInterface intf : interfaces) {
                if (intf.isLoopback() || !intf.isUp()) {
                    continue;
                }
                String name = intf.getName().toLowerCase();
                Enumeration<InetAddress> addrs = intf.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        String hostAddress = addr.getHostAddress();
                        if (hostAddress == null || hostAddress.isEmpty() || hostAddress.startsWith("127.")) {
                            continue;
                        }
                        // 优先提取 wlan / ap / softap / rndis / eth 网卡
                        if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("softap")
                                || name.startsWith("rndis") || name.startsWith("eth")) {
                            preferredIp = hostAddress;
                            break;
                        } else {
                            candidateIps.add(hostAddress);
                        }
                    }
                }
                if (preferredIp != null) {
                    return preferredIp;
                }
            }

            if (!candidateIps.isEmpty()) {
                return candidateIps.get(0);
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * 判断当前是否已接入 Wi-Fi、以太网或具备局域网 IP
     *
     * @param context 上下文
     * @return true 若已连接
     */
    public static boolean isWifiOrHotspotConnected(@Nullable Context context) {
        if (context == null) {
            return getLocalIpAddress(null) != null;
        }
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return getLocalIpAddress(context) != null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Network network = cm.getActiveNetwork();
            if (network != null) {
                NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
                if (capabilities != null) {
                    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        return true;
                    }
                }
            }
        } else {
            @SuppressWarnings("deprecation")
            NetworkInfo info = cm.getActiveNetworkInfo();
            if (info != null && info.isConnected()) {
                int type = info.getType();
                if (type == ConnectivityManager.TYPE_WIFI || type == ConnectivityManager.TYPE_ETHERNET) {
                    return true;
                }
            }
        }

        // 热点模式下可能没有 activeNetwork 连外网，但具备局域网 IP
        return getLocalIpAddress(context) != null;
    }

    /**
     * 拼接局域网网页上传地址
     *
     * @param ipAddress 本地局域网 IP
     * @param port      HTTP 服务端口
     * @param taskId    任务 ID
     * @param token     安全 Session Token
     * @return 格式化 URL，如 http://192.168.1.100:8888/upload?taskId=123&token=abc
     */
    public static String buildUploadUrl(String ipAddress, int port, long taskId, String token) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return "";
        }
        return "http://" + ipAddress + ":" + port + "/upload?taskId=" + taskId + "&token=" + token;
    }
}
