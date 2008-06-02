/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.site.conference;

import junit.framework.Test;

import org.apache.commons.lang.ArrayUtils;
import org.sipfoundry.sipxconfig.site.ListWebTestCase;
import org.sipfoundry.sipxconfig.site.SiteTestHelper;

public class ListBridgesTestUi extends ListWebTestCase {
    public static Test suite() throws Exception {
        return SiteTestHelper.webTestSuite(ListBridgesTestUi.class);
    }

    public ListBridgesTestUi() throws Exception {
        super("ListBridges", "resetConferenceBridgeContext", "bridge");
        setHasDuplicate(false);
    }

    protected String[] getParamNames() {
        return new String[] {
            "item:name", "bridge:host", "item:description"
        };
    }

    protected String[] getParamValues(int i) {
        return new String[] {
            "bridge" + i, "host" + i + ".com", "Description" + i
        };
    }

    protected Object[] getExpectedTableRow(String[] paramValues) {
        Object[] expectedTableRow = super.getExpectedTableRow(paramValues);
        return ArrayUtils.add(expectedTableRow, 2, "Disabled");
    }

    public void setUp() {
        super.setUp();
        setAddLinkSubmit(true);
    }
    
    // FIXME: commented out standard test - see: XCF-2610
    public void testAdd() throws Exception {
    }
    
    public void testEdit() throws Exception {
    }
    
    public void testDisplay() {
    }
    
    public void testDelete() throws Exception {
    }
}
