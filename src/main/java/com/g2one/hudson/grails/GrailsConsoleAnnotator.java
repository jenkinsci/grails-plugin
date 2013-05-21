package com.g2one.hudson.grails;


import hudson.console.LineTransformationOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * @author Kiyotaka Oku
 */
public class GrailsConsoleAnnotator extends LineTransformationOutputStream {

    private final OutputStream out;
    private final Charset charset;
    private boolean testFailed;

    public GrailsConsoleAnnotator(OutputStream out, Charset charset) {
        this.out = out;
        this.charset = charset;
    }

    @Override
    protected void eol(byte[] b, int len) throws IOException {
        String line = charset.decode(ByteBuffer.wrap(b, 0, len)).toString();
        line = trimEOL(line);

        if (line.toLowerCase().contains("tests failed")) {
            testFailed = true;
        }

        out.write(b, 0, len);
    }

    @Override
    public void close() throws IOException {
        super.close();
        out.close();
    }

    public boolean isBuildFailingDueToFailingTests() {
        return testFailed;
    }
}
