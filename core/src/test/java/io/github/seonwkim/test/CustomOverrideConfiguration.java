package io.github.seonwkim.test;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import io.github.seonwkim.core.RootGuardianSupplierWrapper;

@Configuration
public class CustomOverrideConfiguration {
    @Bean
    @Primary
    public RootGuardianSupplierWrapper customRootGuardianSupplierWrapper() {
        return new RootGuardianSupplierWrapper(CustomTestRootGuardian::create);
    }
}
