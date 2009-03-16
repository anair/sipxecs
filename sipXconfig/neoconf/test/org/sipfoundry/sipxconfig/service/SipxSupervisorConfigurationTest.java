/*
 * 
 * 
 * Copyright (C) 2009 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.service;

import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.createMock;

import org.sipfoundry.sipxconfig.TestHelper;

public class SipxSupervisorConfigurationTest extends SipxServiceTestBase {
    @Override
    protected void setUp() throws Exception {

    }
    
    public void testGenerateSipxSupervisor() throws Exception {
        SipxSupervisorConfiguration supervisorConf = new SipxSupervisorConfiguration();
        SipxSupervisorService supervisor = new SipxSupervisorService ();   
        supervisor.setLogLevel("DEBUG");
        
        SipxServiceManager serviceManager = createMock(SipxServiceManager.class);
        serviceManager.getServiceByBeanId(SipxSupervisorService.BEAN_ID);
        expectLastCall().andReturn(supervisor).atLeastOnce();
        replay(serviceManager);
        
        supervisorConf.setSipxServiceManager(serviceManager);        
        supervisorConf.setVelocityEngine(TestHelper.getVelocityEngine());
        supervisorConf.setTemplate("commserver/sipxsupervisor-config.vm");

        assertCorrectFileGeneration(supervisorConf, "expected-sipxsupervisor-config");
    }
}