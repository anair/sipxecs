/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.site.dialplan;

import junit.framework.Test;
import net.sourceforge.jwebunit.junit.WebTestCase;

import org.sipfoundry.sipxconfig.site.SiteTestHelper;
import org.sipfoundry.sipxconfig.site.gateway.GatewaysTestUi;

/**
 * EditEmergencyRoutingTestUi
 */
public class EditEmergencyRoutingTestUi extends WebTestCase {
    public static Test suite() throws Exception {
        return SiteTestHelper.webTestSuite(EditEmergencyRoutingTestUi.class);
    }

    public void setUp() {
        getTestContext().setBaseUrl(SiteTestHelper.getBaseUrl());
        SiteTestHelper.home(getTester());
        clickLink("resetDialPlans");
        clickLink("EmergencyRouting");
    }

    public void testActivate() {
        SiteTestHelper.home(getTester());
        GatewaysTestUi.addTestGateways(getTester(), 3);
        SiteTestHelper.home(getTester());
        clickLink("EmergencyRouting");
        assertLinkPresent("erouting:addException");
        setTextField("erouting:emergencyNumber", "33");
        clickButton("form:apply");
    }

    public void testAddException() throws Exception {
        SiteTestHelper.home(getTester());
        GatewaysTestUi.addTestGateways(getTester(), 3);
        SiteTestHelper.home(getTester());
        clickLink("EmergencyRouting");
        setWorkingForm("form");
        setTextField("erouting:emergencyNumber", "12");
        clickLink("erouting:addException");
        SiteTestHelper.assertNoUserError(tester);
        setTextField("erouting:emergencyNumber", "33");
        setTextField("exception:callers", "11, 22");
        setTextField("exception:externalNumber", "911");
        clickButton("form:apply");
    }

    public void testActivateWithErrors() {
        assertLinkPresent("erouting:addException");
        clickButton("form:apply");
        // activate dial plans page active
        SiteTestHelper.assertUserError(tester);
    }
}
