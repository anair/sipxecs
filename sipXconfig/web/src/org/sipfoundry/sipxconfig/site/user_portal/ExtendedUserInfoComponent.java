/*
 *
 *
 * Copyright (C) 2009 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 *
 */
package org.sipfoundry.sipxconfig.site.user_portal;

import org.apache.tapestry.BaseComponent;
import org.apache.tapestry.annotations.ComponentClass;
import org.apache.tapestry.annotations.InjectObject;
import org.apache.tapestry.annotations.Parameter;
import org.apache.tapestry.event.PageBeginRenderListener;
import org.apache.tapestry.event.PageEvent;
import org.sipfoundry.sipxconfig.common.CoreContext;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.components.TapestryContext;
import org.sipfoundry.sipxconfig.components.TapestryUtils;
import org.sipfoundry.sipxconfig.phonebook.AddressBookEntry;
import org.sipfoundry.sipxconfig.site.UserSession;

@ComponentClass(allowBody = false, allowInformalParameters = true)
public abstract class ExtendedUserInfoComponent extends BaseComponent implements PageBeginRenderListener {

    @InjectObject(value = "spring:tapestry")
    public abstract TapestryContext getTapestry();

    @InjectObject(value = "spring:coreContext")
    public abstract CoreContext getCoreContext();

    @Parameter(required = true)
    public abstract User getUser();

    @Parameter(required = true)
    public abstract UserSession getUserSession();

    public abstract AddressBookEntry getAddressBookEntry();

    public abstract void setAddressBookEntry(AddressBookEntry abe);

    public void pageBeginRender(PageEvent event) {
        if (null == getAddressBookEntry()) {
            AddressBookEntry abe = getUser().getAddressBookEntry();
            if (abe == null) {
                abe = new AddressBookEntry();
            }
            setAddressBookEntry(abe);
        }
    }

    public void commit() {
        if (!TapestryUtils.isValid(this)) {
            return;
        }

        User user = getUser();
        user.setAddressBookEntry(getAddressBookEntry());
        getCoreContext().saveUser(user);
    }
}
