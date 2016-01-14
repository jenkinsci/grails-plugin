package com.g2one.hudson.grails;

import hudson.FilePath;
import hudson.model.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jvnet.hudson.test.HudsonTestCase;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Kiyotaka Oku
 */
public class GrailsBuilderIntegrationTest extends HudsonTestCase {

    private static final String TMP_WORK_DIR = "-Dgrails.work.dir=/tmp";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        jenkins.getDescriptorByType(GrailsInstallation.DescriptorImpl.class).setInstallations(
                mockGrails("echo"),
                mockGrails("buildFailed"),
                mockGrails("testsFailed")
        );
    }

    public void testTargets() {
        assertEcho("test-app",
                TMP_WORK_DIR + " test-app");

        assertEcho("\"test-app -clean\"",
                TMP_WORK_DIR + " test-app -clean");

        assertEcho("\"test-app -clean\" war",
                TMP_WORK_DIR + " test-app -clean",
                TMP_WORK_DIR + " war");

        assertEcho("\"test-app -clean\" \"war\"",
                TMP_WORK_DIR + " test-app -clean",
                TMP_WORK_DIR + " war");

        assertEcho("\"test-app -clean\" \"war target/app.war\"",
                TMP_WORK_DIR + " test-app -clean",
                TMP_WORK_DIR + " war target/app.war");
    }

    public void testExpandEnvironmentsInTargets() {

        assertEcho("\"set-version 1.0.${env['BUILD_NUMBER']}\"",
                TMP_WORK_DIR + " set-version 1.0.1");

        assertEcho("${env['defaultTarget']}", env("defaultTarget", "test-app"),
                "-DdefaultTarget=test-app " + TMP_WORK_DIR + " test-app");

        assertEcho("\"set-version 1.0.${BUILD_NUMBER}\"",
                TMP_WORK_DIR + " set-version 1.0.1");

        assertEcho("${defaultTarget}", env("defaultTarget", "test-app"),
                "-DdefaultTarget=test-app " + TMP_WORK_DIR +" test-app");
    }

    public void testExpandEnvironmentsInProperties() {
        {
            List<String> logs = run(newBuilderWithProperties("foo=FOO"));
            assertEcho(logs, TMP_WORK_DIR +  " -Dfoo=FOO test-app");
        }
        {
            List<String> logs = run(newBuilderWithProperties("foo=FOO\nbar=BAR"));
            assertEcho(logs, TMP_WORK_DIR +  " -Dbar=BAR -Dfoo=FOO test-app");
        }
        {
            List<String> logs = run(newBuilderWithProperties("foo=${env['BUILD_NUMBER']}"));
            assertEcho(logs, TMP_WORK_DIR +  " -Dfoo=1 test-app");
        }
        {
            List<String> logs = run(newBuilderWithProperties("foo=FOO\nbar=${env['BUILD_NUMBER']}"));
            assertEcho(logs, TMP_WORK_DIR +  " -Dbar=1 -Dfoo=FOO test-app");
        }
        {
            List<String> logs = run(newBuilderWithProperties("foo=${BUILD_NUMBER}"));
            assertEcho(logs, TMP_WORK_DIR +  " -Dfoo=1 test-app");
        }
        {
            List<String> logs = run(newBuilderWithProperties("foo=FOO\nbar=${BUILD_NUMBER}"));
            assertEcho(logs, TMP_WORK_DIR +  " -Dbar=1 -Dfoo=FOO test-app");
        }
    }

    public void testForceUpgrade() {
        {
            GrailsBuilder builder = newBuilderWithTargets("test-app");
            builder.setForceUpgrade(true);
            assertEcho(run(builder),
                    TMP_WORK_DIR + " upgrade --non-interactive",
                    TMP_WORK_DIR + " test-app");
        }
        {
            GrailsBuilder builder = newBuilderWithTargets("test-app war");
            builder.setForceUpgrade(true);
            assertEcho(run(builder),
                    TMP_WORK_DIR + " upgrade --non-interactive",
                    TMP_WORK_DIR + " test-app",
                    TMP_WORK_DIR + " war");
        }
    }

    public void testNonInteractive() {
        {
            GrailsBuilder builder = newBuilderWithTargets("test-app");
            builder.setNonInteractive(true);
            assertEcho(run(builder),
                    TMP_WORK_DIR + " test-app --non-interactive");
        }
        {
            GrailsBuilder builder = newBuilderWithTargets("test-app war");
            builder.setNonInteractive(true);
            assertEcho(run(builder),
                    TMP_WORK_DIR + " test-app --non-interactive",
                    TMP_WORK_DIR + " war --non-interactive");
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

        assertTrue(logs(job).contains("[MOCK_GRAILSW] " + TMP_WORK_DIR + " test-app"));
    }

    public void testPlanOutput() {
        GrailsBuilder builder = newBuilderWithTargets("test-app");
        builder.setPlainOutput(true);
        assertEcho(run(builder),
                TMP_WORK_DIR + " test-app --plain-output");
    }

    public void testVerbose() {
        GrailsBuilder builder = newBuilderWithTargets("test-app");
        builder.setVerbose(true);
        assertEcho(run(builder),
                TMP_WORK_DIR + " test-app --verbose");
    }

    public void testRefreshDependencies() {
        GrailsBuilder builder = newBuilderWithTargets("test-app");
        builder.setRefreshDependencies(true);
        assertEcho(run(builder),
                TMP_WORK_DIR + " test-app --refresh-dependencies");
    }

    public void testWithGrailsWorkDir() throws Exception {
        GrailsBuilder builder = new GrailsBuilder("test-app", "echo", "/tmp", null, null, null, null, false, false, true, false, false, false, false, false);
        FreeStyleProject job = job = createFreeStyleProject();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = job.scheduleBuild2(0).get();

        assertEcho(logs(build), "-Dgrails.work.dir=/tmp" + " test-app");
    }

    public void testWithoutGrailsWorkDir() throws Exception {
        GrailsBuilder builder = new GrailsBuilder("test-app", "echo", null, null, null, null, null, false, false, true, false, false, false, false, false);
        FreeStyleProject job = job = createFreeStyleProject();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = job.scheduleBuild2(0).get();
        String defaultWorkDir = build.getWorkspace().toURI().getPath() + "/target";

        assertEcho(logs(build), "-Dgrails.work.dir=" + defaultWorkDir + " test-app");
    }

    public void testBuildFailed() throws Exception {
        GrailsBuilder builder = new GrailsBuilder("test-app", "buildFailed", null, null, null, null, null, false, false, true, false, false, false, false, false);
        FreeStyleProject job = job = createFreeStyleProject();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = job.scheduleBuild2(0).get();

        assertEquals(Result.FAILURE, build.getResult());
    }

    public void testTestsFailed() throws Exception {
        GrailsBuilder builder = new GrailsBuilder("test-app", "testsFailed", null, null, null, null, null, false, false, true, false, false, false, false, false);
        FreeStyleProject job = job = createFreeStyleProject();
        job.getBuildersList().add(builder);
        FreeStyleBuild build = job.scheduleBuild2(0).get();

        assertEquals(Result.UNSTABLE, build.getResult());
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
            return logs(job.scheduleBuild2(0).get());
        } catch(Exception e) {
            throw new AssertionError(e);
        }
    }

    private List<String> logs(FreeStyleBuild build) {
        try {
            assertEquals(Result.SUCCESS, build.getResult());
            return FileUtils.readLines(build.getLogFile());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private void assertEcho(String targets, String... expected) {
        assertEcho(targets, null ,expected);
    }

    private void assertEcho(String targets, Map<String, String> env, String... expected) {
        List<String> logs = run(newBuilderWithTargets(targets), env);
        for (String s : expected) {
            assertTrue(String.format("[%s] is not exists in %s", s, StringUtils.join(logs, "\n")), logs.contains("[MOCK_GRAILS] " + s));
        }
    }

    private void assertEcho(List<String> logs, String... expected) {
        for (String s : expected) {
            assertTrue(String.format("[%s] is not exists in %s", s, StringUtils.join(logs, "\n")), logs.contains("[MOCK_GRAILS] " + s));
        }
    }

    private GrailsBuilder newBuilderWithTargets(String targets) {
        return new GrailsBuilder(targets, "echo", "/tmp", null, null, null, null, false, false, true, false, false, false, false, false);
    }

    private GrailsBuilder newBuilderWithProperties(String properties) {
        return new GrailsBuilder("test-app", "echo", "/tmp", null, null, null, properties, false, false, true, false, false, false, false, false);
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
