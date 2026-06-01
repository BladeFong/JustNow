package com.nearby.justnow.data.holiday;

import com.nearby.justnow.data.entity.HolidayCacheEntity;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 验证 {@link HolidayDataSource#fetch(int)} 模板方法的多态行为：
 * <ul>
 *   <li>getUrl(year) 被子类实现使用</li>
 *   <li>parseAndFill 在 HTTP 200 时被调用</li>
 *   <li>HTTP 错误 / body 为 null 时返回 emptyEntity，parseAndFill 不被调用</li>
 * </ul>
 */
public class HolidayDataSourceTest {

    @Test
    public void fetch_success_callsParseAndFillWithBody() throws IOException {
        AtomicReference<String> capturedContent = new AtomicReference<>();
        AtomicInteger capturedYear = new AtomicInteger();
        OkHttpClient client = newSuccessClient("HELLO_BODY", "application/json");

        HolidayDataSource src = new HolidayDataSource(client) {
            @Override
            protected String getUrl(int year) {
                return "https://example.test/holiday-" + year + ".json";
            }

            @Override
            protected void parseAndFill(HolidayCacheEntity entity, String content, int year) {
                capturedContent.set(content);
                capturedYear.set(year);
                entity.holidayCount = 7; // 子类自定义填充
            }
        };

        HolidayCacheEntity result = src.fetch(2026);
        assertNotNull(result);
        assertEquals(2026, result.year);
        assertEquals("应通过 parseAndFill 设置 holidayCount", 7, result.holidayCount);
        assertEquals("HELLO_BODY", capturedContent.get());
        assertEquals(2026, capturedYear.get());
    }

    @Test
    public void fetch_http404_returnsEmptyEntity_doesNotCallParseAndFill() throws IOException {
        AtomicInteger parseCallCount = new AtomicInteger();
        OkHttpClient client = newFailureClient(404);

        HolidayDataSource src = new HolidayDataSource(client) {
            @Override
            protected String getUrl(int year) {
                return "https://example.test/holiday-" + year + ".json";
            }

            @Override
            protected void parseAndFill(HolidayCacheEntity entity, String content, int year) {
                parseCallCount.incrementAndGet();
            }
        };

        HolidayCacheEntity result = src.fetch(2027);
        assertEquals(2027, result.year);
        assertEquals(0, result.holidayCount);
        assertNull(result.dataJson);
        assertEquals("HTTP 失败不应调用 parseAndFill", 0, parseCallCount.get());
    }

    @Test
    public void fetch_500_returnsEmptyEntity() throws IOException {
        OkHttpClient client = newFailureClient(500);

        HolidayDataSource src = new HolidayDataSource(client) {
            @Override
            protected String getUrl(int year) {
                return "https://example.test/holiday-" + year + ".json";
            }

            @Override
            protected void parseAndFill(HolidayCacheEntity entity, String content, int year) {
                throw new AssertionError("不应被调用");
            }
        };

        HolidayCacheEntity result = src.fetch(2028);
        assertEquals(2028, result.year);
        assertEquals(0, result.holidayCount);
    }

    @Test
    public void fetch_usesGetUrlFromSubclass() throws IOException {
        AtomicReference<String> capturedUrl = new AtomicReference<>();
        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                capturedUrl.set(chain.request().url().toString());
                return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .body(ResponseBody.create("{}",
                        MediaType.get("application/json; charset=utf-8")))
                    .build();
            }).build();

        HolidayDataSource src = new HolidayDataSource(client) {
            @Override
            protected String getUrl(int year) {
                return "https://example.test/subclass-url/" + year;
            }

            @Override
            protected void parseAndFill(HolidayCacheEntity entity, String content, int year) {
                // no-op
            }
        };

        src.fetch(2030);
        assertNotNull(capturedUrl.get());
        assertTrue("URL 应来自子类 getUrl()",
            capturedUrl.get().endsWith("/subclass-url/2030"));
    }

    // ---- 辅助方法 ----

    private static OkHttpClient newSuccessClient(String body, String mediaType) {
        return new OkHttpClient.Builder()
            .addInterceptor(chain -> new Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body(ResponseBody.create(body,
                    MediaType.get(mediaType + "; charset=utf-8")))
                .build())
            .build();
    }

    private static OkHttpClient newFailureClient(int code) {
        return new OkHttpClient.Builder()
            .addInterceptor(chain -> new Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code).message("ERR")
                .body(ResponseBody.create("err",
                    MediaType.get("text/plain; charset=utf-8")))
                .build())
            .build();
    }
}
