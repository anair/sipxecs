/*
 *
 *
 * Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.site.service;

import junit.framework.Test;
import net.sourceforge.jwebunit.junit.WebTestCase;

import org.sipfoundry.sipxconfig.site.SiteTestHelper;

public class EditSipxServiceTestUi extends WebTestCase {

    public static Test suite() throws Exception {
        return SiteTestHelper.webTestSuite(EditSipxServiceTestUi.class);
    }

    public void initService(String service) {
        getTestContext().setBaseUrl(SiteTestHelper.getBaseUrl());
        SiteTestHelper.home(getTester());
        clickLink(service);
        clickLink("toggleNavigation");
        clickLink("menu.locations");

        SiteTestHelper.assertNoException(tester);
        SiteTestHelper.assertNoUserError(tester);

        clickLink("menu.locations");
        assertLinkPresent("editLocationLink");
        clickLink("editLocationLink");
        assertLinkPresent("link:listServices");
        clickLink("link:listServices");
        assertTablePresent("servicesTable");
        assertEquals(2, getTable("servicesTable").getRowCount());
        assertLinkPresent("editSipxService");
        clickLink("editSipxService");
        SiteTestHelper.assertNoException(tester);
    }

    public void testEditProxyServiceDisplay() {
        initService("seedProxyService");
        assertTextPresent("SIPX_PROXY_DEFAULT_SERIAL_EXPIRES");
    }

    public void testEditRegistrarServiceDisplay() {
        initService("seedRegistrarService");
        assertTextPresent("SIP_REDIRECT.160-ENUM.ADD_PREFIX");
    }

    public void testEditRegistrarServiceNoDuplicateCodes() {
        initService("seedRegistrarService");
        SiteTestHelper.dumpPage(tester);
    }

    public void testEditParkServiceDisplay() {
        initService("seedParkService");
        assertTextPresent("SIP_PARK_LOG_LEVEL");
    }

    public void testEditStatusServiceDisplay() {
        initService("seedStatusService");
        assertElementPresent("setting:SIP_STATUS_LOG_LEVEL");
    }

    public void testEditPageServiceDisplay() {
        initService("seedPageService");
        assertTextPresent("SIP_PAGE_LOG_LEVEL");
    }

    public void testEditFreeswitchServiceDisplay() {
        initService("seedFreeswitchService");
        assertElementPresent("setting:FREESWITCH_SIP_DEBUG");
    }

    public void testEditRelayServiceDisplay() {
        initService("seedRelayService");
        assertTextPresent("SIP_RELAY_LOG_LEVEL");
    }

    public void testEditIvrServiceDisplay() {
        initService("seedIvrService");
        assertTextPresent("log.level");
    }
}
