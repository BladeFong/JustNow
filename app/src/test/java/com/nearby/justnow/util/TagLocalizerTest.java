package com.nearby.justnow.util;

import static org.junit.Assert.assertEquals;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TagLocalizerTest {
    @Test
    public void testLocalizationMapping() {
        Context context = ApplicationProvider.getApplicationContext();
        String localized = TagLocalizer.getLocalizedName(context, "美术");
        String dbName = TagLocalizer.getDbTagName(context, localized);
        assertEquals("美术", dbName);

        String custom = TagLocalizer.getLocalizedName(context, "自定义标签");
        assertEquals("自定义标签", custom);
        assertEquals("自定义标签", TagLocalizer.getDbTagName(context, "自定义标签"));
    }
}
