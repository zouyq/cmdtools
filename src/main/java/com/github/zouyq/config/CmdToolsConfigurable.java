package com.github.zouyq.config;

import com.github.zouyq.utils.OperatingSystem;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class CmdToolsConfigurable implements Configurable {
    private static final int HELP_WIDTH = 360;

    private JPanel panel;
    private ComboBox<String> windowsTerminal;
    private TextFieldWithBrowseButton windowsCustomPath;
    private JBTextField windowsCustomArgs;
    private ComboBox<String> macTerminal;
    private TextFieldWithBrowseButton macCustomPath;
    private JBTextField macCustomArgs;
    private ComboBox<String> linuxTerminal;
    private TextFieldWithBrowseButton linuxCustomPath;
    private JBTextField linuxCustomArgs;
    private JBTextField startupCommand;
    private JBTextField processRegex;
    private JBCheckBox confirmBeforeKilling;

    @Override
    public @Nls String getDisplayName() {
        return "CmdTools";
    }

    @Override
    public @Nullable JComponent createComponent() {
        OperatingSystem os = OperatingSystem.current();

        windowsTerminal = compactCombo(CmdToolsSettings.WINDOWS_TERMINAL_OPTIONS);
        windowsCustomPath = browseField(false);
        windowsCustomArgs = argsField("Optional; $DIR = working dir (wt auto -d)");

        macTerminal = compactCombo(CmdToolsSettings.MAC_TERMINAL_OPTIONS);
        macCustomPath = browseField(true);
        macCustomArgs = argsField("Optional; $DIR = working directory");

        linuxTerminal = compactCombo(CmdToolsSettings.LINUX_TERMINAL_OPTIONS);
        linuxCustomPath = browseField(false);
        linuxCustomArgs = argsField("Optional; $DIR = working directory");

        startupCommand = argsField("e.g. claude  or  pi");
        processRegex = argsField("e.g. (?i)java|node");
        confirmBeforeKilling = new JBCheckBox("Confirm before terminating matched processes");

        windowsTerminal.addActionListener(event -> updateCustomFields());
        macTerminal.addActionListener(event -> updateCustomFields());
        linuxTerminal.addActionListener(event -> updateCustomFields());

        FormBuilder form = FormBuilder.createFormBuilder();
        form.addSeparator(8);

        if (os == OperatingSystem.WINDOWS || os == OperatingSystem.UNSUPPORTED) {
            form.addLabeledComponent(wrapLabel("Terminal"), windowsTerminal, 1, false)
                    .addLabeledComponent(wrapLabel("Custom executable"), windowsCustomPath, 1, false)
                    .addLabeledComponent(wrapLabel("Custom arguments"), windowsCustomArgs, 1, false);
        }
        if (os == OperatingSystem.MAC || os == OperatingSystem.UNSUPPORTED) {
            form.addLabeledComponent(wrapLabel("Terminal"), macTerminal, 1, false)
                    .addLabeledComponent(wrapLabel("Custom app / .app"), macCustomPath, 1, false)
                    .addLabeledComponent(wrapLabel("Custom arguments"), macCustomArgs, 1, false);
        }
        if (os == OperatingSystem.LINUX || os == OperatingSystem.UNSUPPORTED) {
            form.addLabeledComponent(wrapLabel("Terminal"), linuxTerminal, 1, false)
                    .addLabeledComponent(wrapLabel("Custom executable"), linuxCustomPath, 1, false)
                    .addLabeledComponent(wrapLabel("Custom arguments"), linuxCustomArgs, 1, false);
        }

        form.addLabeledComponent(wrapLabel("Run in terminal"), startupCommand, 1, false)
                .addComponent(helpLabel(
                        "Optional. Opens the terminal in the current file directory, then runs this command "
                                + "(for example <code>claude</code> or <code>pi</code>). "
                                + "Leave blank for a normal shell. Ignored when Custom arguments are set."))
                .addSeparator(12)
                .addLabeledComponent(wrapLabel("Process name regex"), processRegex, 1, false)
                .addComponent(helpLabel(
                        "Java regex matched against the executable file name (<code>Pattern.find()</code>). "
                                + "Use <code>|</code> for multiple names.<br>"
                                + "Examples:<br>"
                                + "<code>(?i)java</code> — java / java.exe / javaw.exe<br>"
                                + "<code>(?i)java|node</code> — java and node<br>"
                                + "<code>(?i)java|node|gradle</code> — java, node, and gradle<br>"
                                + "IDE process, parents, and other JetBrains IDE JVMs are never killed."))
                .addComponent(confirmBeforeKilling)
                .addComponentFillVertically(new JPanel(), 0);

        panel = form.getPanel();
        panel.setBorder(JBUI.Borders.empty());
        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        CmdToolsSettings.Data data = CmdToolsSettings.getInstance().data();
        return !Objects.equals(windowsTerminal.getSelectedItem(), data.windowsTerminal)
                || !windowsCustomPath.getText().trim().equals(nullToEmpty(data.windowsCustomTerminalPath))
                || !windowsCustomArgs.getText().trim().equals(nullToEmpty(data.windowsCustomTerminalArgs))
                || !Objects.equals(macTerminal.getSelectedItem(), data.macTerminal)
                || !macCustomPath.getText().trim().equals(nullToEmpty(data.macCustomTerminalPath))
                || !macCustomArgs.getText().trim().equals(nullToEmpty(data.macCustomTerminalArgs))
                || !Objects.equals(linuxTerminal.getSelectedItem(), data.linuxTerminal)
                || !linuxCustomPath.getText().trim().equals(nullToEmpty(data.linuxCustomTerminalPath))
                || !linuxCustomArgs.getText().trim().equals(nullToEmpty(data.linuxCustomTerminalArgs))
                || !startupCommand.getText().trim().equals(nullToEmpty(data.terminalStartupCommand))
                || !processRegex.getText().trim().equals(data.processNameRegex)
                || confirmBeforeKilling.isSelected() != data.confirmBeforeKilling;
    }

    @Override
    public void apply() throws ConfigurationException {
        String regex = processRegex.getText().trim();
        if (regex.isEmpty()) {
            throw new ConfigurationException("The process-name regular expression cannot be empty.");
        }
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException exception) {
            throw new ConfigurationException("Invalid process-name regular expression: " + exception.getDescription());
        }

        OperatingSystem os = OperatingSystem.current();
        String windowsSelection = Objects.toString(windowsTerminal.getSelectedItem(), CmdToolsSettings.WINDOWS_POWERSHELL);
        String windowsPath = windowsCustomPath.getText().trim();
        if (os == OperatingSystem.WINDOWS
                && CmdToolsSettings.WINDOWS_CUSTOM.equals(windowsSelection)
                && windowsPath.isEmpty()) {
            throw new ConfigurationException("A Windows custom terminal executable is required.");
        }

        String macSelection = Objects.toString(macTerminal.getSelectedItem(), CmdToolsSettings.MAC_TERMINAL);
        String macPath = macCustomPath.getText().trim();
        if (os == OperatingSystem.MAC
                && CmdToolsSettings.MAC_CUSTOM.equals(macSelection)
                && macPath.isEmpty()) {
            throw new ConfigurationException("A macOS custom terminal application is required.");
        }

        String linuxSelection = Objects.toString(linuxTerminal.getSelectedItem(), CmdToolsSettings.LINUX_AUTO);
        String linuxPath = linuxCustomPath.getText().trim();
        if (os == OperatingSystem.LINUX
                && CmdToolsSettings.LINUX_CUSTOM.equals(linuxSelection)
                && linuxPath.isEmpty()) {
            throw new ConfigurationException("A Linux custom terminal executable is required.");
        }

        CmdToolsSettings.Data data = CmdToolsSettings.getInstance().data();
        data.windowsTerminal = windowsSelection;
        data.windowsCustomTerminalPath = windowsPath;
        data.windowsCustomTerminalArgs = windowsCustomArgs.getText().trim();
        data.macTerminal = macSelection;
        data.macCustomTerminalPath = macPath;
        data.macCustomTerminalArgs = macCustomArgs.getText().trim();
        data.linuxTerminal = linuxSelection;
        data.linuxCustomTerminalPath = linuxPath;
        data.linuxCustomTerminalArgs = linuxCustomArgs.getText().trim();
        data.terminalStartupCommand = startupCommand.getText().trim();
        data.processNameRegex = regex;
        data.confirmBeforeKilling = confirmBeforeKilling.isSelected();
    }

    @Override
    public void reset() {
        CmdToolsSettings.Data data = CmdToolsSettings.getInstance().data();
        data.migrateLegacyFields();
        windowsTerminal.setSelectedItem(data.windowsTerminal);
        windowsCustomPath.setText(nullToEmpty(data.windowsCustomTerminalPath));
        windowsCustomArgs.setText(nullToEmpty(data.windowsCustomTerminalArgs));
        macTerminal.setSelectedItem(data.macTerminal);
        macCustomPath.setText(nullToEmpty(data.macCustomTerminalPath));
        macCustomArgs.setText(nullToEmpty(data.macCustomTerminalArgs));
        linuxTerminal.setSelectedItem(data.linuxTerminal);
        linuxCustomPath.setText(nullToEmpty(data.linuxCustomTerminalPath));
        linuxCustomArgs.setText(nullToEmpty(data.linuxCustomTerminalArgs));
        startupCommand.setText(nullToEmpty(data.terminalStartupCommand));
        processRegex.setText(data.processNameRegex);
        confirmBeforeKilling.setSelected(data.confirmBeforeKilling);
        updateCustomFields();
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        windowsTerminal = null;
        windowsCustomPath = null;
        windowsCustomArgs = null;
        macTerminal = null;
        macCustomPath = null;
        macCustomArgs = null;
        linuxTerminal = null;
        linuxCustomPath = null;
        linuxCustomArgs = null;
        startupCommand = null;
        processRegex = null;
        confirmBeforeKilling = null;
    }

    private void updateCustomFields() {
        boolean windowsCustom = CmdToolsSettings.WINDOWS_CUSTOM.equals(windowsTerminal.getSelectedItem());
        windowsCustomPath.setEnabled(windowsCustom);
        windowsCustomArgs.setEnabled(windowsCustom);

        boolean macCustom = CmdToolsSettings.MAC_CUSTOM.equals(macTerminal.getSelectedItem());
        macCustomPath.setEnabled(macCustom);
        macCustomArgs.setEnabled(macCustom);

        boolean linuxCustom = CmdToolsSettings.LINUX_CUSTOM.equals(linuxTerminal.getSelectedItem());
        linuxCustomPath.setEnabled(linuxCustom);
        linuxCustomArgs.setEnabled(linuxCustom);
    }

    private static ComboBox<String> compactCombo(String[] options) {
        ComboBox<String> combo = new ComboBox<>(options);
        constrainWidth(combo);
        return combo;
    }

    private static JBTextField argsField(String emptyText) {
        JBTextField field = new JBTextField();
        field.getEmptyText().setText(emptyText);
        constrainWidth(field);
        return field;
    }

    private static TextFieldWithBrowseButton browseField(boolean allowFolders) {
        TextFieldWithBrowseButton field = new TextFieldWithBrowseButton();
        FileChooserDescriptor descriptor = allowFolders
                ? FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor()
                : FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
        descriptor.setTitle(allowFolders ? "Select Terminal Application" : "Select Terminal Executable");
        field.addBrowseFolderListener(new TextBrowseFolderListener(descriptor));
        constrainWidth(field);
        return field;
    }

    private static JBLabel wrapLabel(String text) {
        JBLabel label = new JBLabel(text + ":");
        label.setPreferredSize(new Dimension(JBUI.scale(130), label.getPreferredSize().height));
        return label;
    }

    private static JBLabel helpLabel(String htmlBody) {
        JBLabel label = new JBLabel("<html><body style='width:" + HELP_WIDTH + "px'>" + htmlBody + "</body></html>");
        label.setBorder(JBUI.Borders.emptyTop(4));
        constrainWidth(label);
        return label;
    }

    private static void constrainWidth(JComponent component) {
        Dimension preferred = component.getPreferredSize();
        component.setPreferredSize(new Dimension(JBUI.scale(280), preferred.height));
        component.setMinimumSize(new Dimension(0, preferred.height));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
