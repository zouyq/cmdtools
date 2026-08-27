package com.github.zouyq.utils;

import com.intellij.openapi.util.SystemInfo;

public enum OperatingSystem {
    WINDOWS,
    MAC,
    LINUX,
    UNSUPPORTED;

    public static OperatingSystem current() {
        if (SystemInfo.isWindows) {
            return WINDOWS;
        }
        if (SystemInfo.isMac) {
            return MAC;
        }
        if (SystemInfo.isLinux) {
            return LINUX;
        }
        return UNSUPPORTED;
    }

    public boolean isSupported() {
        return this != UNSUPPORTED;
    }
}
