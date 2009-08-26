/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 *
 */
package org.sipfoundry.sipxconfig.phone.gtek_aq;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.sipfoundry.sipxconfig.common.CoreContext;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.phone.PhoneContext;
import org.sipfoundry.sipxconfig.speeddial.Button;
import org.sipfoundry.sipxconfig.speeddial.SpeedDial;

import junit.framework.TestCase;

import org.apache.commons.io.IOUtils;
import org.sipfoundry.sipxconfig.TestHelper;
import org.sipfoundry.sipxconfig.device.MemoryProfileLocation;
import org.sipfoundry.sipxconfig.phone.PhoneModel;
import org.sipfoundry.sipxconfig.phone.PhoneTestDriver;

public class GtekAquiferPhoneTest extends TestCase {

    public void testGenerateTypicalProfile() throws Exception {
        GtekAquiferPhone phone = new GtekAquiferPhone();
        PhoneModel model = new PhoneModel("gtek-aq");
        model.setProfileTemplate("gtek-aq/config.vm");
        phone.setModel(model);
        // call this to inject dummy data
        PhoneTestDriver.supplyTestData(phone);
        MemoryProfileLocation location = TestHelper.setVelocityProfileGenerator(phone);

        phone.generateProfiles(location);

        InputStream expectedProfile = getClass().getResourceAsStream("expected-config");
        String expected = IOUtils.toString(expectedProfile);
        expectedProfile.close();

        String actual = location.toString("0004f200e06b.dat");
        // normalize config version
        actual = actual.replaceFirst("\nAutoConfigVersion=\\d+", "\nAutoConfigVersion=1");

        assertEquals(expected, actual);
    }

    public void testGenerateProfileWithBlfSpeeddial() throws Exception {
        GtekAquiferPhone phone = new GtekAquiferPhone();
        PhoneModel model = new PhoneModel("gtek-aq");
        model.setProfileTemplate("gtek-aq/config.vm");
        phone.setModel(model);

        MemoryProfileLocation location = TestHelper.setVelocityProfileGenerator(phone);

        User user1 = new User() {
            @Override
            public Integer getId() {
                return 115;
            }
        };
        user1.setUserName("juser");
        user1.setFirstName("Joe");
        user1.setLastName("User");
        user1.setSipPassword("1234");
        PhoneTestDriver.supplyTestData(phone, Collections.singletonList(user1));

        User user2 = new User();
        User user3 = new User();
        user2.setUserName("Bill");
        user3.setUserName("Bob");

        Set user2Aliases = new LinkedHashSet(); // use LinkedHashSet for stable ordering
        user2Aliases.add("201");
        user2.setAliases(user2Aliases);

        Set user3Aliases = new LinkedHashSet(); // use LinkedHashSet for stable ordering
        user3Aliases.add("202");
        user3.setAliases(user3Aliases);

        Button[] buttons = new Button[] {
            new Button("Bill User", "201"), new Button("Bob User", "202")
        };

        SpeedDial sp = new SpeedDial();
        sp.setButtons(Arrays.asList(buttons));
        buttons[1].setBlf(true);
        sp.setUser(user1);

        IMocksControl coreContextControl = EasyMock.createNiceControl();
        CoreContext coreContext = coreContextControl.createMock(CoreContext.class);

        coreContext.loadUserByAlias("201");
        EasyMock.expectLastCall().andReturn(user2);
        coreContext.loadUserByAlias("202");
        EasyMock.expectLastCall().andReturn(user3);
        coreContextControl.replay();

        IMocksControl phoneContextControl = EasyMock.createNiceControl();
        PhoneContext phoneContext = phoneContextControl.createMock(PhoneContext.class);
        PhoneTestDriver.supplyVitalTestData(phoneContextControl, phoneContext, phone);

        phoneContext.getSpeedDial(phone);
        phoneContextControl.andReturn(sp).anyTimes();
        phoneContextControl.replay();

        phone.getProfileTypes()[0].generate(phone, location);
        InputStream expectedProfile = getClass().getResourceAsStream("expected-speeddial-config");
        assertNotNull(expectedProfile);
        String expected = IOUtils.toString(expectedProfile);
        expectedProfile.close();

        String actual = location.toString("0004f200e06b.dat");
        // normalize config version
        actual = actual.replaceFirst("\nAutoConfigVersion=\\d+", "\nAutoConfigVersion=1");

        assertEquals(expected, actual);
    }
}
