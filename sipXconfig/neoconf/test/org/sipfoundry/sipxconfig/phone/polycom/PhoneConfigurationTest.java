/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.phone.polycom;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.commons.io.IOUtils;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.sipfoundry.sipxconfig.TestHelper;
import org.sipfoundry.sipxconfig.device.MemoryProfileLocation;
import org.sipfoundry.sipxconfig.device.ProfileGenerator;
import org.sipfoundry.sipxconfig.device.VelocityProfileGenerator;
import org.sipfoundry.sipxconfig.phone.PhoneTestDriver;

/**
 * Tests file phone.cfg generation
 */
public class PhoneConfigurationTest extends XMLTestCase {

    private PolycomPhone phone;
    private ProfileGenerator m_pg;
    private MemoryProfileLocation m_location;

    protected void setUp() throws Exception {
        XMLUnit.setIgnoreWhitespace(true);
        phone = new PolycomPhone();
        PolycomModel model = new PolycomModel();
        model.setMaxLineCount(6);
        phone.setModel(model);
        PhoneTestDriver.supplyTestData(phone);

        m_location = new MemoryProfileLocation();
        VelocityProfileGenerator pg = new VelocityProfileGenerator();
        pg.setVelocityEngine(TestHelper.getVelocityEngine());
        m_pg = pg;
    }

    public void testGenerateProfileVersion16() throws Exception {
        phone.setDeviceVersion(PolycomModel.VER_1_6);
        PhoneConfiguration cfg = new PhoneConfiguration(phone);
        m_pg.generate(m_location, cfg, null, "profile");

        InputStream expectedPhoneStream = getClass()
                .getResourceAsStream("expected-phone.cfg.xml");
        Reader expectedXml = new InputStreamReader(expectedPhoneStream);
        Reader generatedXml = m_location.getReader();

        Diff phoneDiff = new Diff(expectedXml, generatedXml);
        assertXMLEqual(phoneDiff, true);
        expectedPhoneStream.close();
    }

    /**
     * Test 2.x profile generation. It's slightly different since we are comparing generated XML
     * line by line. XML comparison is good enough but it's harder to see what parameters are
     * missing or wrong.
     */
    public void testGenerateProfileVersion20() throws Exception {
        PhoneConfiguration cfg = new PhoneConfiguration(phone);
        PhoneTestDriver.supplyVitalEmergencyData(phone);
        m_pg.generate(m_location, cfg, null, "profile");

        
        InputStream expectedPhoneStream = getClass().getResourceAsStream(
                "expected-phone-3.0.0.cfg.xml");
        assertEquals(IOUtils.toString(expectedPhoneStream), m_location.toString());
        expectedPhoneStream.close();
    }
}
