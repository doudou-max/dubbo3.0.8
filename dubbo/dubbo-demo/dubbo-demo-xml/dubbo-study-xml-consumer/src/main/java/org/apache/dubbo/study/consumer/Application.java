package org.apache.dubbo.study.consumer;

import org.apache.commons.lang3.RandomUtils;
import org.apache.dubbo.demo.DubboStudyService;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author: doudou
 * @since: 2022-09-25
 */
public class Application {

    public static void main(String[] args) throws Exception {

        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("spring/dubbo-consumer.xml");

        context.start();

        DubboStudyService dubboStudyService = context.getBean("dubboStudyService", DubboStudyService.class);

        //System.out.println(dubboStudyService.dubboStudy("dubbo consumer"));

        while (true) {
            System.out.println(dubboStudyService.dubboStudy("dubbo consumer " + RandomUtils.nextInt()));
            Thread.sleep(1000L);
        }
    }

}
