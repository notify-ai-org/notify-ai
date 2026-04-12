package com.notify.ecommerce.config;

import com.notify.agent.annotations.EnableNotify;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableNotify(basePackage = "com.notify.ecommerce")
public class NotifyConfig {
}
