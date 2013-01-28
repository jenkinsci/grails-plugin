package com.g2one.hudson.grails;

import hudson.EnvVars;
import hudson.Extension;
import hudson.XmlFile;
import hudson.model.EnvironmentSpecific;
import hudson.model.TaskListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import hudson.tools.ToolInstallation;
import hudson.util.XStream2;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;


public final class GrailsInstallation extends ToolInstallation implements EnvironmentSpecific<GrailsInstallation>, NodeSpecific<GrailsInstallation> {

    public transient String grailsHome;

    @DataBoundConstructor
    public GrailsInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    public GrailsInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new GrailsInstallation(getName(), translateFor(node, log), getProperties().toList());
    }

    public GrailsInstallation forEnvironment(EnvVars environment) {
        return new GrailsInstallation(getName(), environment.expand(getHome()), getProperties().toList());
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<GrailsInstallation> {

        private static final Logger LOGGER = Logger.getLogger(Descriptor.class.getName());

        private volatile GrailsInstallation[] installations = new GrailsInstallation[0];

        public DescriptorImpl() {
            load();
        }

        @Override
        public synchronized void load() {
            File root = Hudson.getInstance().root;
            if (new File(root, GrailsInstallation.class.getName() + ".xml").exists()) {
                super.load();
            } else if (new File(root, GrailsBuilder.class.getName() + ".xml").exists()) {
                loadFromOldConfigFile();
            }
        }

        private void loadFromOldConfigFile() {
            XStream2 stream = new XStream2();
            stream.addCompatibilityAlias(GrailsBuilder.DescriptorImpl.class.getName(), GrailsInstallation.DescriptorImpl.class);
            XmlFile file = new XmlFile(stream, new File(Hudson.getInstance().root, GrailsBuilder.class.getName() + ".xml"));
            try {
                file.unmarshal(this);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to load "+file, e);
            }
            for (int i=0; i<installations.length; i++) {
                GrailsInstallation inst = installations[i];
                installations[i] = new GrailsInstallation(inst.getName(), inst.grailsHome, null);
            }
        }

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
