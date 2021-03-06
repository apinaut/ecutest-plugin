/**
 * Copyright (c) 2015-2016 TraceTronic GmbH
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *   1. Redistributions of source code must retain the above copyright notice, this
 *      list of conditions and the following disclaimer.
 *
 *   2. Redistributions in binary form must reproduce the above copyright notice, this
 *      list of conditions and the following disclaimer in the documentation and/or
 *      other materials provided with the distribution.
 *
 *   3. Neither the name of TraceTronic GmbH nor the names of its
 *      contributors may be used to endorse or promote products derived from
 *      this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.tracetronic.jenkins.plugins.ecutest.test.client;

import hudson.model.TaskListener;
import hudson.remoting.Callable;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jenkins.security.MasterToSlaveCallable;

import org.apache.commons.lang.StringUtils;

import de.tracetronic.jenkins.plugins.ecutest.log.TTConsoleLogger;
import de.tracetronic.jenkins.plugins.ecutest.test.config.ExecutionConfig;
import de.tracetronic.jenkins.plugins.ecutest.test.config.GlobalConstant;
import de.tracetronic.jenkins.plugins.ecutest.test.config.TestConfig;
import de.tracetronic.jenkins.plugins.ecutest.wrapper.com.Constant;
import de.tracetronic.jenkins.plugins.ecutest.wrapper.com.ETComClient;
import de.tracetronic.jenkins.plugins.ecutest.wrapper.com.ETComException;
import de.tracetronic.jenkins.plugins.ecutest.wrapper.com.TestConfiguration;
import de.tracetronic.jenkins.plugins.ecutest.wrapper.com.api.ComConstants;

/**
 * Common base class for {@link PackageClient} and {@link ProjectClient}.
 *
 * @author Christian Pönisch <christian.poenisch@tracetronic.de>
 */
public abstract class AbstractTestClient implements TestClient {

    private final String testFile;
    private final TestConfig testConfig;
    private final ExecutionConfig executionConfig;
    private String testName;
    private String testDescription;
    private String testReportDir;
    private String testResult;

    /**
     * Instantiates a new {@link AbstractTestClient}.
     *
     * @param testFile
     *            the test file
     * @param testConfig
     *            the test configuration
     * @param executionConfig
     *            the execution configuration
     */
    public AbstractTestClient(final String testFile, final TestConfig testConfig,
            final ExecutionConfig executionConfig) {
        this.testFile = StringUtils.trimToEmpty(testFile);
        this.testConfig = testConfig;
        this.executionConfig = executionConfig;
        testName = "";
        testDescription = "";
        testReportDir = "";
        testResult = "";
    }

    /**
     * @return the test file path
     */
    public String getTestFile() {
        return testFile;
    }

    /**
     * @return the test configuration
     */
    public TestConfig getTestConfig() {
        return testConfig;
    }

    /**
     * @return the execution configuration
     */
    public ExecutionConfig getExecutionConfig() {
        return executionConfig;
    }

    /**
     * @return the test name
     */
    public String getTestName() {
        return testName;
    }

    /**
     * @param testName
     *            the test name to set
     */
    public void setTestName(final String testName) {
        this.testName = testName;
    }

    /**
     * @return the test description
     */
    public String getTestDescription() {
        return testDescription;
    }

    /**
     * @param testDescription
     *            the test description to set
     */
    public void setTestDescription(final String testDescription) {
        this.testDescription = testDescription;
    }

    /**
     * @return the test report directory
     */
    public String getTestReportDir() {
        return testReportDir;
    }

    /**
     * @param testReportDir
     *            the test report directory to set
     */
    public void setTestReportDir(final String testReportDir) {
        this.testReportDir = testReportDir;
    }

    /**
     * @return the test result
     */
    public String getTestResult() {
        return testResult;
    }

    /**
     * @param testResult
     *            the test result to set
     */
    public void setTestResult(final String testResult) {
        this.testResult = testResult;
    }

