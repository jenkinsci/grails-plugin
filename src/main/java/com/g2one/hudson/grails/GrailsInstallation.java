package com.g2one.hudson.grails;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentSpecific;
import hudson.model.TaskListener;
import hudson.model.Node;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import hudson.tools.ToolInstallation;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;


public final class GrailsInstallation extends ToolInstallation implements EnvironmentSpecific<GrailsInstallation>, NodeSpecific<GrailsInstallation> {

    @DataBoundConstructor
    public GrailsInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    public File getExecutable() {
        String execName;
        if (File.separatorChar == '\\')
            execName = "grails.bat";
        else
            execName = "grails";

        return new File(getHome(), "bin/" + execName);
    }

    public boolean getExists() {
        return getExecutable().exists();
    }

    public GrailsInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new GrailsInstallation(getName(), translateFor(node, log), getProperties().toList());
    }

    public GrailsInstallation forEnvironment(EnvVars environment) {
        return new GrailsInstallation(getName(), environment.expand(getHome()), getProperties().toList());
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<GrailsInstallation> {

        private volatile GrailsInstallation[] installations = new GrailsInstallation[0];

        @Override
        public String getDisplayName() {
            return "Grails";
        }

        @Override
        public List<? extends ToolInstaller> getDefaultInstallers() {
            return Collections.singletonList(new GrailsInstaller(null));
        }

        @Override
        public GrailsInstallation[] getInstallations() {
            return installations;
        }

        @Override
        public void setInstallations(GrailsInstallation... installations) {
            this.installations = installations;
            save();
        }
    }
}
