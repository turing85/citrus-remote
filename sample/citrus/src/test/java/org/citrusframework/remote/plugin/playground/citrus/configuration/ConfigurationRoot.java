package org.citrusframework.remote.plugin.playground.citrus.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({ Http.class, Validators.class })
public class ConfigurationRoot {
}
