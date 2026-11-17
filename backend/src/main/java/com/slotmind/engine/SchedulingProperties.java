package com.slotmind.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("slotmind.scheduling")
public record SchedulingProperties(String strategy) {}
