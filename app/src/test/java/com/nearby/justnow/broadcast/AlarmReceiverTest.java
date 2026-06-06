package com.nearby.justnow.broadcast;

import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.model.ActivePeriodGroup;
import com.nearby.justnow.data.repository.TimePeriodRepository;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AlarmReceiver 测试 — 延迟按钮边界。
 */
public class AlarmReceiverTest {

    @Test
    public void canDelay_delayedBeforePeriodEnd_returnsTrue() throws Exception {
        assertTrue(invokeCanDelay(10 * 60, period(9 * 60, 10 * 60 + 31)));
    }

    @Test
    public void canDelay_delayedAtPeriodEnd_returnsFalse() throws Exception {
        assertFalse(invokeCanDelay(10 * 60, period(9 * 60, 10 * 60 + 30)));
    }

    private static boolean invokeCanDelay(int scheduledMinute, TimePeriodEntity period)
            throws Exception {
        TimePeriodRepository repo = mock(TimePeriodRepository.class);
        ActivePeriodGroup group = new ActivePeriodGroup(null, Arrays.asList(period));
        when(repo.getActivePeriodGroupSync()).thenReturn(group);

        Method method = AlarmReceiver.class.getDeclaredMethod(
                "canDelay", int.class, TimePeriodRepository.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, scheduledMinute, repo);
    }

    private static TimePeriodEntity period(int startMinute, int endMinute) {
        TimePeriodEntity period = new TimePeriodEntity();
        period.startMinute = startMinute;
        period.endMinute = endMinute;
        return period;
    }
}
