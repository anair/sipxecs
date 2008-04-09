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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.Test;
import net.sourceforge.jwebunit.html.Table;
import net.sourceforge.jwebunit.junit.WebTestCase;
import net.sourceforge.jwebunit.junit.WebTester;

import org.apache.commons.io.IOUtils;
import org.sipfoundry.sipxconfig.site.SiteTestHelper;
import org.sipfoundry.sipxconfig.test.TestUtil;

public class EditAutoAttendantTestUi extends WebTestCase {

    static public final String PROMPT_TEST_FILE = "thankyou_goodbye.wav";

    private static final String KEYS = "0123456789*#";

    private static final String[][] FACTORY_DEFAULT = {
        {
            KEYS, "Repeat Prompt", ""
        }, {
            KEYS, "Operator", ""
        }
    };

    public static Test suite() throws Exception {
        return SiteTestHelper.webTestSuite(EditAutoAttendantTestUi.class);
    }

    protected void setUp() {
        getTestContext().setBaseUrl(SiteTestHelper.getBaseUrl());
        SiteTestHelper.home(getTester());
        clickLink("resetDialPlans");
        SiteTestHelper.assertNoException(tester);
    }

    public void testNewAttendant() throws IOException {
        seedPromptFile();
        clickLink("NewAutoAttendant");

        // need to rerender page after prompt is copied in
        assertTableRowsEqual("attendant:menuItems", 1, FACTORY_DEFAULT);

        setTextField("item:name", "New Attendant");
        setTextField("item:description", "created by EditAutoAttendantTestUi.testNewAttendant");
        // need to test by uploading file, this alone will not work: selectOption("prompt",
        // PROMPT_TEST_FILE);
        SiteTestHelper.initUploadFields(tester, "EditAutoAttendantTestUI");
        clickButton("form:apply");
        SiteTestHelper.assertNoException(tester);
    }

    public void testUpload() {
        File expectedFile = new File(getCleanPromptsDir("prompts"), PROMPT_TEST_FILE);
        assertFalse(expectedFile.exists());
        clickLink("NewAutoAttendant");

        setTextField("item:name", "Upload Prompt Test");
        setTextField("item:description", "created by EditAutoAttendantTestUi.testUpload");
        String actualFilename = TestUtil.getTestSourceDirectory(getClass()) + "/"
                + PROMPT_TEST_FILE;
        File actualFile = new File(actualFilename);
        assertTrue(actualFile.exists());
        SiteTestHelper.setUpload(tester, "promptUpload", actualFile);
        clickButton("form:apply");
        SiteTestHelper.assertNoException(tester);
        assertTrue(expectedFile.exists());
        assertEquals(actualFile.length(), expectedFile.length());
    }

    public void testReset() throws Exception {
        seedPromptFile();
        clickLink("NewAutoAttendant");
        selectOption("addMenuItemAction", "Voicemail Login");
        SiteTestHelper.initUploadFields(tester, "EditAutoAttendantTestUI");
        clickButton("attendant:addMenuItem");
        SiteTestHelper.initUploadFields(tester, "EditAutoAttendantTestUI");
        clickButton("attendant:reset");
        assertTableRowsEqual("attendant:menuItems", 1, FACTORY_DEFAULT);
    }

    public void testRemoveMenuItems() throws Exception {
        seedPromptFile();
        clickLink("NewAutoAttendant");
        assertElementPresent("attendant:form");
        SiteTestHelper.initUploadFields(tester, "EditAutoAttendantTestUI");
        SiteTestHelper.enableCheckbox(tester, "selectedRow", 0, true);

        clickButton("attendant:removeMenuItems");
        String[][] expectedMenuItems = {
            {
                KEYS, "Operator", ""
            },
        };
        assertTablePresent("attendant:menuItems");
        assertTableRowsEqual("attendant:menuItems", 1, expectedMenuItems);
    }

    public void testAddMenuItems() throws Exception {
        seedAutoAttendant(tester);
        SiteTestHelper.home(tester);

        clickLink("NewAutoAttendant");
        assertElementPresent("attendant:form");

        selectOption("addMenuItemAction", "Auto Attendant");
        SiteTestHelper.initUploadFields(tester, "EditAutoAttendantTestUI");
        clickButton("attendant:addMenuItem");
        selectOption("attendantParameter", "New Attendant");
        SiteTestHelper.initUploadFields(tester, "EditAutoAttendantTestUI");
        clickButton("form:apply");

        Table expected = new Table(FACTORY_DEFAULT);
        String[][] defaultMenuItems = {
            {
                KEYS, "Auto Attendant", "select...New Attendant"
            },
        };
        expected.appendRows(defaultMenuItems);
        assertTableRowsEqual("attendant:menuItems", 1, expected);
        tester.assertSelectedOptionEquals("attendantParameter", "New Attendant");

        selectOption("addMenuItemAction", "Deposit Voicemail");
        SiteTestHelper.initUploadFields(tester, "EditAutoAttendantTestUI");
        clickButton("attendant:addMenuItem");
        setTextField("extensionParameter", "3232");
        SiteTestHelper.initUploadFields(tester, "EditAutoAttendantTestUI");
        clickButton("form:apply");

        String[][] vmDepositRow = {
            // 3rd column - curious why text fields do not show up text in table when
            // all other form elements do. May be related to webunit version.
            {
                KEYS, "Deposit Voicemail", ""
            },
        };
        // verify 3rd column data here
        assertTextFieldEquals("extensionParameter", "3232");

        expected.appendRows(vmDepositRow);
        assertTableRowsEqual("attendant:menuItems", 1, expected);
    }

    public static final String seedPromptFile(String dir) throws IOException {
        File promptsDir = getCleanPromptsDir(dir);
        copyFileToDirectory(PROMPT_TEST_FILE, promptsDir);

        return PROMPT_TEST_FILE;
    }

    public static final String seedPromptFile() throws IOException {
        return seedPromptFile("prompts");
    }

    private static void seedAutoAttendant(WebTester tester) throws Exception {
        seedPromptFile();
        tester.clickLink("NewAutoAttendant");
        tester.setTextField("item:name", "New Attendant");
        tester.setTextField("item:description",
                "created by EditAutoAttendantTestUi.seedAutoAttendant");
        SiteTestHelper.initUploadFields(tester, "EditAutoAttendantTestUI");
        tester.clickButton("form:apply");
    }

    private static final File getCleanPromptsDir(String dir) {
        File promptsDir = new File(SiteTestHelper.getArtificialSystemRootDirectory(), dir);
        SiteTestHelper.cleanDirectory(promptsDir.getPath());
        return promptsDir;
    }

    private static final void copyFileToDirectory(String filename, File dir) throws IOException {
        InputStream in = EditAutoAttendantTestUi.class.getResourceAsStream(filename);
        SiteTestHelper.cleanDirectory(dir.getPath());
        FileOutputStream out = new FileOutputStream(new File(dir, filename));
        IOUtils.copy(in, out);
        IOUtils.closeQuietly(in);
        IOUtils.closeQuietly(out);
    }
}
