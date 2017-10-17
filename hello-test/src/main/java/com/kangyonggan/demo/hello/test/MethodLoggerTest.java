package com.kangyonggan.demo.hello.test;

import com.kangyonggan.demo.hello.core.MethodLogger;

/**
 * @author kangyonggan
 * @since 10/17/17
 */
public class MethodLoggerTest {

    @MethodLogger
    public void test() {
        System.out.println("test");
    }

    public static void main(String[] args) {
        new MethodLoggerTest().test();
    }

}
