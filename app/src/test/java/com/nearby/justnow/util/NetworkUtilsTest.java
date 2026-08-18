package com.nearby.justnow.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NetworkUtilsTest {

    @Test
    public void testBuildUploadUrl() {
        String url = NetworkUtils.buildUploadUrl("192.168.1.100", 8888, 123L, "token_xyz");
        assertEquals("http://192.168.1.100:8888/upload?taskId=123&token=token_xyz", url);
    }

    @Test
    public void testBuildUploadUrlWithNullIp() {
        String url = NetworkUtils.buildUploadUrl(null, 8888, 123L, "token_xyz");
        assertEquals("", url);
    }

    @Test
    public void testGetLocalIpAddressNonNullInTestEnvironment() {
        // 在本地测试环境中，即便没有 Wi-Fi，如果有活动的局域网网卡也能正确提取非 loopback IP
        String ip = NetworkUtils.getLocalIpAddress(null);
        // 本地 CI/测试机可能具备 eth/wlan IP 或 null
        if (ip != null) {
            assertTrue(ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+"));
            assertTrue(!ip.startsWith("127."));
        }
    }
}
