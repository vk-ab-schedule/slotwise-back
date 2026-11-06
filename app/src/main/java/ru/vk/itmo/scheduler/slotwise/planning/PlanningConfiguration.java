package ru.vk.itmo.scheduler.slotwise.planning;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PlanningProperties.class)
class PlanningConfiguration {
    @Bean
    Clock planningClock() {
        return Clock.systemUTC();
    }
}
