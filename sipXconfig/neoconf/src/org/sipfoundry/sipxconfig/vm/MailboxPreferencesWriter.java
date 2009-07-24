/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.vm;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.VelocityContext;
import org.sipfoundry.sipxconfig.vm.MailboxManagerImpl.YesNo;

public class MailboxPreferencesWriter extends XmlWriterImpl<MailboxPreferences> {
    public MailboxPreferencesWriter() {
        setTemplate("mailbox/mailboxprefs.vm");
    }

    @Override
    protected void addContext(VelocityContext context, MailboxPreferences object) {
        boolean altEmail = (object != null && !StringUtils.isBlank(object.getAlternateEmailAddress()));
        context.put("hasAlternateEmailAddress", altEmail);
        context.put("preferences", object);
        context.put("yesNo", new YesNo());
        if (object != null) {
            context.put("ifEmailServer", object.ifEmailServer());
            String pwd = object.getEmailPassword() != null ? object.getEmailPassword() : "";
            String encodedPwd = new String(Base64.encodeBase64(pwd.getBytes()));
            context.put("pwd", encodedPwd);
        }
    }
}
