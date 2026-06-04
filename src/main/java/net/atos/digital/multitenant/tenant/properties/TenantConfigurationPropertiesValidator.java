

package net.atos.digital.multitenant.tenant.properties;

import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

public class TenantConfigurationPropertiesValidator implements Validator{

        private static final String[] VALIDATOR_CLASSES = { "javax.validation.Validator", "jakarta.validation.Validator",
            "javax.validation.ValidatorFactory", "jakarta.validation.ValidatorFactory", "javax.validation.bootstrap.GenericBootstrap", "jakarta.validation.bootstrap.GenericBootstrap" };

        private final Delegate delegate;

    public TenantConfigurationPropertiesValidator(ApplicationContext applicationContext) {
            this.delegate = new Delegate(applicationContext);
        }

        @Override
        public boolean supports(Class<?> type) {
            return this.delegate.supports(type);
        }

        @Override
        public void validate(Object target, Errors errors) {
            this.delegate.validate(target, errors);
        }

        static boolean isJsr303Present(ApplicationContext applicationContext) {
            ClassLoader classLoader = applicationContext.getClassLoader();
            for (String validatorClass : VALIDATOR_CLASSES) {
                if (!ClassUtils.isPresent(validatorClass, classLoader)) {
                    return false;
                }
            }
            return true;
        }

        private static class Delegate extends LocalValidatorFactoryBean {

            Delegate(ApplicationContext applicationContext) {
                setApplicationContext(applicationContext);
                //setMessageInterpolator(new MessageInterpolatorFactory().getObject());
                afterPropertiesSet();
            }
        }
}
