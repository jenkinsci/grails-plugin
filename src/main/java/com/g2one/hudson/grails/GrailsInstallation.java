package com.g2one.hudson.grails;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;


public final class GrailsInstallation {
    private final String name;
    private final String grailsHome;

    @DataBoundConstructor
    public GrailsInstallation(String name, String home) {
        this.name = name;
        this.grailsHome = home;
    }

    public String getGrailsHome() {
        return grailsHome;
    }

    public String getName() {
        return name;
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
