package com.g2one.hudson.grails;

import java.io.File;
import java.io.IOException;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;
import org.xml.sax.SAXException;

public class PluginTest extends HudsonTestCase {

    @LocalData
    public void testMigrateFromPluginVersion_1_4() throws IOException, SAXException, InterruptedException {
        GrailsInstallation[] installations = hudson.getDescriptorByType(GrailsInstallation.DescriptorImpl.class).getInstallations();
        assertEquals(2, installations.length);
        {
            GrailsInstallation inst = installations[0];
            assertEquals("grails-1.3.5", inst.getName());
            assertEquals("/usr/local/grails/grails-1.3.5", inst.getHome());
        }
        {
            GrailsInstallation inst = installations[1];
            assertEquals("grails-1.3.7", inst.getName());
            assertEquals("/usr/local/grails/grails-1.3.7", inst.getHome());
        }

        assertFalse("Old config file should be deleted.",
            new File(hudson.getRootDir(), GrailsBuilder.class.getName() + ".xml").exists());
        assertTrue("New config file should be created.",
            new File(hudson.getRootDir(), GrailsInstallation.class.getName() + ".xml").exists());
    }
}
