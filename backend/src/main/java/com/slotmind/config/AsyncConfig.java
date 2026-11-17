package com.slotmind.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Kept off the application class so slice tests (@DataJpaTest etc.) still start. */
@Configuration
@EnableAsync
public class AsyncConfig {}
