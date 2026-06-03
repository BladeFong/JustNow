package com.nearby.justnow.ui.main;

import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskQuadrantDegradeEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.ui.engine.TimeRemainingCalculator;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * MainViewModel 测试 — 重构后结构完整性验证。
 * F 重构抽提了 ComputeContext + prepareComputeContext()，此处验证类/方法存在性及字段完整性。
 * 标签过滤等运行时逻辑在 MainViewModelShortCompletionTest（Robolectric）中覆盖。
 */
public class MainViewModelTest {

    // ============================================================
    // ComputeContext 结构验证
    // ============================================================

    @Test
    public void computeContext_classExists() {
        Class<?>[] innerClasses = MainViewModel.class.getDeclaredClasses();
        boolean found = false;
        for (Class<?> cls : innerClasses) {
            if ("ComputeContext".equals(cls.getSimpleName())) {
                found = true;
                break;
            }
        }
        assertTrue("ComputeContext 内部类应当存在（F 重构抽提）", found);
    }

    @Test
    public void computeContext_isPrivateStatic() {
        for (Class<?> cls : MainViewModel.class.getDeclaredClasses()) {
            if ("ComputeContext".equals(cls.getSimpleName())) {
                int mods = cls.getModifiers();
                assertTrue("ComputeContext 应为 static", Modifier.isStatic(mods));
                assertTrue("ComputeContext 应为 private", Modifier.isPrivate(mods));
                return;
            }
        }
        fail("ComputeContext 类未找到");
    }

    @Test
    public void computeContext_hasRequiredFields() throws Exception {
        Class<?> computeContextClass = null;
        for (Class<?> cls : MainViewModel.class.getDeclaredClasses()) {
            if ("ComputeContext".equals(cls.getSimpleName())) {
                computeContextClass = cls;
                break;
            }
        }
        assertNotNull("ComputeContext 类应存在", computeContextClass);

        Field[] fields = computeContextClass.getDeclaredFields();
        Set<String> fieldNames = new HashSet<>();
        for (Field f : fields) {
            fieldNames.add(f.getName());
        }

        assertTrue("ComputeContext 应有 tasks 字段", fieldNames.contains("tasks"));
        assertTrue("ComputeContext 应有 tagMap 字段", fieldNames.contains("tagMap"));
        assertTrue("ComputeContext 应有 periods 字段", fieldNames.contains("periods"));
        assertTrue("ComputeContext 应有 timelinePeriods 字段", fieldNames.contains("timelinePeriods"));
        assertTrue("ComputeContext 应有 activeGroupType 字段", fieldNames.contains("activeGroupType"));
        assertTrue("ComputeContext 应有 status 字段", fieldNames.contains("status"));
        assertTrue("ComputeContext 应有 statusText 字段", fieldNames.contains("statusText"));
        assertTrue("ComputeContext 应有 priorityTagIds 字段", fieldNames.contains("priorityTagIds"));
        assertTrue("ComputeContext 应有 degradeMap 字段", fieldNames.contains("degradeMap"));
        assertTrue("ComputeContext 应有 executingTasks 字段", fieldNames.contains("executingTasks"));
        assertTrue("ComputeContext 应有 timelineItems 字段", fieldNames.contains("timelineItems"));
    }

    @Test
    public void computeContext_fieldTypes_correct() throws Exception {
        Class<?> cc = findComputeContext();
        assertNotNull(cc);

        assertEquals("tasks 类型", List.class, getFieldType(cc, "tasks"));
        assertEquals("tagMap 类型", Map.class, getFieldType(cc, "tagMap"));
        assertEquals("periods 类型", List.class, getFieldType(cc, "periods"));
        assertEquals("activeGroupType 类型", String.class, getFieldType(cc, "activeGroupType"));
        assertEquals("priorityTagIds 类型", Set.class, getFieldType(cc, "priorityTagIds"));
        assertEquals("degradeMap 类型", Map.class, getFieldType(cc, "degradeMap"));
        assertEquals("executingTasks 类型", List.class, getFieldType(cc, "executingTasks"));
    }

    // ============================================================
    // 重构后方法存在性验证
    // ============================================================

    @Test
    public void prepareComputeContext_methodExists() throws Exception {
        Method method = MainViewModel.class.getDeclaredMethod("prepareComputeContext");
        assertNotNull("prepareComputeContext 方法应存在（F 重构抽提）", method);
        assertTrue("prepareComputeContext 应为 private",
                Modifier.isPrivate(method.getModifiers()));
    }

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
        assertNotNull("computeQuadrantOverviewSync 方法应存在（F 重构后调用 prepareComputeContext）",
                method);
        assertTrue("computeQuadrantOverviewSync 应为 private",
                Modifier.isPrivate(method.getModifiers()));
    }

    @Test
    public void prepareComputeContext_returnType_isComputeContext() throws Exception {
        Method method = MainViewModel.class.getDeclaredMethod("prepareComputeContext");
        Class<?> returnType = method.getReturnType();
        assertEquals("prepareComputeContext 返回类型应为 ComputeContext",
                "ComputeContext", returnType.getSimpleName());
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private static Class<?> findComputeContext() {
        for (Class<?> cls : MainViewModel.class.getDeclaredClasses()) {
            if ("ComputeContext".equals(cls.getSimpleName())) {
                return cls;
            }
        }
        return null;
    }

    private static Class<?> getFieldType(Class<?> clazz, String fieldName) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        return field.getType();
    }
}
