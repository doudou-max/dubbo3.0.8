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
package org.apache.dubbo.registry.client.migration;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.status.reporter.FrameworkStatusReporter;
import org.apache.dubbo.registry.client.migration.model.MigrationRule;
import org.apache.dubbo.registry.client.migration.model.MigrationStep;

public class MigrationRuleHandler<T> {
    public static final String DUBBO_SERVICEDISCOVERY_MIGRATION = "dubbo.application.service-discovery.migration";
    private static final Logger logger = LoggerFactory.getLogger(MigrationRuleHandler.class);

    private MigrationClusterInvoker<T> migrationInvoker;
    private MigrationStep currentStep;
    private Float currentThreshold = 0f;
    private URL consumerURL;

    public MigrationRuleHandler(MigrationClusterInvoker<T> invoker, URL url) {
        this.migrationInvoker = invoker;
        this.consumerURL = url;
    }

    /**
     * 三种订阅模式的核心方法
     * dubbo.application.service-discovery.migration 三种类型：
     *    FORCE_APPLICATION:只消费接口级
     *    APPLICATION_FIRST:智能决策接口级/应用级，双订阅
     *    FORCE_INTERFACE:只消费应用级地址
     */
    public synchronized void doMigrate(MigrationRule rule) {
        // 接口级订阅
        if (migrationInvoker instanceof ServiceDiscoveryMigrationInvoker) {
            refreshInvoker(MigrationStep.FORCE_APPLICATION, 1.0f, rule);
            return;
        }

        // 默认情况下就是APPLICATION_FIRST， 智能匹配 应用/接口级订阅
        // initial step : APPLICATION_FIRST
        MigrationStep step = MigrationStep.APPLICATION_FIRST;
        float threshold = -1f;

        try {
            step = rule.getStep(consumerURL);
            threshold = rule.getThreshold(consumerURL);
        } catch (Exception e) {
            logger.error("Failed to get step and threshold info from rule: " + rule, e);
        }

        // 刷新 invoker，debug 看 if 条件里面
        if (refreshInvoker(step, threshold, rule)) {
            // refresh success, update rule
            setMigrationRule(rule);
        }
    }

    /**
     * consumer 订阅处理
     *   APPLICATION_FIRST: 智能决策，双订阅
     *   FORCE_APPLICATION: 只消费接口级地址
     *   FORCE_INTERFACE: 只消费应用级地址
     */
    private boolean refreshInvoker(MigrationStep step, Float threshold, MigrationRule newRule) {
        if (step == null || threshold == null) {
            throw new IllegalStateException("Step or threshold of migration rule cannot be null");
        }
        MigrationStep originStep = currentStep;

        if ((currentStep == null || currentStep != step) || !currentThreshold.equals(threshold)) {
            boolean success = true;
            // 三种订阅模式的核心处理
            switch (step) {
                case APPLICATION_FIRST:
                    // ServiceDiscoveryMigrationInvoker.migrateToApplicationFirstInvoker()
                    migrationInvoker.migrateToApplicationFirstInvoker(newRule);
                    break;
                case FORCE_APPLICATION:
                    success = migrationInvoker.migrateToForceApplicationInvoker(newRule);
                    break;
                case FORCE_INTERFACE:
                default:
                    success = migrationInvoker.migrateToForceInterfaceInvoker(newRule);
            }

            if (success) {
                setCurrentStepAndThreshold(step, threshold);
                logger.info("Succeed Migrated to " + step + " mode. Service Name: " + consumerURL.getDisplayServiceKey());
                report(step, originStep, "true");
            } else {
                // migrate failed, do not save new step and rule
                logger.warn("Migrate to " + step + " mode failed. Probably not satisfy the threshold you set "
                        + threshold + ". Please try re-publish configuration if you still after check.");
                report(step, originStep, "false");
            }

            return success;
        }
        // ignore if step is same with previous, will continue override rule for MigrationInvoker
        return true;
    }

    private void report(MigrationStep step, MigrationStep originStep, String success) {
        if (FrameworkStatusReporter.hasReporter()) {
            FrameworkStatusReporter.reportMigrationStepStatus(
                    FrameworkStatusReporter.createMigrationStepReport(consumerURL.getServiceInterface(), consumerURL.getVersion(),
                            consumerURL.getGroup(), String.valueOf(originStep), String.valueOf(step), success));
        }
    }

    private void setMigrationRule(MigrationRule rule) {
        this.migrationInvoker.setMigrationRule(rule);
    }

    private MigrationStep getMigrationStep(MigrationRule rule, MigrationStep step) {
        MigrationStep configuredStep = rule.getStep(consumerURL);
        step = configuredStep == null ? step : configuredStep;
        return step;
    }

    private Float getMigrationThreshold(MigrationRule rule, Float threshold) {
        Float configuredThreshold = rule.getThreshold(consumerURL);
        threshold = configuredThreshold == null ? threshold : configuredThreshold;
        return threshold;
    }

    private void setCurrentStepAndThreshold(MigrationStep currentStep, Float currentThreshold) {
        if (currentThreshold != null) {
            this.currentThreshold = currentThreshold;
        }
        if (currentStep != null) {
            this.currentStep = currentStep;
            this.migrationInvoker.setMigrationStep(currentStep);
        }
    }
}
