package com.apache.dubbo.study.provider.impl;

import org.apache.dubbo.demo.DubboStudyService;

/**
 * @author: doudou
 * @since: 2022-09-25
 */
public class DubboStudyServiceImpl implements DubboStudyService {

    @Override
    public String dubboStudy(String name) {
        return "dubbo provider return name: " + name;
    }

}
