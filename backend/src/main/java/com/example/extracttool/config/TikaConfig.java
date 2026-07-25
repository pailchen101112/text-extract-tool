package com.example.extracttool.config;

import org.apache.tika.Tika;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Apache Tika 配置. Tika 是线程安全的, 单例即可。
 */
@Configuration
public class TikaConfig {

    @Bean
    public Tika tika() {
        return new Tika();
    }
}
