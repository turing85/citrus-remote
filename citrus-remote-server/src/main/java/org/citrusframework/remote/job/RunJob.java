/*
 * Copyright 2006-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citrusframework.remote.job;

import org.citrusframework.main.TestRunConfiguration;
import org.citrusframework.remote.CitrusRemoteConfiguration;
import org.citrusframework.remote.controller.RunController;
import org.citrusframework.remote.model.RemoteResult;
import org.citrusframework.remote.listener.RemoteTestListener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author Christoph Deppisch
 * @since 2.7.4
 */
public record RunJob (
        CitrusRemoteConfiguration configuration,
        TestRunConfiguration runConfiguration,
        RemoteTestListener remoteTestListener)
        implements Supplier<List<RemoteResult>> {

    @Override
    public List<RemoteResult> get() {
        RunController runController = new RunController(configuration);

        runController.setEngine(runConfiguration.getEngine());
        runController.setIncludes(runConfiguration.getIncludes());

        if (!runConfiguration.getDefaultProperties().isEmpty()) {
            runController.addDefaultProperties(runConfiguration.getDefaultProperties());
        }

        if (runConfiguration.getPackages().isEmpty() &&
                runConfiguration.getTestSources().isEmpty()) {
            runController.runAll();
        }

        if (!runConfiguration.getPackages().isEmpty()) {
            runController.runPackages(runConfiguration.getPackages());
        }

        if (!runConfiguration.getTestSources().isEmpty()) {
            runController.runClasses(runConfiguration.getTestSources());
        }

        List<RemoteResult> results = new ArrayList<>();
        remoteTestListener.getResults()
                .doWithResults(result -> results.add(RemoteResult.fromTestResult(result)));
        return results;
    }
}
