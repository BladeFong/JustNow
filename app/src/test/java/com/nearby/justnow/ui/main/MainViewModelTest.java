package com.nearby.justnow.ui.main;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.Assert.*;

/**
 * MainViewModel 测试 — 重构后方法存在性验证。
 */
public class MainViewModelTest {

    // ============================================================
    // 重构后方法存在性验证
    // ============================================================

    @Test
    public void recomputeSync_methodExists() throws Exception {
        Method method = MainViewModel.class.getDeclaredMethod("recomputeSync");
        assertNotNull("recomputeSync 方法应存在", method);
        assertTrue("recomputeSync 应为 private",
                Modifier.isPrivate(method.getModifiers()));
    }

    @Test
    public void computeQuadrantOverviewSync_methodExists() throws Exception {
        Method method = MainViewModel.class.getDeclaredMethod("computeQuadrantOverviewSync");
        assertNotNull("computeQuadrantOverviewSync 方法应存在",
                method);
        assertTrue("computeQuadrantOverviewSync 应为 private",
                Modifier.isPrivate(method.getModifiers()));
    }
}
