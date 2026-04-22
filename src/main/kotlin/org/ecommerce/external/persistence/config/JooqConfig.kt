package org.ecommerce.external.persistence.config

import org.jooq.conf.ExecuteWithoutWhere.THROW
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JooqConfig {

    @Bean
    fun defaultConfigurationCustomizer(): DefaultConfigurationCustomizer =
        DefaultConfigurationCustomizer { configuration ->
            configuration.settings()
                .withExecuteUpdateWithoutWhere(THROW)
                .withExecuteDeleteWithoutWhere(THROW)
                .withRenderSchema(false)
        }
}
