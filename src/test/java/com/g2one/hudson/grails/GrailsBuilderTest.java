package com.g2one.hudson.grails;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import hudson.EnvVars;
import hudson.util.ArgumentListBuilder;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.internal.matchers.TypeSafeMatcher;


public class GrailsBuilderTest {

    @Test
    public void getTargetsToRun() {

        assertThat(newBuilderWithTargetsAndForceUpgrade(null, false).getTargetsToRun(null),
                is(arrayOfStrings()));

        assertThat(newBuilderWithTargetsAndForceUpgrade(null, true).getTargetsToRun(null),
                is(arrayOfStrings(new String[] {"upgrade", "--non-interactive"})));

        assertThat(newBuilderWithTargetsAndForceUpgrade("test-app", true).getTargetsToRun(null),
                is(arrayOfStrings(
                        new String[] {"upgrade", "--non-interactive"},
                        new String[] {"test-app"})));

        assertThat(newBuilderWithTargetsAndForceUpgrade("\"test-app -clean\"", true).getTargetsToRun(null),
                is(arrayOfStrings(
                        new String[] {"upgrade", "--non-interactive"},
                        new String[] {"test-app", "-clean"})));
    }

    @Test
    public void addArgumentWithTarget() {
        ArgumentListBuilder args = new ArgumentListBuilder();
        EnvVars env = new EnvVars();
        newBuilder().addArgument("--non-interactive", true, args, env, new String[] {"test-app"});
        assertThat(args.toStringWithQuote(), is("--non-interactive"));
    }

    @Test
    public void addArgumentWithTargetAndArgs() {
        ArgumentListBuilder args = new ArgumentListBuilder();
        EnvVars env = new EnvVars();
        newBuilder().addArgument("--non-interactive", true, args, env, new String[] {"test-app", "-clean"});
        assertThat(args.toStringWithQuote(), is("-clean --non-interactive"));
    }

    @Test
    public void addArgument_option_exists() {
        ArgumentListBuilder args = new ArgumentListBuilder();
        EnvVars env = new EnvVars();
        newBuilder().addArgument("--non-interactive", true, args, env, new String[] {"test-app", "--non-interactive", "-clean"});
        assertThat(args.toStringWithQuote(), is("--non-interactive -clean"));
    }

    @Test
    public void addArgumentWithTargetAndArgs_all_options() {

        ArgumentListBuilder args = new ArgumentListBuilder();
        EnvVars env = new EnvVars();
        GrailsBuilder builder = newBuilder();
        String[] targetsAndArgs = new String[] {"test-app", "-clean"};

        builder.addArgument("--non-interactive", true, args, env, targetsAndArgs);
        builder.addArgument("--plain-output", true, args, env, targetsAndArgs);
        builder.addArgument("--stacktrace", true, args, env, targetsAndArgs);
        builder.addArgument("--verbose", true, args, env, targetsAndArgs);
        builder.addArgument("--refresh-dependencies", true, args, env, targetsAndArgs);

        assertThat(args.toStringWithQuote(),
                is("-clean --non-interactive --plain-output --stacktrace --verbose --refresh-dependencies"));
    }

    private GrailsBuilder newBuilder() {
        return new GrailsBuilder(null, null, null, null, null, null, null, false, false, false, false, false, false, false);
    }

    private GrailsBuilder newBuilderWithTargetsAndForceUpgrade(String targets, Boolean forceUpgrade) {
        return new GrailsBuilder(targets, null, null, null, null, null, null, forceUpgrade, false, false, false, false, false, false);
    }

    private List<String[]> stringsList(String... arr) {
        List<String[]> result = new ArrayList<String[]>();
        if (arr.length > 0) result.add(arr);
        return result;
    }

    private static Matcher<List<String[]>> arrayOfStrings(final String[]... arrays) {
        return new TypeSafeMatcher<List<String[]>>() {
            @Override
            public boolean matchesSafely(List<String[]> item) {
                if (arrays.length == item.size()) {
                    for (int i=0; i<arrays.length; i++) {
                        if (!Arrays.equals(arrays[i], item.get(i))) {
                            return false;
                        }
                    }
                } else {
                    return false;
                }
                return true;
            }

            public void describeTo(Description description) {
                StringBuilder buff = new StringBuilder();
                for (String[] arr : arrays) {
                    buff.append(Arrays.toString(arr));
                }
                description.appendText(buff.toString());
            }
        };
    }
}
