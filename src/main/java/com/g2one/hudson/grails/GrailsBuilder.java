package com.g2one.hudson.grails;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.UnflaggedOption;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.console.ConsoleNote;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;

import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

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

    @DataBoundConstructor
    public GrailsBuilder(String targets, String name, String grailsWorkDir, String projectWorkDir, String projectBaseDir, String serverPort, String properties, Boolean forceUpgrade, Boolean nonInteractive, Boolean useWrapper, Boolean plainOutput, Boolean stackTrace, Boolean verbose, Boolean refreshDependencies) {
        this.name = name;
        this.targets = targets;
        this.grailsWorkDir = grailsWorkDir;
        this.projectWorkDir = projectWorkDir;
        this.projectBaseDir = projectBaseDir;
        this.serverPort = serverPort;
        this.properties = properties;
        this.forceUpgrade = forceUpgrade;
        this.nonInteractive = nonInteractive;
        this.useWrapper = !useWrapper;
        this.plainOutput = plainOutput;
        this.stackTrace = stackTrace;
        this.verbose = verbose;
        this.refreshDependencies = refreshDependencies;
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
            } else {
                execName = launcher.isUnix() ? "grails" : "grails.bat";
            }


            GrailsInstallation grailsInstallation = useWrapper ? null : getGrails();

            if (grailsInstallation != null) {
                grailsInstallation = grailsInstallation.forEnvironment(env)
                    .forNode(Computer.currentComputer().getNode(), listener);
                env.put("GRAILS_HOME", grailsInstallation.getHome());
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
                    systemProperties.put("grails.work.dir", evalTarget(env, grailsWorkDir.trim()));
                } else {
                    systemProperties.put("grails.work.dir", build.getWorkspace().toURI().getPath() + "/target");
                }
                if (projectWorkDir != null && !"".equals(projectWorkDir.trim())) {
                    systemProperties.put("grails.project.work.dir", evalTarget(env, projectWorkDir.trim()));
                }
                if (serverPort != null && !"".equals(serverPort.trim())) {
                    systemProperties.put("server.port", evalTarget(env, serverPort.trim()));
                }
                if (systemProperties.size() > 0) {
                    args.addKeyValuePairs("-D", systemProperties);
                }
                args.addKeyValuePairsFromPropertyString("-D", properties, build.getBuildVariableResolver());

                args.add(target);
                addArgument("--non-interactive", nonInteractive, args, env, targetsAndArgs);
                addArgument("--plain-output", plainOutput, args, env, targetsAndArgs);
                addArgument("--stacktrace", stackTrace, args, env, targetsAndArgs);
                addArgument("--verbose", verbose, args, env, targetsAndArgs);
                addArgument("--refresh-dependencies", refreshDependencies, args, env, targetsAndArgs);

                if (!launcher.isUnix()) {
                    args.prepend("cmd.exe", "/C");
                    args.add("&&", "exit", "%%ERRORLEVEL%%");
                }

                try {
                    WrappedListener wrappedListener = WrappedListener.create(listener);
                    int r = launcher.launch().cmds(args).envs(env).stdout(wrappedListener).pwd(getBasePath(build)).join();
                    if (r != 0 && !wrappedListener.isBuildFailingDueToFailingTests()) {
                        return false;
                    }
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

    protected void addArgument(String option, Boolean optionEnabled, ArgumentListBuilder args, EnvVars env, String[] targetsAndArgs) {
        boolean foundArgument = false;
        for (int i = 1; i < targetsAndArgs.length; i++) {
            String arg = evalTarget(env, targetsAndArgs[i]);
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

    protected List<String[]> getTargetsToRun(EnvVars env) {
        List<String[]> targetsToRun = new ArrayList<String[]>();
        if(forceUpgrade) {
            targetsToRun.add(new String[]{"upgrade", "--non-interactive"});
        }
        if (targets != null && targets.length() > 0) {
            try {
            	String targetsEval =this.targets; 
            	if(env!=null){
            		targetsEval = evalTarget(env,this.targets);
            	}
                JSAP jsap = new JSAP();
                UnflaggedOption option = new UnflaggedOption("targets");
                option.setGreedy(true);
                jsap.registerParameter(option);
                JSAPResult jsapResult = jsap.parse(targetsEval);
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

class WrappedListener implements BuildListener {
	private final BuildListener wrapped;
	private PrintStream wrappedLogger;
	private final MaxLengthList<String> consoleCache;

//> CONSTRUCTORS
	private WrappedListener(BuildListener listener) {
		this.wrapped = listener;
		// TODO might be good to let users set this - depending on what
		// they are running in their build, e.g. Cobertura or exceptions
		// in e.g. TestPhasesEnd, they may need more cacheing done.
		this.consoleCache = new MaxLengthList(200);
	}

//> PASS THRUS TO WRAPPED LISTENER
	public void started(List<Cause> causes) { wrapped.started(causes); }
	public void finished(Result result) { wrapped.finished(result); }
	public void annotate(ConsoleNote ann) throws IOException { wrapped.annotate(ann); }
	public PrintWriter error(String msg) { return wrapped.error(msg); }
	public PrintWriter error(String format, Object... args) { return wrapped.error(format, args); }
	public PrintWriter fatalError(String msg) { return wrapped.fatalError(msg); }
	public PrintWriter fatalError(String format, Object... args) { return wrapped.fatalError(format, args); }
	public void hyperlink(String url, String text) throws IOException { wrapped.hyperlink(url, text); }

//> CUSTOM METHODS AND OVERRIDES
	public synchronized PrintStream getLogger() {
		if(wrappedLogger == null) {
			log("getLogger() :: wrapping logger...");
			PrintStream realLogger = wrapped.getLogger();
			wrappedLogger = new CachedPrintStream(realLogger, consoleCache, this);
		}
		return wrappedLogger;
	}

	boolean isBuildFailingDueToFailingTests() {
		for(String consoleLine : consoleCache) {
			if(consoleLine.toLowerCase().contains("tests failed")) {
				return true;
			}
		}
		return false;
	}

	static WrappedListener create(BuildListener listener) {
		return new WrappedListener(listener);
	}

	private void log(String message) {
		error("WrappedListener :: LOG :: " + message);
	}
}

class CachedPrintStream extends PrintStream {
	private final PrintStream wrapped;
	private final List<String> cache;
	private final BuildListener listener;

	CachedPrintStream(PrintStream wrapped, List<String> cache, BuildListener listener) {
		super(wrapped);
		this.wrapped = wrapped;
		this.cache = cache;
		this.listener = listener;
	}

	// This appears to be the only method called on the Listener's logger.
	// If others are called in future, we might need to decorate them as well.
	public void write(byte[] buff, int off, int len) {
		// TODO may need to explicitly set encoding here
		cache.add(new String(buff, off, len));
		super.write(buff, off, len);
	}
}

class MaxLengthList<E> extends LinkedList<E> {
	private final int lengthLimit;

	MaxLengthList(int lengthLimit) {
		this.lengthLimit = lengthLimit;
	}

	public synchronized boolean add(E e) {
		while(size() >= lengthLimit) {
			removeFirst();
		}
		return super.add(e);
	}
}

