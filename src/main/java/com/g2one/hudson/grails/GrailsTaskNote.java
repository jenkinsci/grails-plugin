package com.g2one.hudson.grails;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;

/**
 * @author Kiyotaka Oku
 */
public class GrailsTaskNote extends ConsoleNote {

    private final String target;

    public GrailsTaskNote(String target) {
        this.target = target;
    }

    @Override
    public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
        text.addMarkup(charPos, "<span data_grails_target=\"" + target + "\"></span>");
        return null;
    }

    @Extension
    public static class DescriptorImpl extends ConsoleAnnotationDescriptor {

        @Override
        public String getDisplayName() {
            return "Grails targets";
        }
    }
}
