package com.g2one.hudson.grails;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kiyotaka Oku
 */
public class GrailsBuilderIntegrationTest extends HudsonTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        jenkins.getDescriptorByType(GrailsInstallation.DescriptorImpl.class).setInstallations(
                mockGrails("echo")
        );
    }

    public void testTargets() {
        assertEcho("test-app",
                "test-app");

        assertEcho("\"test-app -clean\"",
                "test-app -clean");

        assertEcho("\"test-app -clean\" war",
                "test-app -clean",
                "war");

        assertEcho("\"test-app -clean\" \"war\"",
                "test-app -clean",
                "war");

        assertEcho("\"test-app -clean\" \"war target/app.war\"",
                "test-app -clean",
                "war target/app.war");
    }

    public void testExpandEnvironmentsInTargets() {
        assertEcho("\"set-version 1.0.${env['BUILD_NUMBER']}\"",
                "set-version 1.0.1");
        assertEcho("${env['defaultTarget']}", env("defaultTarget", "test-app"),
                "-DdefaultTarget=test-app test-app");

        assertEcho("\"set-version 1.0.${BUILD_NUMBER}\"",
                "set-version 1.0.1");
        assertEcho("${defaultTarget}", env("defaultTarget", "test-app"),
                "-DdefaultTarget=test-app test-app");
    }

    public void testForceUpgrade() {
        {
            GrailsBuilder builder = newBuilderWithTargets("test-app");
            builder.setForceUpgrade(true);
            assertEcho(run(builder), "upgrade --non-interactive", "test-app");
        }
        {
            GrailsBuilder builder = newBuilderWithTargets("test-app war");
            builder.setForceUpgrade(true);
            assertEcho(run(builder), "upgrade --non-interactive", "test-app", "war");
        }
    }

    public void testNonInteractive() {
        {
            GrailsBuilder builder = newBuilderWithTargets("test-app");
            builder.setNonInteractive(true);
            assertEcho(run(builder), "test-app --non-interactive");
        }
        {
            GrailsBuilder builder = newBuilderWithTargets("test-app war");
            builder.setNonInteractive(true);
            assertEcho(run(builder), "test-app --non-interactive", "war --non-interactive");
        }
    }

    public void testUseWrapper() throws IOException, URISyntaxException, InterruptedException {

        FreeStyleProject job = createFreeStyleProject();
        File customWorkspace = createTmpDir();
        FilePath mockPath = new FilePath(new File("src/test/resources/mock/wrapper"));
        for (FilePath wrapper : mockPath.list()) {
            wrapper.copyToWithPermission(new FilePath(customWorkspace).child(wrapper.getName()));
        }
        job.setCustomWorkspace(customWorkspace.getAbsolutePath());

        GrailsBuilder builder = newBuilderWithTargets("test-app");
        builder.setUseWrapper(true);
        job.getBuildersList().add(builder);

        assertTrue(logs(job).contains("[MOCK_GRAILSW] test-app"));
    }

    public void testPlanOutput() {
        GrailsBuilder builder = newBuilderWithTargets("test-app");
        builder.setPlainOutput(true);
        assertEcho(run(builder), "test-app --plain-output");
    }

    public void testVerbose() {
        GrailsBuilder builder = newBuilderWithTargets("test-app");
        builder.setVerbose(true);
        assertEcho(run(builder), "test-app --verbose");
    }

    public void testRefreshDependencies() {
        GrailsBuilder builder = newBuilderWithTargets("test-app");
        builder.setRefreshDependencies(true);
        assertEcho(run(builder), "test-app --refresh-dependencies");
    }

    private List<String> run(GrailsBuilder builder) {
        return run(builder, null);
    }

    private List<String> run(GrailsBuilder builder, Map<String, String> env) {
        try {
            FreeStyleProject job = job = createFreeStyleProject();
            job.getBuildersList().add(builder);
            if (env != null) {
                List<ParameterDefinition> params = new ArrayList<ParameterDefinition>();
                for (Map.Entry<String, String> entry : env.entrySet()) {
                    params.add(new StringParameterDefinition(entry.getKey(), entry.getValue()));
                }
                job.addProperty(new ParametersDefinitionProperty(params));
            }
            return logs(job);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private List<String> logs(FreeStyleProject job) {
        try {
            FreeStyleBuild build = job.scheduleBuild2(0).get();
            return FileUtils.readLines(build.getLogFile());
        } catch(Exception e) {
            throw new AssertionError(e);
        }
    }

    private void assertEcho(String targets, String... expected) {
        assertEcho(targets, null ,expected);
    }

    private void assertEcho(String targets, Map<String, String> env, String... expected) {
        List<String> logs = run(newBuilderWithTargets(targets), env);
        for (String s : expected) {
            assertTrue(String.format("[%s] is not exists in %s", s, logs), logs.contains("[MOCK_GRAILS] " + s));
        }
    }

    private void assertEcho(List<String> logs, String... expected) {
        for (String s : expected) {
            assertTrue(String.format("[%s] is not exists in %s", s, logs), logs.contains("[MOCK_GRAILS] " + s));
        }
    }

    private GrailsBuilder newBuilderWithTargets(String targets) {
        return new GrailsBuilder(targets, "echo", null, null, null, null, null, false, false, true, false, false, false, false);
    }

    private Map<String, String> env(String key, String value) {
        Map<String, String> result = new HashMap<String, String>();
        result.put(key, value);
        return result;
    }

    private GrailsInstallation mockGrails(String name) {
        return new GrailsInstallation(name, new File("src/test/resources/mock", name).getAbsolutePath(), null);
    }
}
