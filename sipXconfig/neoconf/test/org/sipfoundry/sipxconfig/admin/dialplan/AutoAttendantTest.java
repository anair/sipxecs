/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.admin.dialplan;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.sipfoundry.sipxconfig.TestHelper;
import org.sipfoundry.sipxconfig.common.DialPad;
import org.sipfoundry.sipxconfig.setting.Setting;

public class AutoAttendantTest extends XMLTestCase {

    private VxmlGenerator m_vxml;

    protected void setUp() throws Exception {
        super.setUp();
        XMLUnit.setIgnoreWhitespace(true);

        m_vxml = new VxmlGenerator();
        String etc = TestHelper.getSysDirProperties().getProperty("sysdir.etc");
        m_vxml.setScriptsDirectory(etc);
        m_vxml.setVelocityEngine(TestHelper.getVelocityEngine());
        m_vxml.setDomainManager(TestHelper.getTestDomainManager("sipfoundry.org"));
    }

    public void testGetSystemName() {
        AutoAttendant aa = new AutoAttendant();
        assertEquals("xcf-1", aa.getSystemName());
        assertFalse(aa.isAfterhour());
        assertFalse(aa.isOperator());
        assertFalse(aa.isPermanent());

        AutoAttendant operator = new AutoAttendant();
        operator.setSystemId(AutoAttendant.OPERATOR_ID);
        assertEquals("operator", operator.getSystemName());
        assertFalse(operator.isAfterhour());
        assertTrue(operator.isOperator());
        assertTrue(operator.isPermanent());

        AutoAttendant afterhour = new AutoAttendant();
        afterhour.setSystemId(AutoAttendant.AFTERHOUR_ID);
        assertEquals("afterhour", afterhour.getSystemName());
        assertTrue(afterhour.isAfterhour());
        assertFalse(afterhour.isOperator());
        assertTrue(afterhour.isPermanent());
    }

    private AutoAttendant createAutoAttendant() {
        return new AutoAttendant() {
            protected Setting loadSettings() {
                return TestHelper.loadSettings("sipxvxml/autoattendant.xml");
            }
        };
    }

    // TODO: fix the test after autoattendant.vm has been changed
    // see: http://paradise.pingtel.com/viewsvn/sipX?view=rev&rev=6846
    // test should not depend on real autoattendant.vm
    public void testActivateDefaultAttendant() throws Exception {
        AutoAttendant aa = createAutoAttendant();
        aa.setVxmlGenerator(new VxmlGenerator() {
            public String getPromptsDirectory() {
                return "prompts/";
            }
        });
        aa.setPrompt("prompt.wav");

        AttendantMenu menu = new AttendantMenu();
        menu.addMenuItem(DialPad.NUM_0, AttendantMenuAction.OPERATOR);
        menu.addMenuItem(DialPad.NUM_1, AttendantMenuAction.DISCONNECT);
        aa.setMenu(menu);

        aa.setSettingValue("onfail/transfer", "1");
        aa.setSettingValue("onfail/transfer-extension", "999");

        StringWriter actualXml = new StringWriter();
        m_vxml.generate(aa, actualXml);

        InputStream referenceXmlStream = AutoAttendant.class
                .getResourceAsStream("expected-autoattendant.xml");

        assertXMLEqual(new InputStreamReader(referenceXmlStream), new StringReader(actualXml
                .toString()));

    }
}
