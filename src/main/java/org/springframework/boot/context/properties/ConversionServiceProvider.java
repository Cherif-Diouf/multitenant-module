
package org.springframework.boot.context.properties;

import org.springframework.context.ApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConversionServiceProvider {
    private final ApplicationContext applicationContext;

    public ConversionServiceProvider(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public List<ConversionService> getConversionServices() {
        return new ConversionServiceDeducer(this.applicationContext).getConversionServices();
    }
}
