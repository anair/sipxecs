package org.sipfoundry.sipxconfig.site.admin.commserver;

import junit.framework.Test;
import net.sourceforge.jwebunit.junit.WebTestCase;

import org.sipfoundry.sipxconfig.site.SiteTestHelper;

public class LocationsPageTestUi extends WebTestCase {

    public static Test suite() throws Exception {
        return SiteTestHelper.webTestSuite(LocationsPageTestUi.class);
    }

    @Override
    protected void setUp() throws Exception {
        getTestContext().setBaseUrl(SiteTestHelper.getBaseUrl());
        SiteTestHelper.home(getTester());
        clickLink("seedLocationsManager");
        clickLink("toggleNavigation");
        clickLink("menu.locations");
    }

    public void testDisplay() {
        SiteTestHelper.assertNoUserError(tester);
        assertTextPresent("host.example.org");
        assertEquals("Registered", SiteTestHelper.getCellAsText(getTable("locations:list"), 1, 4));
        clickLink("editLocationLink");
        assertLinkPresent("link:configureLocation");
        assertLinkPresent("link:listServices");
        assertLinkPresent("link:monitorTarget");
        SiteTestHelper.assertNoUserError(tester);
    }

    public void testAddLocation() {
        SiteTestHelper.assertNoUserError(tester);
        clickLink("locations:add");
        SiteTestHelper.assertNoUserError(tester);
        setTextField("location:description", "newLocation");
        setTextField("location:address", "192.168.1.1");
        setTextField("location:fqdn", "another.example.org");
        setTextField("location:password", "123");
        clickButton("form:ok");

        SiteTestHelper.assertNoUserError(tester);
        assertTextPresent("another.example.org");
        assertEquals("Uninitialized", SiteTestHelper.getCellAsText(getTable("locations:list"), 2, 4));
        clickLink("editLocationLink_0");
        assertLinkNotPresent("link:configureLocation");
        assertLinkNotPresent("link:listServices");
        assertLinkNotPresent("link:monitorTarget");
        SiteTestHelper.assertNoUserError(tester);
    }

    public void testDeleteLocation() {
        SiteTestHelper.assertNoUserError(tester);
        setWorkingForm("Form");
        SiteTestHelper.selectRow(tester, 0, true);
        clickButton("locations:delete");
        SiteTestHelper.assertNoUserError(tester);
        assertTextNotPresent("host.example.org");
    }

    public void testSendProfiles() {
        SiteTestHelper.assertNoException(tester);
        SiteTestHelper.assertNoUserError(tester);
        clickLink("locations:add");
        setTextField("location:description", "wrong location");
        setTextField("location:address", "1.1.1.1");
        setTextField("location:fqdn", "test.wrong.org");
        setTextField("location:password", "123");
        clickButton("form:ok");

        SiteTestHelper.assertNoException(tester);
        SiteTestHelper.assertNoUserError(tester);
        setWorkingForm("Form");
        SiteTestHelper.selectRow(tester, 0, true);
        clickButton("locations:sendProfiles");

        // does not throw exceptions any more since replication happens in the background
        SiteTestHelper.assertNoException(tester);
    }
}
