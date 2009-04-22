/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.phone.aastra;

import java.util.List;
import java.io.IOException;
import java.util.Collections;

import junit.framework.TestCase;

import org.apache.commons.io.IOUtils;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.sipfoundry.sipxconfig.TestHelper;
import org.sipfoundry.sipxconfig.device.MemoryProfileLocation;
import org.sipfoundry.sipxconfig.device.ProfileGenerator;
import org.sipfoundry.sipxconfig.device.VelocityProfileGenerator;
import org.sipfoundry.sipxconfig.phonebook.PhonebookEntry;

public class AastraPhonebookTest extends TestCase {
    private ProfileGenerator m_pg;
    private MemoryProfileLocation m_location;

    protected void setUp() {
        m_location = new MemoryProfileLocation();
        VelocityProfileGenerator pg = new VelocityProfileGenerator();
        pg.setVelocityEngine(TestHelper.getVelocityEngine());
        m_pg = pg;
    }

    public void testPhonebook() throws IOException {
        IMocksControl phonebookEntryControl = EasyMock.createNiceControl();
        PhonebookEntry phonebookEntry = phonebookEntryControl.createMock(PhonebookEntry.class);
        phonebookEntry.getFirstName();
        phonebookEntryControl.andReturn("Ram");
        phonebookEntry.getLastName();
        phonebookEntryControl.andReturn("Jeo");
        phonebookEntry.getNumber();
        phonebookEntryControl.andReturn("303");
        phonebookEntryControl.replay();

        AastraPhonebook book = new AastraPhonebook(Collections.singleton(phonebookEntry));

        m_pg.generate(m_location, book, null, "phonebook");

        List<String> list = IOUtils.readLines(m_location.getReader());
        assertEquals("Ram Jeo,303", list.get(23));

        phonebookEntryControl.verify();
    }

}
