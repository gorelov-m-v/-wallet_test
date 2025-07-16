package com.uplatform.wallet_tests.allure;

import io.qameta.allure.Allure;
import io.qameta.allure.model.Label;
import org.junit.jupiter.api.extension.*;

public class CustomSuiteExtension
        implements BeforeTestExecutionCallback {

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        Suite ann = context.getRequiredTestClass().getAnnotation(Suite.class);
        if (ann == null) {
            return;
        }
        Allure.getLifecycle().updateTestCase(tc ->
                tc.getLabels().removeIf(l -> "suite".equals(l.getName()))
        );
        Allure.getLifecycle().updateTestCase(tc ->
                tc.getLabels().add(new Label().setName("suite").setValue(ann.value()))
        );
    }
}
