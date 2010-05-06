package com.g2one.hudson.grails;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.UnflaggedOption;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GrailsBuilder extends Builder {

    private final String targets;
    private final String name;
    private String grailsWorkDir;
    private String projectWorkDir;
    private String projectBaseDir;
    private String serverPort;
    private String properties;

    @DataBoundConstructor
    public GrailsBuilder(String targets, String name, String grailsWorkDir, String projectWorkDir, String projectBaseDir, String serverPort, String properties) {
        this.name = name;
        this.targets = targets;
        this.grailsWorkDir = grailsWorkDir;
        this.projectWorkDir = projectWorkDir;
        this.projectBaseDir = projectBaseDir;
        this.serverPort = serverPort;
        this.properties = properties;
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

    public GrailsInstallation getGrails() {
        for (GrailsInstallation i : DESCRIPTOR.getInstallations()) {
            if (name != null && i.getName().equals(name))
                return i;
        }
        return null;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        List<String[]> targetsToRun = getTargetsToRun();
        if (targetsToRun.size() > 0) {
            String execName;
            if (launcher.isUnix()) {
                execName = "grails";
            } else {
                execName = "grails.bat";
            }

            GrailsInstallation grailsInstallation = getGrails();

            Map<String, String> env = build.getEnvironment(listener);
            if (grailsInstallation != null) {
                env.put("GRAILS_HOME", grailsInstallation.getGrailsHome());
            }

            for (String[] targetsAndArgs : targetsToRun) {

                String target = targetsAndArgs[0];
                ArgumentListBuilder args = new ArgumentListBuilder();

                if (grailsInstallation == null) {
                    args.add(execName);
                } else {
                    File exec = grailsInstallation.getExecutable();
                    if (!grailsInstallation.getExists()) {
                        listener.fatalError(exec + " doesn't exist");
                        return false;
                    }
                    args.add(exec.getPath());
                }
                args.addKeyValuePairs("-D", build.getBuildVariables());
                Map sytemProperties = new HashMap();
                if (grailsWorkDir != null && !"".equals(grailsWorkDir.trim())) {
                    sytemProperties.put("grails.work.dir", grailsWorkDir.trim());
                }
                if (projectWorkDir != null && !"".equals(projectWorkDir.trim())) {
                    sytemProperties.put("grails.project.work.dir", projectWorkDir.trim());
                }
                if (serverPort != null && !"".equals(serverPort.trim())) {
                    sytemProperties.put("server.port", serverPort.trim());
                }
                if (sytemProperties.size() > 0) {
                    args.addKeyValuePairs("-D", sytemProperties);
                }
                args.addKeyValuePairsFromPropertyString("-D", properties, build.getBuildVariableResolver());

                args.add(target);
                for (int i = 1; i < targetsAndArgs.length; i++) {
                    args.add(evalTarget(env, targetsAndArgs[i]));
                }

                if (!launcher.isUnix()) {
                    args.prepend("cmd.exe", "/C");
                    args.add("&&", "exit", "%%ERRORLEVEL%%");
                }

                try {
                    final FilePath basePath;
                    FilePath moduleRoot = build.getModuleRoot();
                    if (projectBaseDir != null && !"".equals(projectBaseDir.trim())) {
                        basePath = new FilePath(moduleRoot, projectBaseDir);
                    } else {
                        basePath = moduleRoot;
                    }
                    int r = launcher.launch().cmds(args).envs(env).stdout(listener).pwd(basePath).join();
                    if (r != 0) return false;
                } catch (IOException e) {
                    Util.displayIOException(e, listener);
                    e.printStackTrace(listener.fatalError("command execution failed"));
                    return false;
                }
            }
        } else {
            listener.getLogger().println("Error: No Targets To Run!");
            return false;
        }
        return true;
    }

    /**
     * Method based on work from Kenji Nakamura
     *
     * @param env    The enviroment vars map
     * @param target The target with environment vars
     * @return The target with evaluated environment vars
     */
    @SuppressWarnings({"StaticMethodOnlyUsedInOneClass", "TypeMayBeWeakened"})
    static String evalTarget(Map<String, String> env, String target) {
        Binding binding = new Binding();
        binding.setVariable("env", env);
        binding.setVariable("sys", System.getProperties());
        GroovyShell shell = new GroovyShell(binding);
        Object result = shell.evaluate("return \"" + target + "\"");
        if (result == null) {
            return target;
        } else {
            return result.toString().trim();
        }
    }

    protected List<String[]> getTargetsToRun() {
        List<String[]> targetsToRun = new ArrayList<String[]>();
        if (targets != null && targets.length() > 0) {
            try {
                JSAP jsap = new JSAP();
                UnflaggedOption option = new UnflaggedOption("targets");
                option.setGreedy(true);
                jsap.registerParameter(option);
                JSAPResult jsapResult = jsap.parse(this.targets);
                String[] targets = jsapResult.getStringArray("targets");
                for (String targetAndArgs : targets) {
                    String[] pieces = targetAndArgs.split(" ");
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

        private volatile GrailsInstallation[] installations = new GrailsInstallation[0];

        DescriptorImpl() {
            super(GrailsBuilder.class);
            load();
        }

        public String getDisplayName() {
            return "Build With Grails";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            installations = req.bindParametersToList(GrailsInstallation.class, "grails.").toArray(new GrailsInstallation[0]);
            save();
            return true;
        }

//        public Builder newInstance(StaplerRequest req) {
//            return req.bindParameters(GrailsBuilder.class, "grails.");
//        }

        @Override
        public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return req.bindJSON(clazz, formData);
        }

        public GrailsInstallation[] getInstallations() {
            return installations;
        }

        public FormValidation doCheckGrailsHome(@QueryParameter String value) {
            if (!Hudson.getInstance().hasPermission(Hudson.ADMINISTER)) return FormValidation.ok();
            File f = new File(Util.fixNull(value));
            if (!f.isDirectory()) {
                return FormValidation.error(f + " is not a directory");
            }

            if (!new File(f, "bin/grails").exists() && !new File(f, "bin/grails.bat").exists()) {
                return FormValidation.error(f + " doesn't look like a Grails directory");
            }
            return FormValidation.ok();
        }

    }
}
