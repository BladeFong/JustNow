package com.nearby.justnow.ui.engine;

/**
 * 展示策略 YAML 校验失败。
 */
public class DisplayPolicyValidationException extends Exception {
    public DisplayPolicyValidationException(String message) {
        super(message);
    }

    public DisplayPolicyValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
