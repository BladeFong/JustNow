package com.nearby.justnow;

import static org.junit.Assert.assertFalse;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class JustNowApplicationTest {
    @Test
    public void testIsTabletResourceDefaultIsFalse() {
        Context context = ApplicationProvider.getApplicationContext();
        boolean isTablet = context.getResources().getBoolean(R.bool.is_tablet);
        assertFalse("默认配置下 is_tablet 应为 false", isTablet);
    }
}