    /**
     * {@link Callable} providing remote access to load configurations via COM.
     */
    protected static final class LoadConfigCallable extends MasterToSlaveCallable<Boolean, IOException> {

        private static final long serialVersionUID = 1L;

        private final TestConfig testConfig;
        private final ExecutionConfig executionConfig;
        private final TaskListener listener;

        /**
         * Instantiates a new {@link LoadConfigCallable}.
         *
         * @param testConfig
         *            the test configuration
         * @param executionConfig
         *            the execution configuration
         * @param listener
         *            the listener
         */
        public LoadConfigCallable(final TestConfig testConfig, final ExecutionConfig executionConfig,
                final TaskListener listener) {
            this.testConfig = testConfig;
            this.executionConfig = executionConfig;
            this.listener = listener;
        }

        @Override
        public Boolean call() throws IOException {
            final String tbcFile = testConfig.getTbcFile();
            final String tcfFile = testConfig.getTcfFile();
            final List<GlobalConstant> constants = testConfig.getConstants();
            final int timeout = executionConfig.getTimeout();
            final TTConsoleLogger logger = new TTConsoleLogger(listener);
            boolean isLoaded = false;

            try (ETComClient comClient = new ETComClient()) {
                final String tbcName = getConfigName(tbcFile);
                final String tcfName = getConfigName(tcfFile);
                logger.logInfo(String.format("- Loading configurations: TBC=%s TCF=%s", tbcName, tcfName));
                if (testConfig.isForceReload()) {
                    logger.logInfo("-> Forcing reload configurations...");
                    comClient.stop();
                }
                if (comClient.openTestConfiguration(StringUtils.defaultIfBlank(tcfFile, null))) {
                    if (tcfFile != null && !constants.isEmpty()) {
                        final Map<String, String> constantMap = getGlobalConstantMap();
                        logger.logInfo("-> With global constants: " + constantMap.toString());
                        setGlobalConstants(comClient, constantMap, tcfFile);
                    }
                    comClient.waitForIdle(timeout);
                    logger.logInfo("-> Test configuration loaded successfully.");
                } else {
                    logger.logError(String.format("-> Loading TCF=%s failed!", tcfName));
                }
                if (comClient.openTestbenchConfiguration(StringUtils.defaultIfBlank(tbcFile, null))) {
                    comClient.waitForIdle(timeout);
                    logger.logInfo("-> Test bench configuration loaded successfully.");
                    isLoaded = true;
                } else {
                    logger.logError(String.format("-> Loading TBC=%s failed!", tbcName));
                }
                if (isLoaded) {
                    if (testConfig.isLoadOnly()) {
                        logger.logInfo("-> Starting configurations will be skipped.");
                    } else {
                        logger.logInfo("- Starting configurations...");
                        comClient.start();
                        comClient.waitForIdle(timeout);
                        logger.logInfo("-> Configurations started successfully.");
                    }
                }
            } catch (final ETComException e) {
                logger.logError("Caught ComException: " + e.getMessage());
                isLoaded = false;
            }
            return isLoaded;
        }

        /**
         * Gets the name of the given configuration file.
         *
         * @param configFile
         *            the configuration file
         * @return the configuration name
         */
        private String getConfigName(final String configFile) {
            String configName;
            if (StringUtils.isBlank(configFile)) {
                configName = "None";
            } else {
                configName = new File(configFile).getName();
            }
            return configName;
        }

