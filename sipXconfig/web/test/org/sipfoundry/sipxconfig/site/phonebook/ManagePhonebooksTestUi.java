/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.site.phonebook;

import junit.framework.Test;
import net.sourceforge.jwebunit.junit.WebTestCase;
import net.sourceforge.jwebunit.junit.WebTester;

import org.sipfoundry.sipxconfig.site.SiteTestHelper;

public class ManagePhonebooksTestUi extends WebTestCase {

    public static Test suite() throws Exception {
        return SiteTestHelper.webTestSuite(ManagePhonebooksTestUi.class);
    }

    protected void setUp() throws Exception {
        super.setUp();
        getTestContext().setBaseUrl(SiteTestHelper.getBaseUrl());
        SiteTestHelper.home(getTester());
        clickLink("link:phonebookReset");
    }

    public void testDisplay() {
        clickLink("link:managePhonebooks");
        SiteTestHelper.assertNoException(tester);
        assertElementPresent("phonebook:list");
    }
    
    public void testEditPhonebook() {
        seedPhonebook(tester, "manage-phonebooks");
        SiteTestHelper.home(getTester());
        clickLink("link:managePhonebooks");
        clickLinkWithText("manage-phonebooks");
        assertElementPresent("phonebookForm");
        // ok button tests that callback is present
        assertButtonPresent("form:ok");
    }
    
    public static void seedPhonebook(WebTester tester, String name) {        
        SiteTestHelper.home(tester);
        tester.clickLink("link:phonebook");        
        SiteTestHelper.initUploadFields(tester, "EditPhonebook");
        tester.setTextField("name", name);
        tester.clickButton("form:apply");        
    }
}
