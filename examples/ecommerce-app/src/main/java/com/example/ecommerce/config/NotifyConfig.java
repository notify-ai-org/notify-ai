package com.example.ecommerce.config;

import com.example.agent.annotations.EnableNotify;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableNotify(basePackage = "com.example.ecommerce")
public class NotifyConfig {
}
