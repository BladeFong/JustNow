package com.nearby.justnow.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.repository.TaskPhotoRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TaskPhotoHttpServerTest {

    @Rule
    public TemporaryFolder mTempFolder = new TemporaryFolder();

    private TaskPhotoHttpServer mServer;
    private TaskPhotoRepository mMockRepo;
    private TaskEntity mTestTask;
    private Context mContext;
    private int mPort;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mMockRepo = mock(TaskPhotoRepository.class);
        when(mMockRepo.getPhotoCountForTask(anyLong())).thenReturn(0);
        when(mMockRepo.bindPhotoToTask(anyLong(), anyString())).thenReturn(1L);

        mTestTask = new TaskEntity();
        mTestTask.id = 88L;
        mTestTask.content = "单元测试打卡任务";
        mTestTask.quadrant = 0;

        mServer = new TaskPhotoHttpServer(
                mContext, mMockRepo, mTestTask, "test_token_123", 5, "#1A73E8");
        mPort = mServer.start(8910);
    }

    @After
    public void tearDown() {
        if (mServer != null) {
            mServer.stop();
        }
    }

    @Test
    public void testMultipartStreamParser() throws IOException {
        File outDir = mTempFolder.newFolder("parsed_photos");
        String boundary = "----TestBoundary123456";
        String contentType = "multipart/form-data; boundary=" + boundary;

        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"taskId\"\r\n\r\n"
                + "88\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"token\"\r\n\r\n"
                + "test_token_123\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"photos\"; filename=\"sample.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n"
                + "FakeImageBytes1234567890\r\n"
                + "--" + boundary + "--\r\n";

        ByteArrayInputStream in = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        MultipartStreamParser.ParseResult result = MultipartStreamParser.parse(
                in, contentType, outDir, "test_photo");

        assertEquals("88", result.fields.get("taskId"));
        assertEquals("test_token_123", result.fields.get("token"));
        assertEquals(1, result.savedFiles.size());

        File saved = result.savedFiles.get(0);
        assertTrue(saved.exists());
        byte[] fileBytes = new byte[(int) saved.length()];
        try (FileInputStream fis = new FileInputStream(saved)) {
            fis.read(fileBytes);
        }
        assertEquals("FakeImageBytes1234567890", new String(fileBytes, StandardCharsets.UTF_8));
    }

    @Test
    public void testPingEndpointSuccess() throws IOException {
        assertTrue(mPort > 0);
        URL url = new URL("http://127.0.0.1:" + mPort + "/api/ping?taskId=88&token=test_token_123");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.connect();

        assertEquals(200, conn.getResponseCode());
        conn.disconnect();
    }

    @Test
    public void testPingEndpointForbiddenWithInvalidToken() throws IOException {
        assertTrue(mPort > 0);
        URL url = new URL("http://127.0.0.1:" + mPort + "/api/ping?taskId=88&token=invalid_token");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.connect();

        assertEquals(403, conn.getResponseCode());
        conn.disconnect();
    }

    @Test
    public void testUploadEndpointSuccess() throws IOException {
        assertTrue(mPort > 0);
        OkHttpClient client = new OkHttpClient();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("taskId", "88")
                .addFormDataPart("token", "test_token_123")
                .addFormDataPart("photos", "photo1.jpg",
                        RequestBody.create(MediaType.parse("image/jpeg"), "TestImageData"))
                .build();

        Request request = new Request.Builder()
                .url("http://127.0.0.1:" + mPort + "/api/upload")
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            assertEquals(200, response.code());
            assertNotNull(response.body());
            String responseStr = response.body().string();
            assertTrue(responseStr.contains("\"code\":200"));
            assertTrue(responseStr.contains("\"savedCount\":1"));
        }
    }
}
