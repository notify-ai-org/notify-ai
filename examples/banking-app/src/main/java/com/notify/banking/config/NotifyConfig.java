package com.notify.banking.config;

import com.notify.agent.annotations.EnableNotify;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableNotify(basePackage = "com.notify.banking")
public class NotifyConfig {
}
