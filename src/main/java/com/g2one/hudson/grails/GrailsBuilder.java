package com.g2one.hudson.grails;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.UnflaggedOption;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormFieldValidator;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class GrailsBuilder extends Builder {

    private final String targets;
    private final String name;
    private boolean runTestApp;
    private boolean runWar;
    private String grailsWorkDir;
    private String projectWorkDir;
    private boolean runClean;
    private boolean runUpgrade;

    @DataBoundConstructor
    public GrailsBuilder(String targets, String name, boolean runUpgrade, boolean runClean, boolean runTestApp, boolean runWar, String grailsWorkDir) {
        this.name = name;
        this.runClean = runClean;
        this.runUpgrade = runUpgrade;
        this.targets = targets;
        this.runTestApp = runTestApp;
        this.runWar = runWar;
        this.grailsWorkDir = grailsWorkDir;
    }

    public boolean isRunUpgrade() {
        return runUpgrade;
    }

    public String getProjectWorkDir() {
        return projectWorkDir;
    }

    public void setProjectWorkDir(String projectWorkDir) {
        this.projectWorkDir = projectWorkDir;
    }

    public void setRunUpgrade(boolean runUpgrade) {
        this.runUpgrade = runUpgrade;
    }

    public boolean isRunClean() {
        return runClean;
    }

    public void setRunClean(boolean runClean) {
        this.runClean = runClean;
    }

    public String getGrailsWorkDir() {
        return grailsWorkDir;
    }

    public void setGrailsWorkDir(String grailsWorkDir) {
        this.grailsWorkDir = grailsWorkDir;
    }

    public String getName() {
        return name;
    }

    public String getTargets() {
        return targets;
    }

    public boolean isRunTestApp() {
        return runTestApp;
    }

    public void setRunTestApp(boolean runTestApp) {
        this.runTestApp = runTestApp;
    }

    public boolean isRunWar() {
        return runWar;
    }

    public void setRunWar(boolean runWar) {
        this.runWar = runWar;
    }

    public GrailsInstallation getGrails() {
        for (GrailsInstallation i : DESCRIPTOR.getInstallations()) {
            if (name != null && i.getName().equals(name))
                return i;
        }
        return null;
    }

    public boolean perform(Build<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
        Project proj = build.getProject();
        Map<String, List<String>> targetsToRun = getTargetsToRun();
        if (targetsToRun.size() > 0) {
            String execName;
            if (launcher.isUnix()) {
                execName = "grails";
            } else {
                execName = "grails.bat";
            }

            GrailsInstallation grailsInstallation = getGrails();

            Map<String, String> env = build.getEnvVars();
            if (grailsInstallation != null) {
                env.put("GRAILS_HOME", grailsInstallation.getGrailsHome());
            }

            Set<Map.Entry<String, List<String>>> targetEntrySet = targetsToRun.entrySet();
            for (Map.Entry<String, List<String>> targetEntry : targetEntrySet) {
                String target = targetEntry.getKey();
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
                    sytemProperties.put("project.work.dir", projectWorkDir.trim());
                }
                if (sytemProperties.size() > 0) {
                    args.addKeyValuePairs("-D", sytemProperties);
                }

                args.add(target);
                List<String> targetArgs = targetEntry.getValue();
                if (targetArgs != null && targetArgs.size() > 0) {
                    for (String targetArg : targetArgs) {
                        args.add(targetArg);
                    }
                }

                if (!launcher.isUnix()) {
                    args.prepend("cmd.exe", "/C");
                    args.add("&&", "exit", "%%ERRORLEVEL%%");
                }

                try {
                    int r = launcher.launch(args.toCommandArray(), env, listener.getLogger(), proj.getModuleRoot()).join();
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

    protected Map<String, List<String>> getTargetsToRun() {
        Map<String, List<String>> targetsToRun = new LinkedHashMap<String, List<String>>();
        if (runUpgrade) {
            List<String> args = new ArrayList<String>();
            args.add("-force");
            targetsToRun.put("upgrade", args);
        }
        if (runClean) targetsToRun.put("clean", null);
        if (runTestApp) targetsToRun.put("test-app", null);
        if (runWar) targetsToRun.put("war", null);
        System.out.println("targets: " + targets);
        if (targets != null && targets.length() > 0) {
            try {
                JSAP jsap = new JSAP();
                UnflaggedOption option = new UnflaggedOption("targets");
                option.setGreedy(true);
                jsap.registerParameter(option);
                JSAPResult jsapResult = jsap.parse(targets);
                String[] strings = jsapResult.getStringArray("targets");
                for (String requestedTarget : strings) {
                    List<String> args = new ArrayList<String>();
                    String[] values = requestedTarget.split(" ");
                    String command = values[0];
                    for (int i = 1; i < values.length; i++) {
                        args.add(values[i]);
                    }
                    targetsToRun.put(command, args);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return targetsToRun;
    }

    public Descriptor<Builder> getDescriptor() {
        return DESCRIPTOR;
    }

    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends Descriptor<Builder> {

        private volatile GrailsInstallation[] installations = new GrailsInstallation[0];

        DescriptorImpl() {
            super(GrailsBuilder.class);
        }

        public String getDisplayName() {
            return "Build With Grails";
        }

        public boolean configure(StaplerRequest req) throws FormException {
            installations = req.bindParametersToList(GrailsInstallation.class, "grails.").toArray(new GrailsInstallation[0]);
            save();
            return true;
        }

        public Builder newInstance(StaplerRequest req) {
            return req.bindParameters(GrailsBuilder.class, "grails.");
        }


        public GrailsInstallation[] getInstallations() {
            return installations;
        }

        public void doCheckGrailsHome(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new FormFieldValidator(req, rsp, true) {
                public void check() throws IOException, ServletException {
                    File f = getFileParameter("value");
                    if (!f.isDirectory()) {
                        error(f + " is not a directory");
                        return;
                    }

                    if (!new File(f, "bin/grails").exists() && !new File(f, "bin/grails.bat").exists()) {
                        error(f + " doesn't look like a Grails directory");
                        return;
                    }
                    ok();
                }
            }.process();
        }

    }
}