        /**
         * Sets the constants that are not already present in the currently loaded test configuration.
         * Identical global constants means both having the same name and the same value.
         * This requires to start the configuration, add the constants and reload the configuration.
         *
         * @param comClient
         *            the COM client
         * @param constantMap
         *            the constant map to set
         * @param tcfFile
         *            the test configuration file
         * @throws ETComException
         *             in case of a COM exception
         */
        private void setGlobalConstants(final ETComClient comClient, final Map<String, String> constantMap,
                final String tcfFile) throws ETComException {
            comClient.start();
            boolean reloadConfig = false;
            final TestConfiguration testConfig = (TestConfiguration) comClient.getCurrentTestConfiguration();
            final List<Constant> currentConstants = getCurrentConstants(testConfig.getGlobalConstants());
            for (final Entry<String, String> constant : constantMap.entrySet()) {
                boolean newConstant = true;
                for (final Constant currentConstant : currentConstants) {
                    if (currentConstant.getName().equals(constant.getKey())
                            && currentConstant.getValue().equals(constant.getValue())) {
                        newConstant = false;
                        break;
                    }
                }
                if (newConstant) {
                    testConfig.setGlobalConstant(constant.getKey(), constant.getValue());
                    reloadConfig = true;
                }
            }
            if (reloadConfig) {
                comClient.openTestConfiguration(tcfFile);
                comClient.stop();
            }
        }

        /**
         * Gets the global constants of the currently loaded test configuration.
         *
         * @param globalConstants
         *            the global constants
         * @return the current constants
         * @throws ETComException
         *             in case of a COM exception
         */
        private List<Constant> getCurrentConstants(final ComConstants globalConstants) throws ETComException {
            final List<Constant> constants = new ArrayList<Constant>();
            for (int i = 0; i < globalConstants.getCount(); i++) {
                final Constant constant = (Constant) globalConstants.item(i);
                constants.add(constant);
            }
            return constants;
        }

        /**
         * Converts the global constant list to a map.
         *
         * @return the global constant map
         * @throws ETComException
         *             in case of a COM exception
         */
        private Map<String, String> getGlobalConstantMap() throws ETComException {
            final Map<String, String> constantMap = new LinkedHashMap<String, String>();
            for (final GlobalConstant constant : testConfig.getConstants()) {
                constantMap.put(constant.getName(), constant.getValue());
            }
            return constantMap;
        }
    }

    /**
     * Helper class storing information about the test result and the test report directory.
     */
    protected static final class TestInfoHolder implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String testResult;
        private final String testReportDir;

        /**
         * Instantiates a new {@link TestInfoHolder}.
         *
         * @param testResult
         *            the test result
         * @param testReportDir
         *            the test report directory
         */
        public TestInfoHolder(final String testResult, final String testReportDir) {
            this.testResult = testResult;
            this.testReportDir = testReportDir;
        }

        /**
         * @return the test result
         */
        public String getTestResult() {
            return testResult;
        }

        /**
         * @return the test report directory
         */
        public String getTestReportDir() {
            return testReportDir;
        }
    }

    /**
     * Helper class storing information about the errors returned by checking packages and projects.
     */
    public static final class CheckInfoHolder {

        /**
         * Defines the seriousness types for checks.
         */
        public enum Seriousness {
            /**
             * Seriousness indicating the check is informational only.
             */
            NOTE,

            /**
             * Seriousness indicating the check represents a warning.
             */
            WARNING,

            /**
             * Seriousness indicating the check represents an error.
             */
            ERROR;
        }

        private final String filePath;
        private final Seriousness seriousness;
        private final String errorMessage;
        private final String lineNumber;

        /**
         * Instantiates a new {@link CheckInfoHolder}.
         *
         * @param filePath
         *            the file path
         * @param seriousness
         *            the seriousness
         * @param errorMessage
         *            the error message
         * @param lineNumber
         *            the line number
         */
        public CheckInfoHolder(final String filePath, final Seriousness seriousness, final String errorMessage,
                final String lineNumber) {
            super();
            this.filePath = filePath;
            this.seriousness = seriousness;
            this.errorMessage = errorMessage;
            this.lineNumber = lineNumber;
        }

        /**
         * @return the file path
         */
        public String getFilePath() {
            return filePath;
        }

        /**
         * @return the seriousness
         */
        public Seriousness getSeriousness() {
            return seriousness;
        }

        /**
         * @return the error message
         */
        public String getErrorMessage() {
            return errorMessage;
        }

        /**
         * @return the line number
         */
        public String getLineNumber() {
            return lineNumber;
        }
    }
}
