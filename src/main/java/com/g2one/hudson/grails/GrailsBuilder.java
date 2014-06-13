package com.g2one.hudson.grails;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.UnflaggedOption;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import hudson.*;
import hudson.model.*;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.VariableResolver;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GrailsBuilder extends Builder {

    private static final String JAVA_OPTS = "JAVA_OPTS";
    private static final String JENKINS_7702_TRIGGER = "-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager";

    private final String targets;
    private final String name;
    private String grailsWorkDir;
    private String projectWorkDir;
    private String projectBaseDir;
    private String serverPort;
    private String properties;
    private Boolean forceUpgrade;
    private Boolean nonInteractive;
    private Boolean useWrapper;
    private Boolean plainOutput;
    private Boolean stackTrace;
    private Boolean verbose;
    private Boolean refreshDependencies;
    private Boolean makeExecutable;

    @DataBoundConstructor
    public GrailsBuilder(String targets, String name, String grailsWorkDir, String projectWorkDir, String projectBaseDir, String serverPort, String properties, Boolean forceUpgrade, Boolean nonInteractive, Boolean useWrapper, Boolean plainOutput, Boolean stackTrace, Boolean verbose, Boolean refreshDependencies, Boolean makeExecutable) {
          this.name = name;
        this.targets = targets;
        this.grailsWorkDir = grailsWorkDir;
        this.projectWorkDir = projectWorkDir;
        this.projectBaseDir = projectBaseDir;
        this.serverPort = serverPort;
        this.properties = properties;
        this.forceUpgrade = forceUpgrade;
        this.nonInteractive = nonInteractive;
        this.useWrapper = useWrapper;
        this.plainOutput = plainOutput;
        this.stackTrace = stackTrace;
        this.verbose = verbose;
        this.refreshDependencies = refreshDependencies;
        this.makeExecutable = makeExecutable;
    }

    public boolean getNonInteractive() {
        return nonInteractive;
    }

    public void setNonInteractive(Boolean b) {
        nonInteractive = b;
    }
    
    public boolean getForceUpgrade() {
        return forceUpgrade;
    }
    
    public void setForceUpgrade(Boolean b) {
        forceUpgrade = b;
    }
    
    public String getProperties() {
        return properties;
    }
    
    public void setProperties(String properties) {
        this.properties = properties;
    }

    public String getProjectBaseDir() {
        return projectBaseDir;
    }

    public void setProjectBaseDir(String projectBaseDir) {
        this.projectBaseDir = projectBaseDir;
    }

    public String getProjectWorkDir() {
        return projectWorkDir;
    }

    public void setProjectWorkDir(String projectWorkDir) {
        this.projectWorkDir = projectWorkDir;
    }

    public String getGrailsWorkDir() {
        return grailsWorkDir;
    }

    public void setGrailsWorkDir(String grailsWorkDir) {
        this.grailsWorkDir = grailsWorkDir;
    }

    public String getServerPort() {
        return serverPort;
    }

    public void setServerPort(String serverPort) {
        this.serverPort = serverPort;
    }

    public String getName() {
        return name;
    }

    public String getTargets() {
        return targets;
    }

    public void setUseWrapper(Boolean useWrapper) {
        this.useWrapper = useWrapper;
    }

    public Boolean getUseWrapper() {
        return useWrapper;
    }

    public Boolean getPlainOutput() {
        return plainOutput;
    }

    public void setPlainOutput(Boolean plainOutput) {
        this.plainOutput = plainOutput;
    }

    public Boolean getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(Boolean stackTrace) {
        this.stackTrace = stackTrace;
    }

    public Boolean getVerbose() {
        return verbose;
    }

    public void setVerbose(Boolean verbose) {
        this.verbose = verbose;
    }

    public Boolean getRefreshDependencies() {
        return refreshDependencies;
    }

    public void setRefreshDependencies(Boolean refreshDependencies) {
        this.refreshDependencies = refreshDependencies;
    }

    public Boolean getMakeExecutable(){
        return this.makeExecutable;
    }

    public void setMakeExecutable(Boolean makeExecutable){
        this.makeExecutable = makeExecutable;
    }

    public GrailsInstallation getGrails() {
        GrailsInstallation[] installations = Hudson.getInstance()
            .getDescriptorByType(GrailsInstallation.DescriptorImpl.class)
            .getInstallations();
        for (GrailsInstallation i : installations) {
            if (name != null && i.getName().equals(name))
                return i;
        }
        return null;
    }

    private Object readResolve() {
        // Default to false when loading old data to preserve previous behavior.
        if (nonInteractive == null) nonInteractive = Boolean.FALSE;
        if (useWrapper == null) useWrapper = Boolean.FALSE;
        return this;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        readResolve();
        EnvVars env = build.getEnvironment(listener);
        List<String[]> targetsToRun = getTargetsToRun(env);
     
        if (targetsToRun.size() > 0) {
            String execName;
            if (useWrapper) {
                FilePath wrapper = new FilePath(getBasePath(build), launcher.isUnix() ? "grailsw" : "grailsw.bat");
                execName = wrapper.getRemote();

                if (makeExecutable) {
                    wrapper.chmod(0744);
                }

            } else {
                execName = launcher.isUnix() ? "grails" : "grails.bat";
            }


            GrailsInstallation grailsInstallation = useWrapper ? null : getGrails();

            if (grailsInstallation != null) {
                grailsInstallation = grailsInstallation.forEnvironment(env)
                    .forNode(Computer.currentComputer().getNode(), listener);
                env.put("GRAILS_HOME", grailsInstallation.getHome());

                String path = env.get("PATH");
                path = path == null ? "" : path + (launcher.isUnix() ? ":" : ";");
                env.put("PATH", path + grailsInstallation.getHome() + (launcher.isUnix() ? "/bin" : "\\bin"));
            }

            String jopts = env.get(JAVA_OPTS);
            if (jopts != null && jopts.contains(JENKINS_7702_TRIGGER)) {
                listener.getLogger().println("[JENKINS-7702] sanitizing $" + JAVA_OPTS);
                env.put(JAVA_OPTS, jopts.replace(JENKINS_7702_TRIGGER, "")); // leading/trailing spaces should be harmless
            }

            for (String[] targetsAndArgs : targetsToRun) {

                String target = targetsAndArgs[0];
                ArgumentListBuilder args = new ArgumentListBuilder();

                if (grailsInstallation == null) {
                    args.add(execName);
                } else {
                    FilePath exec = new FilePath(launcher.getChannel(), grailsInstallation.getHome()).child("bin").child(execName);
                    if (!exec.exists()) {
                        listener.fatalError(exec + " doesn't exist");
                        return false;
                    }
                    args.add(exec.getRemote());
                }
                args.addKeyValuePairs("-D", build.getBuildVariables());
                Map systemProperties = new HashMap();
                if (grailsWorkDir != null && !"".equals(grailsWorkDir.trim())) {
                    systemProperties.put("grails.work.dir", eval(env, grailsWorkDir));
                } else {
                    systemProperties.put("grails.work.dir", build.getWorkspace().toURI().getPath() + "/target");
                }
                if (projectWorkDir != null && !"".equals(projectWorkDir.trim())) {
                    systemProperties.put("grails.project.work.dir", eval(env, projectWorkDir));
                }
                if (serverPort != null && !"".equals(serverPort.trim())) {
                    systemProperties.put("server.port", eval(env, serverPort));
                }
                if (systemProperties.size() > 0) {
                    args.addKeyValuePairs("-D", systemProperties);
                }
                args.addKeyValuePairsFromPropertyString("-D", eval(env, properties), build.getBuildVariableResolver());

                args.add(target);
                addArgument("--non-interactive", nonInteractive, args, env, targetsAndArgs);
                addArgument("--plain-output", plainOutput, args, env, targetsAndArgs);
                addArgument("--stacktrace", stackTrace, args, env, targetsAndArgs);
                addArgument("--verbose", verbose, args, env, targetsAndArgs);
                addArgument("--refresh-dependencies", refreshDependencies, args, env, targetsAndArgs);

                if (!launcher.isUnix()) {
                    args = args.toWindowsCommand();
                }

                GrailsConsoleAnnotator gca = new GrailsConsoleAnnotator(listener.getLogger(), build.getCharset());
                new GrailsTaskNote(target).encodeTo(listener.getLogger());
                try {
                    int r = launcher.launch().cmds(args).envs(env).stdout(gca).pwd(getBasePath(build)).join();
                    if (r != 0) {
                        if (gca.isBuildFailingDueToFailingTests()) {
                            build.setResult(Result.UNSTABLE);
                        } else {
                            return false;
                        }
                    }
                } catch (IOException e) {
                    Util.displayIOException(e, listener);
                    e.printStackTrace(listener.fatalError("command execution failed"));
                    return false;
                } finally {
                    gca.forceEol();
                }
            }
        } else {
            listener.getLogger().println("Error: No Targets To Run!");
            return false;
        }
        return true;
    }

    protected void addArgument(String option, Boolean optionEnabled, ArgumentListBuilder args, EnvVars env, String[] targetsAndArgs) {
        boolean foundArgument = false;
        for (int i = 1; i < targetsAndArgs.length; i++) {
            String arg = eval(env, targetsAndArgs[i]);
            if(option.equals(arg)) {
                foundArgument = true;
            }
            if (!args.toList().contains(arg)) {
                args.add(arg);
            }
        }
        if(optionEnabled != null && optionEnabled && !foundArgument) {
            args.add(option);
        }
    }

    private FilePath getBasePath(AbstractBuild<?, ?> build) {
        FilePath basePath;
        FilePath moduleRoot = build.getModuleRoot();
        if (projectBaseDir != null && !"".equals(projectBaseDir.trim())) {
            basePath = new FilePath(moduleRoot, projectBaseDir);
        } else {
            basePath = moduleRoot;
        }
        return basePath;
    }

    /**
     * Method based on work from Kenji Nakamura
     *
     * @param env    The enviroment vars map
     * @param target The target with environment vars
     * @return The target with evaluated environment vars
     */
    @SuppressWarnings({"StaticMethodOnlyUsedInOneClass", "TypeMayBeWeakened"})
    static String eval(Map<String, String> env, String target) {
        List<String> result = new ArrayList<String>();
        if (target == null) {
            return null;
        } else {
            target = target.trim();
        }
        for (String s : target.split("\r?\n")) {
            s = Util.replaceMacro(s, new VariableResolver.ByMap<String>(env));
            Binding binding = new Binding();
            binding.setVariable("env", env);
            binding.setVariable("sys", System.getProperties());
            GroovyShell shell = new GroovyShell(binding);
            Object value = shell.evaluate("return \"" + s + "\"");
            if (value == null) {
                result.add(s);
            } else {
                result.add(value.toString().trim());
            }
        }
        return StringUtils.join(result, "\n");
    }

    protected List<String[]> getTargetsToRun(EnvVars env) {
        List<String[]> targetsToRun = new ArrayList<String[]>();
        if(forceUpgrade) {
            targetsToRun.add(new String[]{"upgrade", "--non-interactive"});
        }
        if (targets != null && targets.length() > 0) {
            try {
                String targetsEval = this.targets;
                JSAP jsap = new JSAP();
                UnflaggedOption option = new UnflaggedOption("targets");
                option.setGreedy(true);
                jsap.registerParameter(option);
                JSAPResult jsapResult = jsap.parse(targetsEval);
                String[] targets = jsapResult.getStringArray("targets");
                for (String targetAndArgs : targets) {
                    String[] pieces = eval(env, targetAndArgs).split(" ");
                    targetsToRun.add(pieces);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return targetsToRun;
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends Descriptor<Builder> {

        public String getDisplayName() {
            return "Build With Grails";
        }

        @Override
        public synchronized void load() {
            // NOP
        }

        @Override
        public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(clazz, formData);
        }

        public GrailsInstallation[] getInstallations() {
            return Hudson.getInstance().getDescriptorByType(GrailsInstallation.DescriptorImpl.class).getInstallations();
        }
    }
}
