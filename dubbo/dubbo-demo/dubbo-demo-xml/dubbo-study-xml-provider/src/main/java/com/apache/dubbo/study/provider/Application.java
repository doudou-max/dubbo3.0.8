package com.apache.dubbo.study.provider;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author: doudou
 * @since: 2022-09-25
 */
public class Application {

    public static void main(String[] args) throws Exception {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("spring/dubbo-provider.xml");
        context.start();
        System.in.read();
    }

}
