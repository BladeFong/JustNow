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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 验证 {@link HolidayDataSource#fetch(int)} 模板方法的多态行为：
 * <ul>
 *   <li>getUrl(year) 被子类实现使用</li>
 *   <li>parseAndFill 在 HTTP 200 时被调用</li>
 *   <li>HTTP 错误 / 空 body / 无效解析结果会抛 IOException，供多源 fallback 继续尝试</li>
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
                entity.dataJson = "{\"year\":" + year + ",\"holidays\":[]}";
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
    public void fetch_http404_throwsIOException_doesNotCallParseAndFill() {
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

        try {
            src.fetch(2027);
            fail("HTTP 失败应抛 IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("404"));
        }
        assertEquals("HTTP 失败不应调用 parseAndFill", 0, parseCallCount.get());
    }

    @Test
    public void fetch_500_throwsIOException() {
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

        try {
            src.fetch(2028);
            fail("HTTP 失败应抛 IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("500"));
        }
    }

    @Test
    public void fetch_emptyBody_throwsIOException() {
        OkHttpClient client = newSuccessClient("", "application/json");

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

        try {
            src.fetch(2029);
            fail("空 body 应抛 IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("empty body"));
        }
    }

    @Test
    public void fetch_parseLeavesDataJsonEmpty_throwsIOException() {
        OkHttpClient client = newSuccessClient("{}", "application/json");

        HolidayDataSource src = new HolidayDataSource(client) {
            @Override
            protected String getUrl(int year) {
                return "https://example.test/holiday-" + year + ".json";
            }

            @Override
            protected void parseAndFill(HolidayCacheEntity entity, String content, int year) {
                entity.holidayCount = 1;
            }
        };

        try {
            src.fetch(2029);
            fail("无有效 dataJson 应抛 IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("invalid data"));
        }
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
                entity.dataJson = "{\"year\":" + year + ",\"holidays\":[]}";
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
