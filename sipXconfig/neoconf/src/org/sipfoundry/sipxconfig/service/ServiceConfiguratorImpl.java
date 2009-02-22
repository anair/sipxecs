/*
 *
 *
 * Copyright (C) 2009 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 *
 */
package org.sipfoundry.sipxconfig.service;

import java.util.List;

import static java.util.Collections.singleton;

import org.sipfoundry.sipxconfig.admin.commserver.Location;
import org.sipfoundry.sipxconfig.admin.commserver.LocationStatus;
import org.sipfoundry.sipxconfig.admin.commserver.SipxProcessContext;
import org.sipfoundry.sipxconfig.admin.commserver.SipxReplicationContext;
import org.springframework.beans.factory.annotation.Required;

import static org.sipfoundry.sipxconfig.admin.commserver.SipxProcessContext.Command.START;
import static org.sipfoundry.sipxconfig.admin.commserver.SipxProcessContext.Command.STOP;

public class ServiceConfiguratorImpl implements ServiceConfigurator {
    private SipxReplicationContext m_replicationContext;

    private SipxProcessContext m_sipxProcessContext;

    private ConfigVersionManager m_configVersionManager;

    public void startService(Location location, SipxService service) {
        replicateServiceConfig(location, service);
        m_sipxProcessContext.manageServices(location, singleton(service), START);
    }

    public void replicateServiceConfig(SipxService service) {
        List<SipxServiceConfiguration> configurations = service.getConfigurations();
        for (SipxServiceConfiguration configuration : configurations) {
            m_replicationContext.replicate(configuration);
        }
    }

    /**
     * Replicates the configuration for the service and sets configuration stamp once the
     * replication succeeds.
     */
    public void replicateServiceConfig(Location location, SipxService service) {
        if (!location.isRegistered()) {
            return;
        }
        List<SipxServiceConfiguration> configurations = service.getConfigurations();
        for (SipxServiceConfiguration configuration : configurations) {
            m_replicationContext.replicate(location, configuration);
        }
        m_configVersionManager.setConfigVersion(service, location);
    }

    /**
     * Stops services that need to be stopped, replicates the configuration for the ones that need
     * to be started, set configuration stamps for newly replicated configuration and finally
     * starts the services.
     */
    public void enforceRole(Location location) {
        if (!location.isRegistered()) {
            return;
        }
        LocationStatus locationStatus = m_sipxProcessContext.getLocationStatus(location);
        m_sipxProcessContext.manageServices(location, locationStatus.getToBeStopped(), STOP);
        for (SipxService service : locationStatus.getToBeStarted()) {
            replicateServiceConfig(location, service);
        }
        m_sipxProcessContext.manageServices(location, locationStatus.getToBeStarted(), START);
    }

    @Required
    public void setSipxReplicationContext(SipxReplicationContext replicationContext) {
        m_replicationContext = replicationContext;
    }

    @Required
    public void setSipxProcessContext(SipxProcessContext sipxProcessContext) {
        m_sipxProcessContext = sipxProcessContext;
    }

    @Required
    public void setConfigVersionManager(ConfigVersionManager configVersionManager) {
        m_configVersionManager = configVersionManager;
    }
}
