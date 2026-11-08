package io.github.seonwkim.test;

import io.github.seonwkim.core.RootGuardianSupplierWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class CustomOverrideConfiguration {
    @Bean
    @Primary
    public RootGuardianSupplierWrapper customRootGuardianSupplierWrapper() {
        return new RootGuardianSupplierWrapper(CustomTestRootGuardian::create);
    }
}
