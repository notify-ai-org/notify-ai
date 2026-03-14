package com.example.banking.config;

import com.example.agent.annotations.EnableNotify;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableNotify(basePackage = "com.example.banking")
public class NotifyConfig {
}
