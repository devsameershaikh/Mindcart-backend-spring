package com.mindcart.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BackendApplication {

    public static void main(String[] args) {
        // Belt-and-braces: make sure a stray uncaught exception on any
        // non-request thread (e.g. an @Async task or the socket.io event
        // loop) is logged instead of silently killing that thread or,
        // worse, taking the JVM down.
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) ->
                org.slf4j.LoggerFactory.getLogger(BackendApplication.class)
                        .error("Uncaught exception on thread {}", thread.getName(), ex));

        SpringApplication.run(BackendApplication.class, args);
    }
}
