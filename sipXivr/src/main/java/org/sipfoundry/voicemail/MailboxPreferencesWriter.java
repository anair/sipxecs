/*
 * 
 * 
 * Copyright (C) 2009 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.voicemail;

import java.io.IOException;
import java.io.Writer;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;


public class MailboxPreferencesWriter extends XmlWriterImpl<MailboxPreferences> {
    static final Logger LOG = Logger.getLogger("org.sipfoundry.sipxivr");
    static final DocumentFactory FACTORY = DocumentFactory.getInstance();
    private MailboxPreferences m_prefs;
    
    @Override
    public void writeObject(MailboxPreferences prefs, Writer output) {
        m_prefs = prefs ;
        OutputFormat format = new OutputFormat();
        format.setNewlines(true);
        format.setIndent(true);
        XMLWriter xmlWriter = new XMLWriter(output, format);
        try {
            xmlWriter.write(getDocument());
        } catch (IOException e) {
            LOG.error("Error writing mailboxprefs.xml", e);
            throw new RuntimeException(e);
        }
    }

    private Document getDocument() {
        Document document = FACTORY.createDocument();
        QName prefsQ = FACTORY.createQName("prefs");
        Element prefsEl = document.addElement(prefsQ);
        prefsEl.addElement("activegreeting").setText(m_prefs.getActiveGreeting().getId());
        addNotification(prefsEl);
        return document ;
    }
    
    private void addNotification(Element prefsEl) {
        Element notificationEl = prefsEl.addElement("notification");
        if (m_prefs.getEmailAddress() != null) {
            addEmailContact(notificationEl, m_prefs.getEmailAddress(), 
                    m_prefs.isAttachVoicemailToEmail());
        }
        if (m_prefs.getAlternateEmailAddress() != null) {
            addEmailContact(notificationEl, m_prefs.getAlternateEmailAddress(), 
                    m_prefs.isAttachVoicemailToAlternateEmail());
        }
    }
    
    private void addEmailContact(Element notificationEl, String emailAddr, boolean attach) {
        Element contactEl = notificationEl.addElement("contact");
        contactEl.setText(emailAddr);
        contactEl.add(FACTORY.createAttribute(contactEl, "type", "email"));
        contactEl.add(FACTORY.createAttribute(contactEl, "attachments", attach?"yes":"no"));
    }
}
