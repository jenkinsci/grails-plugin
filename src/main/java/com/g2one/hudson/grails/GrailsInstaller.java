package com.g2one.hudson.grails;

import hudson.Extension;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstallation;

import org.kohsuke.stapler.DataBoundConstructor;

public class GrailsInstaller extends DownloadFromUrlInstaller {

    @DataBoundConstructor
    public GrailsInstaller(String id) {
        super(id);
    }

    @Extension
    public static class DescriptorImpl extends DownloadFromUrlInstaller.DescriptorImpl<GrailsInstaller> {

        @Override
        public String getDisplayName() {
            return "Install from mirrors";
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == GrailsInstallation.class;
        }
    }
}
