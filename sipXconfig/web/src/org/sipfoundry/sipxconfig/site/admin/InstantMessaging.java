/*
 *
 *
 * Copyright (C) 2009 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.site.admin;

import org.apache.tapestry.annotations.Bean;
import org.apache.tapestry.annotations.InitialValue;
import org.apache.tapestry.annotations.Persist;
import org.apache.tapestry.html.BasePage;
import org.sipfoundry.sipxconfig.components.SipxValidationDelegate;

public abstract class InstantMessaging extends BasePage {
    public static final String PAGE = "admin/InstantMessaging";

    @Bean
    public abstract SipxValidationDelegate getValidator();

    @Persist
    @InitialValue(value = "literal:certificates")
    public abstract String getTab();
}
