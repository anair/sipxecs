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
import org.sipfoundry.sipxconfig.admin.dialplan.config.ConfigFileType;

public class SipxRegistrarConfiguration extends SipxServiceConfiguration {

    private SipxService m_service;

    @Override
    public void generate(SipxService service) {
        m_service = service;
    }

    public ConfigFileType getType() {
        return ConfigFileType.REGISTRAR_CONFIG;
    }
    
    @Override
    protected VelocityContext setupContext() {
        VelocityContext context = super.setupContext();
        context.put("settings", m_service.getSettings());
        context.put("registrarService", m_service);
        return context;
    }
}
