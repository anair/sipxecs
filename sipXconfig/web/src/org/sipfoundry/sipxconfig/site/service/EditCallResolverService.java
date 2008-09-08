/*
 *
 *
 * Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.site.service;

import org.sipfoundry.sipxconfig.service.SipxCallResolverService;
import org.sipfoundry.sipxconfig.service.SipxProxyService;
import org.sipfoundry.sipxconfig.service.SipxService;

public abstract class EditCallResolverService extends EditSipxService {
    public static final String PAGE = "service/EditCallResolverService";
    /*
     * Override apply method to manually replicate proxy service config that depends on
     * CALLRESOLVER_CALL_STATE_DB setting from call resolver service
     */
    public void apply() {
        super.apply();
        SipxService proxyService = getSipxServiceManager().getServiceByBeanId(SipxProxyService.BEAN_ID);
        getSipxServiceManager().replicateServiceConfig(proxyService);
    }

    @Override
    protected String getBeanId() {
        return SipxCallResolverService.BEAN_ID;
    }
}
