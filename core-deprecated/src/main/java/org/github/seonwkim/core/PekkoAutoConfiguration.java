package org.github.seonwkim.core;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(value = {PekkoSystemConfiguration.class})
@EnableConfigurationProperties(value = {PekkoConfiguration.class})
@ComponentScan(basePackages = "org.github.seonwkim.core")
public class PekkoAutoConfiguration {}
