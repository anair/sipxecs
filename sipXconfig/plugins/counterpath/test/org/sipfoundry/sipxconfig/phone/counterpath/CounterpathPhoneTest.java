/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.phone.counterpath;

import junit.framework.TestCase;

import org.apache.commons.io.IOUtils;
import org.easymock.EasyMock;
import org.sipfoundry.sipxconfig.TestHelper;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.device.DeviceDefaults;
import org.sipfoundry.sipxconfig.device.MemoryProfileLocation;
import org.sipfoundry.sipxconfig.phone.Line;
import org.sipfoundry.sipxconfig.phone.PhoneContext;
import org.sipfoundry.sipxconfig.phone.PhoneTestDriver;
import org.sipfoundry.sipxconfig.phone.counterpath.CounterpathPhone.CounterpathLineDefaults;
import org.sipfoundry.sipxconfig.phone.counterpath.CounterpathPhone.CounterpathPhoneDefaults;
import org.sipfoundry.sipxconfig.speeddial.SpeedDial;

public class CounterpathPhoneTest extends TestCase {
    private Line m_line;
    private User m_user;
    private CounterpathPhone m_phone;

    protected void setUp() {
        CounterpathPhoneModel counterpathModel = new CounterpathPhoneModel("counterpath");
        counterpathModel.setProfileTemplate("counterpath/counterpath.ini.vm");
        counterpathModel.setModelId("counterpathCMCEnterprise");
        m_phone = new CounterpathPhone();
        m_phone.setModel(counterpathModel);
        m_phone.setDefaults(new DeviceDefaults());
    }

    public void testGetFileName() throws Exception {
        m_phone.setSerialNumber("0011AABB4455");
        assertEquals("0011AABB4455.ini", m_phone.getProfileFilename());
    }

    public void testGenerateCounterpathCMCEnterprise() throws Exception {
        PhoneTestDriver.supplyTestData(m_phone, true, true, false);

        MemoryProfileLocation location = TestHelper.setVelocityProfileGenerator(m_phone);

        m_phone.generateProfiles(location);

        String expected = IOUtils.toString(getClass().getResourceAsStream("cmc-enterprise.ini"));
        assertEquals(expected, location.toString());
    }

    public void testCounterpathLineDefaults() {
        DeviceDefaults defaults = new DeviceDefaults();
        defaults.setDomainManager(TestHelper.getTestDomainManager("example.org"));
        m_phone.setDefaults(defaults);

        m_line = m_phone.createLine();
        supplyUserData();
        m_line.setUser(m_user);

        CounterpathLineDefaults lineDefaults = new CounterpathPhone.CounterpathLineDefaults(m_line);

        PhoneContext phoneContextMock = EasyMock.createMock(PhoneContext.class);
        phoneContextMock.getPhoneDefaults();
        EasyMock.expectLastCall().andReturn(defaults).anyTimes();
        EasyMock.replay(phoneContextMock);

        m_phone.setPhoneContext(phoneContextMock);

        assertEquals("jsmit", lineDefaults.getUserName());
        assertEquals("John Smit", lineDefaults.getDisplayName());
        assertEquals("1234", lineDefaults.getPassword());
        assertEquals("example.org", lineDefaults.getDomain());
        assertEquals("101", lineDefaults.getVoicemailURL());
    }

    public void testCounterpathPhoneDefaults() {
        SpeedDial speedDial = new SpeedDial();
        supplyUserData();
        speedDial.setUser(m_user);

        DeviceDefaults defaults = new DeviceDefaults();
        defaults.setDomainManager(TestHelper.getTestDomainManager("example.org"));

        PhoneContext phoneContextMock = EasyMock.createMock(PhoneContext.class);
        phoneContextMock.getSpeedDial(m_phone);
        EasyMock.expectLastCall().andReturn(speedDial).anyTimes();
        phoneContextMock.getPhoneDefaults();
        EasyMock.expectLastCall().andReturn(defaults).anyTimes();
        EasyMock.replay(phoneContextMock);

        m_phone.setPhoneContext(phoneContextMock);

        CounterpathPhoneDefaults phoneDefaults = m_phone.new CounterpathPhoneDefaults(m_phone);

        assertEquals("sip:~~rl~C~jsmit@example.org", phoneDefaults.getWorkgroupSubscriptionAor());
    }

    private void supplyUserData() {
        m_user = new User();
        m_user.setUserName("jsmit");
        m_user.setFirstName("John");
        m_user.setLastName("Smit");
        m_user.setSipPassword("1234");
    }
}
