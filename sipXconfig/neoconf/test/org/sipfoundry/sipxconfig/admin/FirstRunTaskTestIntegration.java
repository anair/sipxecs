/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.admin;

import java.util.Collection;

import org.sipfoundry.sipxconfig.IntegrationTestCase;
import org.sipfoundry.sipxconfig.admin.commserver.Location;
import org.sipfoundry.sipxconfig.admin.commserver.LocationsManager;
import org.sipfoundry.sipxconfig.admin.dialplan.sbc.SbcManagerImpl;
import org.sipfoundry.sipxconfig.common.AlarmContext;
import org.sipfoundry.sipxconfig.common.CoreContext;
import org.sipfoundry.sipxconfig.domain.Domain;
import org.sipfoundry.sipxconfig.domain.DomainManager;
import org.sipfoundry.sipxconfig.service.LocationSpecificService;
import org.sipfoundry.sipxconfig.service.ServiceConfigurator;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

public class FirstRunTaskTestIntegration extends IntegrationTestCase {
    private LocationsManager m_locationsManager;
    private FirstRunTask m_firstRun;
    private DomainManager m_domainManager;
    private CoreContext m_coreContext;
    private AdminContext m_adminContext;
    private ServiceConfigurator m_serviceConfigurator;
    private SbcManagerImpl m_sbcManagerImpl;

    public void setDomainManager(DomainManager domainManager) {
        m_domainManager = domainManager;
    }

    public void setAdminContext(AdminContext adminContext) {
        m_adminContext = adminContext;
    }

    public void setCoreContext(CoreContext coreContext) {
        m_coreContext = coreContext;
    }

    public void setLocationsManager(LocationsManager locationsManager) {
        m_locationsManager = locationsManager;
    }

    public void setFirstRun(FirstRunTask firstRun) {
        m_firstRun = firstRun;
    }

    public void setServiceConfigurator(ServiceConfigurator serviceConfigurator) {
        m_serviceConfigurator = serviceConfigurator;
    }

    public void setSbcManagerImpl(SbcManagerImpl sbcManagerImpl) {
        m_sbcManagerImpl = sbcManagerImpl;
    }

    @Override
    protected void onTearDownAfterTransaction() throws Exception {
        m_firstRun.setAdminContext(m_adminContext);
        m_firstRun.setDomainManager(m_domainManager);
        m_firstRun.setCoreContext(m_coreContext);
        m_firstRun.setServiceConfigurator(m_serviceConfigurator);
    }

    public void testEnableFirstRunServices() throws Exception {
        Domain domain = new Domain();
        domain.setName("example.org");
        DomainManager domainManager = createMock(DomainManager.class);
        domainManager.initializeDomain();
        expect(domainManager.getDomain()).andReturn(domain).anyTimes();

        AdminContext adminContext = createNiceMock(AdminContext.class);
        CoreContext coreContext = createNiceMock(CoreContext.class);
        AlarmContext alarmContext = createNiceMock(AlarmContext.class);

        replay(domainManager, adminContext, coreContext, alarmContext);

        m_sbcManagerImpl.setDomainManager(domainManager);
        m_firstRun.setDomainManager(domainManager);
        m_firstRun.setAdminContext(adminContext);
        m_firstRun.setCoreContext(coreContext);
        m_firstRun.setSbcManager(m_sbcManagerImpl);

        loadDataSetXml("admin/commserver/seedLocationsAndServices.xml");
        m_firstRun.setLocationsManager(m_locationsManager);

        ServiceConfigurator serviceConfigurator = createMock(ServiceConfigurator.class);
        serviceConfigurator.initLocations();
        Location[] locations = m_locationsManager.getLocations();
        for (Location location : locations) {
            serviceConfigurator.enforceRole(location);
        }

        replay(serviceConfigurator);

        m_firstRun.setServiceConfigurator(serviceConfigurator);
        m_firstRun.runTask();

        verify(domainManager, adminContext, coreContext, alarmContext, serviceConfigurator);

        Location primaryLocation = m_locationsManager.getPrimaryLocation();
        Collection<LocationSpecificService> servicesForPrimaryLocation = primaryLocation.getServices();
        assertFalse(servicesForPrimaryLocation.isEmpty());
        // auto-enabled bundles are set for primary location
        assertEquals(4, primaryLocation.getInstalledBundles().size());
    }
}
