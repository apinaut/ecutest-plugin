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
package de.tracetronic.jenkins.plugins.ecutest.report.atx;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeFalse;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.recipes.LocalData;

import com.gargoylesoftware.htmlunit.WebAssert;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import de.tracetronic.jenkins.plugins.ecutest.SystemTestBase;
import de.tracetronic.jenkins.plugins.ecutest.report.atx.installation.ATXConfig;
import de.tracetronic.jenkins.plugins.ecutest.report.atx.installation.ATXInstallation;
import de.tracetronic.jenkins.plugins.ecutest.tool.installation.ETInstallation;

/**
 * System tests for {@link ATXPublisher}.
 *
 * @author Christian Pönisch <christian.poenisch@tracetronic.de>
 */
public class ATXPublisherST extends SystemTestBase {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        final ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                .getDescriptorByType(ETInstallation.DescriptorImpl.class);
        etDescriptor.setInstallations(new ETInstallation("ECU-TEST", "C:\\ECU-TEST", JenkinsRule.NO_PROPERTIES));
        final ATXPublisher.DescriptorImpl atxImpl = jenkins.jenkins
                .getDescriptorByType(ATXPublisher.DescriptorImpl.class);
        final ATXInstallation inst = new ATXInstallation("TEST-GUIDE", "ECU-TEST", new ATXConfig());
        atxImpl.setInstallations(inst);
    }

    @Test
    public void testDefaultConfigRoundTripStep() throws Exception {
        final ATXPublisher before = new ATXPublisher("TEST-GUIDE");
        final ATXPublisher after = jenkins.configRoundtrip(before);
        jenkins.assertEqualDataBoundBeans(before, after);
    }

    @Test
    public void testConfigRoundTripStep() throws Exception {
        final ATXPublisher before = new ATXPublisher("TEST-GUIDE");
        before.setRunOnFailed(false);
        before.setAllowMissing(false);
        before.setRunOnFailed(false);
        before.setArchiving(true);
        before.setKeepAll(true);
        final ATXPublisher after = jenkins.configRoundtrip(before);
        jenkins.assertEqualBeans(before, after,
                "allowMissing,runOnFailed,archiving,keepAll");
    }

    @Deprecated
    @Test
    public void testConfigRoundTrip() throws Exception {
        final ATXPublisher before = new ATXPublisher("TEST-GUIDE", false, false, true, true);
        final ATXPublisher after = jenkins.configRoundtrip(before);
        jenkins.assertEqualBeans(before, after, "allowMissing,runOnFailed,archiving,keepAll");
    }

    @Test
    public void testConfigView() throws Exception {
        final FreeStyleProject project = jenkins.createFreeStyleProject();
        final ATXPublisher publisher = new ATXPublisher("TEST-GUIDE");
        publisher.setAllowMissing(true);
        publisher.setRunOnFailed(true);
        publisher.setArchiving(false);
        publisher.setKeepAll(false);
        project.getPublishersList().add(publisher);

        final HtmlPage page = getWebClient().getPage(project, "configure");
        WebAssert.assertTextPresent(page, Messages.ATXPublisher_DisplayName());
        jenkins.assertXPath(page, "//select[@name='atxName']");
        jenkins.assertXPath(page, "//option[@value='TEST-GUIDE']");
        jenkins.assertXPath(page, "//input[@name='_.allowMissing' and @checked='true']");
        jenkins.assertXPath(page, "//input[@name='_.runOnFailed' and @checked='true']");
        jenkins.assertXPath(page, "//input[@name='_.archiving']");
        jenkins.assertXPath(page, "//input[@name='_.keepAll']");
    }

    @Test
    public void testGlobalConfigPresence() throws Exception {
        final HtmlPage page = getWebClient().goTo("configure");
        jenkins.assertXPath(page, "//tr[@name='de-tracetronic-jenkins-plugins-ecutest-report-atx-ATXPublisher']");
    }

    @Test
    @LocalData
    public void testDefaultConfig() {
        final ATXPublisher.DescriptorImpl atxImpl = jenkins.jenkins
                .getDescriptorByType(ATXPublisher.DescriptorImpl.class);
        assertNotNull(atxImpl.getDefaultConfig());
    }

    @Test
    @LocalData
    public void testCurrentInstallation() {
        final ATXPublisher publisher = new ATXPublisher("TEST-GUIDE");
        assertNotNull(publisher.getInstallation());
    }

    @Test
    public void testFormRoundTrip() throws Exception {
        final ATXPublisher.DescriptorImpl atxImpl = jenkins.jenkins
                .getDescriptorByType(ATXPublisher.DescriptorImpl.class);
        assertEquals(1, atxImpl.getInstallations().length);

        final ATXPublisher publisher = new ATXPublisher("TEST-GUIDE");
        final ATXInstallation installation = publisher.getInstallation();
        assertNotNull(installation);
        assertEquals(installation.getName(), "TEST-GUIDE");
        assertEquals(installation.getToolName(), "ECU-TEST");
    }

    @Test
    public void testAllowMissing() throws Exception {
        // Windows only
        final DumbSlave slave = jenkins.createOnlineSlave();
        final SlaveComputer computer = slave.getComputer();
        assumeFalse("Test is Windows only!", computer.isUnix());

        final FreeStyleProject project = jenkins.createFreeStyleProject();
        project.setAssignedNode(slave);
        final ATXPublisher publisher = new ATXPublisher("TEST-GUIDE");
        publisher.setAllowMissing(false);
        project.getPublishersList().add(publisher);
        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, build);
    }

    @Test
    public void testRunOnFailed() throws Exception {
        // Windows only
        final DumbSlave slave = jenkins.createOnlineSlave();
        final SlaveComputer computer = slave.getComputer();
        assumeFalse("Test is Windows only!", computer.isUnix());

        final FreeStyleProject project = jenkins.createFreeStyleProject();
        project.setAssignedNode(slave);
        project.getBuildersList().add(new TestBuilder() {

            @Override
            public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher,
                    final BuildListener listener) throws InterruptedException, IOException {
                return false;
            }
        });
        final ATXPublisher publisher = new ATXPublisher("TEST-GUIDE");
        publisher.setRunOnFailed(false);
        project.getPublishersList().add(publisher);
        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, build);
        assertThat("Skip message should be present in console log", build.getLog(100).toString(),
                containsString("Skipping publisher"));
    }

    @Test
    public void testParameterizedATXName() throws Exception {
        final ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                .getDescriptorByType(ETInstallation.DescriptorImpl.class);
        etDescriptor.setInstallations(new ETInstallation("ECU-TEST", "C:\\ECU-TEST", JenkinsRule.NO_PROPERTIES));

        final FreeStyleProject project = jenkins.createFreeStyleProject();
        final ATXPublisher publisher = new ATXPublisher("${TESTGUIDE}");
        project.getPublishersList().add(publisher);

        final EnvVars envVars = new EnvVars(
                Collections.unmodifiableMap(new HashMap<String, String>() {

                    private static final long serialVersionUID = 1L;
                    {
                        put("TESTGUIDE", "TEST-GUIDE");
                    }
                }));

        assertEquals("ATX name should be resolved", "TEST-GUIDE", publisher.getInstallation(envVars).getName());
    }

    @Test
    public void testParameterizedToolName() throws Exception {
        final ETInstallation.DescriptorImpl etDescriptor = jenkins.jenkins
                .getDescriptorByType(ETInstallation.DescriptorImpl.class);
        etDescriptor.setInstallations(new ETInstallation("ECU-TEST", "C:\\ECU-TEST", JenkinsRule.NO_PROPERTIES));

        final FreeStyleProject project = jenkins.createFreeStyleProject();
        final ATXPublisher publisher = new ATXPublisher("TEST-GUIDE");
        project.getPublishersList().add(publisher);

        final EnvVars envVars = new EnvVars(
                Collections.unmodifiableMap(new HashMap<String, String>() {

                    private static final long serialVersionUID = 1L;
                    {
                        put("ECUTEST", "ECU-TEST");
                    }
                }));

        final ATXInstallation installation = publisher.getInstallation();
        assertNotNull(installation);
        assertEquals("Tool name should be resolved", "ECU-TEST",
                publisher.getToolInstallation(installation.getToolName(), envVars).getName());
    }

    @Test
    public void testDefaultPipelineStep() throws Exception {
        final String script = ""
                + "node('slaves') {\n"
                + "  step([$class: 'ATXPublisher', atxName: 'TEST-GUIDE'])\n"
                + "}";
        assertPipelineStep(script);
    }

    @Test
    public void testPipelineStep() throws Exception {
        final String script = ""
                + "node('slaves') {\n"
                + "  step([$class: 'ATXPublisher', atxName: 'TEST-GUIDE',"
                + "        allowMissing: true, runOnFailed: true,"
                + "        archiving: false, keepAll: false])\n"
                + "}";
        assertPipelineStep(script);
    }

    /**
     * Asserts the pipeline step execution.
     *
     * @param script
     *            the script
     * @throws Exception
     *             the exception
     */
    private void assertPipelineStep(final String script) throws Exception {
        // Windows only
        final DumbSlave slave = jenkins.createOnlineSlave(Label.get("slaves"));
        final SlaveComputer computer = slave.getComputer();
        assumeFalse("Test is Windows only!", computer.isUnix());

        final WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "pipeline");
        job.setDefinition(new CpsFlowDefinition(script, true));

        final WorkflowRun run = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get());
        jenkins.assertLogContains("Publishing ATX reports...", run);
        jenkins.assertLogContains("Starting ECU-TEST failed.", run);
    }
}
