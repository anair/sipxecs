/*
 *
 *
 * Copyright (C) 2009 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 */
package org.sipfoundry.sipxivr;

import java.io.File;
import java.io.IOException;

import org.sipfoundry.sipxivr.MailboxPreferences;
import org.sipfoundry.sipxivr.MailboxPreferencesReader;
import org.sipfoundry.sipxivr.MailboxPreferencesWriter;
import org.sipfoundry.sipxivr.MailboxPreferences.GreetingType;

import junit.framework.TestCase;

public class MailboxPreferencesTest extends TestCase {
    public void testMailboxPreferencesWriter() throws IOException {
        // Start with empty preferences
        MailboxPreferences prefs = new MailboxPreferences();
        String emptyXml = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "\n" +
            "<prefs>\n" +
            "  <activegreeting>none</activegreeting>\n" +
            "  <notification/>\n" +
            "</prefs>\n";

        MailboxPreferencesWriter mpw = new MailboxPreferencesWriter();
        File tempFile;
        tempFile = File.createTempFile("MailboxPreferencesTest", ".xml", new File("/tmp"));
        mpw.writeObject(prefs, tempFile);
        assertTrue(tempFile.exists());
        String contents = org.apache.commons.io.FileUtils.readFileToString(tempFile);
        assertEquals(emptyXml, contents);

        // Now set a greeting
        String standardXml = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "\n" +
            "<prefs>\n" +
            "  <activegreeting>standard</activegreeting>\n" +
            "  <notification/>\n" +
            "</prefs>\n";

        prefs.setActiveGreeting(GreetingType.STANDARD);
        tempFile.delete();
        mpw.writeObject(prefs, tempFile);
        assertTrue(tempFile.exists());
        contents = org.apache.commons.io.FileUtils.readFileToString(tempFile);
        assertEquals(standardXml, contents);

        // Now add an email address
        String emailXml = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "\n" +
            "<prefs>\n" +
            "  <activegreeting>extendedabsence</activegreeting>\n" +
            "  <notification>\n" +
            "    <contact type=\"email\" attachments=\"yes\">woof@dog</contact>\n" +
            "  </notification>\n" +
            "</prefs>\n";

        prefs.setActiveGreeting(GreetingType.EXTENDED_ABSENCE);
        prefs.setEmailAddress("woof@dog");
        prefs.setAttachVoicemailToEmail(true);
        tempFile.delete();
        mpw.writeObject(prefs, tempFile);
        assertTrue(tempFile.exists());
        contents = org.apache.commons.io.FileUtils.readFileToString(tempFile);
        assertEquals(emailXml, contents);

        // Now add a second email address
        String email2Xml = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "\n" +
            "<prefs>\n" +
            "  <activegreeting>extendedabsence</activegreeting>\n" +
            "  <notification>\n" +
            "    <contact type=\"email\" attachments=\"yes\">woof@dog</contact>\n" +
            "    <contact type=\"email\" attachments=\"no\">dog@woof</contact>\n" +
            "  </notification>\n" +
            "</prefs>\n";

        prefs.setActiveGreeting(GreetingType.EXTENDED_ABSENCE);
        prefs.setEmailAddress("woof@dog");
        prefs.setAttachVoicemailToEmail(true);
        prefs.setAlternateEmailAddress("dog@woof");
        prefs.setAttachVoicemailToAlternateEmail(false);
        tempFile.delete();
        mpw.writeObject(prefs, tempFile);
        assertTrue(tempFile.exists());
        contents = org.apache.commons.io.FileUtils.readFileToString(tempFile);
        assertEquals(email2Xml, contents);

        tempFile.delete();
    }
    public void testMailboxPreferencesReader() throws IOException {
        String xml = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "\n" +
            "<prefs>\n" +
            "  <activegreeting>none</activegreeting>\n" +
            "  <notification/>\n" +
            "</prefs>\n";

        File tempFile;
        tempFile = File.createTempFile("MailboxPreferencesTest", ".xml", new File("/tmp"));
        org.apache.commons.io.FileUtils.writeStringToFile(tempFile, xml);
        
        MailboxPreferencesReader mpr = new MailboxPreferencesReader();
        MailboxPreferences newPrefs = mpr.readObject(tempFile) ;
        assertNull(newPrefs.getEmailAddress());
        assertEquals(GreetingType.NONE, newPrefs.getActiveGreeting());

        xml = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "\n" +
            "<prefs>\n" +
            "  <activegreeting>standard</activegreeting>\n" +
            "  <notification>\n" +
            "    <contact>puppy@creature</contact>\n" +
            "  </notification>\n" +
            "</prefs>\n";
        org.apache.commons.io.FileUtils.writeStringToFile(tempFile, xml);
        newPrefs = mpr.readObject(tempFile) ;
        assertEquals(GreetingType.STANDARD, newPrefs.getActiveGreeting());
        assertEquals("puppy@creature", newPrefs.getEmailAddress());
        assertFalse(newPrefs.isAttachVoicemailToEmail());


        xml = 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "\n" +
            "<prefs>\n" +
            "  <activegreeting>outofoffice</activegreeting>\n" +
            "  <notification>\n" +
            "    <contact>puppy@creature</contact>\n" +
            "    <contact attachments=\"yes\">dog@creature</contact>\n" +
            "  </notification>\n" +
            "</prefs>\n";
        org.apache.commons.io.FileUtils.writeStringToFile(tempFile, xml);
        newPrefs = mpr.readObject(tempFile) ;
        assertEquals(GreetingType.OUT_OF_OFFICE, newPrefs.getActiveGreeting());
        assertEquals("puppy@creature", newPrefs.getEmailAddress());
        assertFalse(newPrefs.isAttachVoicemailToEmail());
        assertEquals("dog@creature", newPrefs.getAlternateEmailAddress());
        assertTrue(newPrefs.isAttachVoicemailToAlternateEmail());
        
        tempFile.delete();
    }
}
