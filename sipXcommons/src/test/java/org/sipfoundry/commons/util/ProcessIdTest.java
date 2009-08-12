/**
 * Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 */
package org.sipfoundry.commons.util;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Basic test of the Hostname class.
 * <p>
 *
 * @author Mardy Marshall
 */
public class ProcessIdTest extends TestCase {
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public ProcessIdTest(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(ProcessIdTest.class);
    }

    /**
     * Rigorous Test :-)
     */
    public void testApp() {
        if (ProcessID.get() <= 0) {
            fail("Unable to retrieve process ID.");
        }
    }

}
