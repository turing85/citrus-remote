package org.citrusframework.remote.sample.entrypoint;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.io.IoBuilder;
import org.citrusframework.remote.CitrusRemoteServer;

public class CustomEntrypoint {
    public static void main(String... args) {
        System.setOut(IoBuilder
                .forLogger(LogManager.getLogger("system.out"))
                .buildPrintStream());
        CitrusRemoteServer.main(args);
    }
}
