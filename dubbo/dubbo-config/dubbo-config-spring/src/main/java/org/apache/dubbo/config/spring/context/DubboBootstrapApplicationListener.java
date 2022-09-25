/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.config.spring.context;

import org.apache.dubbo.config.DubboShutdownHook;
import org.apache.dubbo.config.bootstrap.BootstrapTakeoverMode;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;

import org.apache.dubbo.config.spring.context.event.DubboAnnotationInitedEvent;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;

import static org.springframework.util.ObjectUtils.nullSafeEquals;

/**
 * The {@link ApplicationListener} for {@link DubboBootstrap}'s lifecycle when the {@link ContextRefreshedEvent}
 * and {@link ContextClosedEvent} raised
 *
 * ApplicationListener 应用事件监听
 * onApplicationEvent 监听事件，启动事件
 *
 * @since 2.7.5
 */
public class DubboBootstrapApplicationListener implements ApplicationListener, ApplicationContextAware, Ordered {

    /**
     * The bean name of {@link DubboBootstrapApplicationListener}
     *
     * @since 2.7.6
     */
    public static final String BEAN_NAME = "dubboBootstrapApplicationListener";

    private final DubboBootstrap dubboBootstrap;
    private ApplicationContext applicationContext;

    public DubboBootstrapApplicationListener() {
        this.dubboBootstrap = initBootstrap();
    }

    public DubboBootstrapApplicationListener(ApplicationContext applicationContext) {
        this.dubboBootstrap = initBootstrap();
        DubboBootstrapStartStopListenerSpringAdapter.applicationContext = applicationContext;
    }

    private DubboBootstrap initBootstrap() {
        DubboBootstrap dubboBootstrap = DubboBootstrap.getInstance();
        if (dubboBootstrap.getTakeoverMode() != BootstrapTakeoverMode.MANUAL) {
            dubboBootstrap.setTakeoverMode(BootstrapTakeoverMode.SPRING);
        }
        return dubboBootstrap;
    }
    // spring 中的应用监听器，在实例化和初始化完成 bean 集合对象之后，调用 finishRefresh() 方法执行所有事件
    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (isOriginalEventSource(event)) {
            // dubbo 通过注解方式初始化
            if (event instanceof DubboAnnotationInitedEvent) {
                // This event will be notified at AbstractApplicationContext.registerListeners(),
                // init dubbo config beans before spring singleton beans
                applicationContext.getBean(DubboConfigBeanInitializer.BEAN_NAME, DubboConfigBeanInitializer.class);

                // All infrastructure config beans are loaded, initialize dubbo here
                DubboBootstrap.getInstance().initialize();
            }
            // 非注解的方式初始化 (阅读源码通过该种方式)
            else if (event instanceof ApplicationContextEvent) {
                // 调用应用上下文的事件，调用 DubboBootstrap 的 start()
                this.onApplicationContextEvent((ApplicationContextEvent) event);
            }
        }
    }

    /**
     * Is original {@link ApplicationContext} as the event source
     * @param event {@link ApplicationEvent}
     * @return if original, return <code>true</code>, or <code>false</code>
     */
    private boolean isOriginalEventSource(ApplicationEvent event) {

        boolean originalEventSource = nullSafeEquals(getApplicationContext(), event.getSource());
//        if (!originalEventSource) {
//            if (log.isDebugEnabled()) {
//                log.debug("The source of event[" + event.getSource() + "] is not original!");
//            }
//        }
        return originalEventSource;
    }

    private void onApplicationContextEvent(ApplicationContextEvent event) {
        if (DubboBootstrapStartStopListenerSpringAdapter.applicationContext == null) {
            DubboBootstrapStartStopListenerSpringAdapter.applicationContext = event.getApplicationContext();
        }
        // 事件刷新 (调用 DubboBootstrap 的 start)
        if (event instanceof ContextRefreshedEvent) {
            onContextRefreshedEvent((ContextRefreshedEvent) event);     // 调用这里
        } else if (event instanceof ContextClosedEvent) {
            onContextClosedEvent((ContextClosedEvent) event);
        }
    }

    /**
     * 通过 spring 的 事件发布方式
     * @param event
     */
    private void onContextRefreshedEvent(ContextRefreshedEvent event) {
        if (dubboBootstrap.getTakeoverMode() == BootstrapTakeoverMode.SPRING) {
            dubboBootstrap.start();
        }
    }

    private void onContextClosedEvent(ContextClosedEvent event) {
        if (dubboBootstrap.getTakeoverMode() == BootstrapTakeoverMode.SPRING) {
            // will call dubboBootstrap.stop() through shutdown callback.
            DubboShutdownHook.getDubboShutdownHook().run();
        }
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
