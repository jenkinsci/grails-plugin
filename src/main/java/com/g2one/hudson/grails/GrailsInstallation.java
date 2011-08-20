package com.g2one.hudson.grails;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.tools.ToolProperty;
import hudson.tools.ToolInstallation;

import java.io.File;
import java.util.List;


public final class GrailsInstallation extends ToolInstallation {

    public GrailsInstallation(String name, String home) {
        super(name, home);
    }

    @DataBoundConstructor
    public GrailsInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    public String getGrailsHome() {
        return getHome();
    }

    public File getExecutable() {
        String execName;
        if (File.separatorChar == '\\')
            execName = "grails.bat";
        else
            execName = "grails";

        return new File(getGrailsHome(), "bin/" + execName);
    }

    public boolean getExists() {
        return getExecutable().exists();
    }
}
