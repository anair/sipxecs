/*
 * 
 * 
 * Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.service;

import org.apache.velocity.VelocityContext;
import org.sipfoundry.sipxconfig.admin.commserver.Location;

public class SipxRegistrarConfiguration extends SipxServiceConfiguration {

    private SipxService m_service;

    @Override
    public void generate(SipxService service) {
        m_service = service;
    }

    @Override
    protected VelocityContext setupContext(Location location) {
        VelocityContext context = super.setupContext(null);
        if (location != null) {
            context.put("location", location);
        }
        context.put("settings", m_service.getSettings());
        context.put("registrarService", m_service);
        
        String domainAliases = m_service.getSettings().getSetting("domain/SIP_REGISTRAR_DOMAIN_ALIASES").getValue();
        
        return context;
    }
}
