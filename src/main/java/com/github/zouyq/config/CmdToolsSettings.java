package com.github.zouyq.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service
@State(name = "CmdToolsSettings", storages = @Storage("cmdtools.xml"))
public final class CmdToolsSettings implements PersistentStateComponent<CmdToolsSettings.Data> {
    public static final String WINDOWS_POWERSHELL = "PowerShell";
    public static final String WINDOWS_CMD = "Command Prompt (cmd)";
    public static final String WINDOWS_CUSTOM = "Custom executable";

    public static final String MAC_TERMINAL = "Terminal";
    public static final String MAC_ITERM = "iTerm";
    public static final String MAC_ITERM2 = "iTerm2";
    public static final String MAC_ALACRITTY = "Alacritty";
    public static final String MAC_KITTY = "kitty";
    public static final String MAC_WEZTERM = "WezTerm";
    public static final String MAC_WARP = "Warp";
    public static final String MAC_CUSTOM = "Custom application";

    public static final String LINUX_AUTO = "Automatic detection";
    public static final String LINUX_GNOME = "gnome-terminal";
    public static final String LINUX_KONSOLE = "konsole";
    public static final String LINUX_XFCE = "xfce4-terminal";
    public static final String LINUX_MATE = "mate-terminal";
    public static final String LINUX_TILIX = "tilix";
    public static final String LINUX_KITTY = "kitty";
    public static final String LINUX_WEZTERM = "wezterm";
    public static final String LINUX_ALACRITTY = "alacritty";
    public static final String LINUX_XTERM = "xterm";
    public static final String LINUX_X_TERMINAL_EMULATOR = "x-terminal-emulator";
    public static final String LINUX_CUSTOM = "Custom executable";

    public static final String[] WINDOWS_TERMINAL_OPTIONS = {
            WINDOWS_POWERSHELL, WINDOWS_CMD, WINDOWS_CUSTOM
    };

    public static final String[] MAC_TERMINAL_OPTIONS = {
            MAC_TERMINAL, MAC_ITERM, MAC_ITERM2, MAC_ALACRITTY, MAC_KITTY, MAC_WEZTERM, MAC_WARP, MAC_CUSTOM
    };

    public static final String[] LINUX_TERMINAL_OPTIONS = {
            LINUX_AUTO, LINUX_GNOME, LINUX_KONSOLE, LINUX_XFCE, LINUX_MATE, LINUX_TILIX,
            LINUX_KITTY, LINUX_WEZTERM, LINUX_ALACRITTY, LINUX_XTERM, LINUX_X_TERMINAL_EMULATOR, LINUX_CUSTOM
    };

    private final Data data = new Data();

    public static CmdToolsSettings getInstance() {
        return ApplicationManager.getApplication().getService(CmdToolsSettings.class);
    }

    @Override
    public @Nullable Data getState() {
        return data;
    }

    @Override
    public void loadState(@NotNull Data state) {
        XmlSerializerUtil.copyBean(state, data);
        data.migrateLegacyFields();
    }

    public Data data() {
        return data;
    }

    public static final class Data {
        public String windowsTerminal = WINDOWS_POWERSHELL;
        public String windowsCustomTerminalPath = "";
        /** Optional args for custom Windows terminals. Use $DIR for the working directory. */
        public String windowsCustomTerminalArgs = "";

        public String macTerminal = MAC_TERMINAL;
        public String macCustomTerminalPath = "";
        /** Optional args for custom macOS terminals. Use $DIR for the working directory. */
        public String macCustomTerminalArgs = "";

        public String linuxTerminal = LINUX_AUTO;
        public String linuxCustomTerminalPath = "";
        /** Optional args for custom Linux terminals. Use $DIR for the working directory. */
        public String linuxCustomTerminalArgs = "";

        /**
         * Optional command executed inside the opened terminal (for example {@code claude} or {@code pi}).
         * Ignored when a platform custom-arguments field is already set.
         */
        public String terminalStartupCommand = "";

        /** @deprecated migrated into {@link #macTerminal} / {@link #macCustomTerminalPath} */
        @Deprecated
        public String macTerminalApplication = "";
        /** @deprecated migrated into {@link #linuxTerminal} / {@link #linuxCustomTerminalPath} */
        @Deprecated
        public String linuxTerminalPath = "";

        public String processNameRegex = "(?i)java";
        public boolean confirmBeforeKilling = true;

        public void migrateLegacyFields() {
            if (macCustomTerminalPath.isBlank()
                    && macTerminalApplication != null
                    && !macTerminalApplication.isBlank()) {
                String legacy = macTerminalApplication.trim();
                String preset = matchMacPreset(legacy);
                if (preset != null) {
                    macTerminal = preset;
                } else if (MAC_TERMINAL.equals(macTerminal) || macTerminal == null || macTerminal.isBlank()) {
                    macTerminal = MAC_CUSTOM;
                    macCustomTerminalPath = legacy;
                }
                macTerminalApplication = "";
            }

            if (linuxCustomTerminalPath.isBlank()
                    && linuxTerminalPath != null
                    && !linuxTerminalPath.isBlank()) {
                String legacy = linuxTerminalPath.trim();
                String preset = matchLinuxPreset(legacy);
                if (preset != null) {
                    linuxTerminal = preset;
                } else if (LINUX_AUTO.equals(linuxTerminal) || linuxTerminal == null || linuxTerminal.isBlank()) {
                    linuxTerminal = LINUX_CUSTOM;
                    linuxCustomTerminalPath = legacy;
                }
                linuxTerminalPath = "";
            }

            if (macTerminal == null || macTerminal.isBlank()) {
                macTerminal = MAC_TERMINAL;
            }
            if (linuxTerminal == null || linuxTerminal.isBlank()) {
                linuxTerminal = LINUX_AUTO;
            }
        }

        private static @Nullable String matchMacPreset(String value) {
            String normalized = stripAppSuffix(value);
            for (String option : MAC_TERMINAL_OPTIONS) {
                if (MAC_CUSTOM.equals(option)) {
                    continue;
                }
                if (option.equalsIgnoreCase(normalized) || option.equalsIgnoreCase(value)) {
                    return option;
                }
            }
            return null;
        }

        private static @Nullable String matchLinuxPreset(String value) {
            String base = value;
            int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
            if (slash >= 0) {
                base = value.substring(slash + 1);
            }
            for (String option : LINUX_TERMINAL_OPTIONS) {
                if (LINUX_AUTO.equals(option) || LINUX_CUSTOM.equals(option)) {
                    continue;
                }
                if (option.equalsIgnoreCase(base) || option.equalsIgnoreCase(value)) {
                    return option;
                }
            }
            return null;
        }

        private static String stripAppSuffix(String value) {
            String name = value;
            int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
            if (slash >= 0) {
                name = value.substring(slash + 1);
            }
            if (name.toLowerCase().endsWith(".app")) {
                return name.substring(0, name.length() - 4);
            }
            return name;
        }
    }
}
