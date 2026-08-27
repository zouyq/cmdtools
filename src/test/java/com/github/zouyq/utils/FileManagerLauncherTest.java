package com.github.zouyq.utils;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileManagerLauncherTest {
    @Test
    public void revealsFileOnWindowsAndMac() throws Exception {
        Path directory = Files.createTempDirectory("cmdtools-test-");
        Path file = Files.createFile(directory.resolve("example.txt"));
        try {
            assertEquals(List.of("explorer.exe", "/select," + file),
                    FileManagerLauncher.commandFor(OperatingSystem.WINDOWS, file));
            assertEquals(List.of("open", "-R", file.toString()),
                    FileManagerLauncher.commandFor(OperatingSystem.MAC, file));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void linuxFileRevealFallsBackToParentDirectory() throws Exception {
        Path directory = Files.createTempDirectory("cmdtools-test-");
        Path file = Files.createFile(directory.resolve("example.txt"));
        try {
            List<List<String>> commands = FileManagerLauncher.commandsFor(OperatingSystem.LINUX, file);
            assertTrue(commands.size() >= 1);

            List<String> fallback = commands.get(commands.size() - 1);
            assertEquals("xdg-open", TerminalLauncher.executableName(fallback.get(0)));
            assertEquals(directory.toString(), fallback.get(1));

            if (commands.size() > 1) {
                List<String> reveal = commands.get(0);
                assertTrue(reveal.stream().anyMatch(part -> part.contains("FileManager1.ShowItems")));
                assertTrue(reveal.stream().anyMatch(part -> part.startsWith("array:string:file:")));
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}
