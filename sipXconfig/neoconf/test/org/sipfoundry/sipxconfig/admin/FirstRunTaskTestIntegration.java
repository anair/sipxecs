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
import org.sipfoundry.sipxconfig.common.AlarmContext;
import org.sipfoundry.sipxconfig.common.CoreContext;
import org.sipfoundry.sipxconfig.domain.DomainManager;
import org.sipfoundry.sipxconfig.service.LocationSpecificService;
import org.sipfoundry.sipxconfig.service.ServiceConfigurator;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

public class FirstRunTaskTestIntegration extends IntegrationTestCase {
    private LocationsManager m_locationsManager;
    private FirstRunTask m_firstRun;
    private DomainManager m_domainManager;
    private AlarmContext m_alarmContext;
    private CoreContext m_coreContext;
    private AdminContext m_adminContext;
    private ServiceConfigurator m_serviceConfigurator;

    public void setDomainManager(DomainManager domainManager) {
        m_domainManager = domainManager;
    }

    public void setAlarmContext(AlarmContext alarmContext) {
        m_alarmContext = alarmContext;
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

    @Override
    protected void onTearDownAfterTransaction() throws Exception {
        m_firstRun.setAdminContext(m_adminContext);
        m_firstRun.setAlarmContext(m_alarmContext);
        m_firstRun.setDomainManager(m_domainManager);
        m_firstRun.setCoreContext(m_coreContext);
        m_firstRun.setServiceConfigurator(m_serviceConfigurator);
    }

    public void testEnableFirstRunServices() throws Exception {
        DomainManager domainManager = createNiceMock(DomainManager.class);
        m_firstRun.setDomainManager(domainManager);
        AdminContext adminContext = createNiceMock(AdminContext.class);
        m_firstRun.setAdminContext(adminContext);
        CoreContext coreContext = createNiceMock(CoreContext.class);
        m_firstRun.setCoreContext(coreContext);
        AlarmContext alarmContext = createNiceMock(AlarmContext.class);
        m_firstRun.setAlarmContext(alarmContext);

        replay(domainManager, adminContext, coreContext, alarmContext);

        loadDataSetXml("admin/commserver/seedLocationsAndServices.xml");
        m_firstRun.setLocationsManager(m_locationsManager);

        ServiceConfigurator serviceConfigurator = createMock(ServiceConfigurator.class);
        serviceConfigurator.replicateDialPlans();
        Location[] locations = m_locationsManager.getLocations();
        for (Location location : locations) {
            serviceConfigurator.enforceRole(location);
        }

        replay(serviceConfigurator);

        m_firstRun.setServiceConfigurator(serviceConfigurator);
        m_firstRun.runTask();

        verify(serviceConfigurator);

        Location primaryLocation = m_locationsManager.getPrimaryLocation();
        Collection<LocationSpecificService> servicesForPrimaryLocation = primaryLocation.getServices();
        assertFalse(servicesForPrimaryLocation.isEmpty());
        // auto-enabled bundles are set for primary location
        assertEquals(3, primaryLocation.getInstalledBundles().size());
    }
}
