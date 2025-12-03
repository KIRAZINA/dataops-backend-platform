// src/test/java/com/dataops/platform/test/TestUtils.java
package com.dataops.platform.test;

import com.github.javafaker.Faker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestUtils {

    private static final Faker FAKER = new Faker();

    public static Faker faker() {
        return FAKER;
    }
}