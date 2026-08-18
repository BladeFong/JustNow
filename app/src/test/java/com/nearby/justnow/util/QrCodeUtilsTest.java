package com.nearby.justnow.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.graphics.Bitmap;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class QrCodeUtilsTest {

    @Test
    public void testGenerateQrCodeSuccess() {
        String content = "http://192.168.1.100:8888/upload?taskId=123&token=abc";
        Bitmap bitmap = QrCodeUtils.generateQrCode(content, 300, 300);
        assertNotNull(bitmap);
        assertEquals(300, bitmap.getWidth());
        assertEquals(300, bitmap.getHeight());
    }

    @Test
    public void testGenerateQrCodeWithInvalidInputs() {
        assertNull(QrCodeUtils.generateQrCode(null, 300, 300));
        assertNull(QrCodeUtils.generateQrCode("", 300, 300));
        assertNull(QrCodeUtils.generateQrCode("http://example.com", 0, 300));
        assertNull(QrCodeUtils.generateQrCode("http://example.com", 300, -1));
    }
}
