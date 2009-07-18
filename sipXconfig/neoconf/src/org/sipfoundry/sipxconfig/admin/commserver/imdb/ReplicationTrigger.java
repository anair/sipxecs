/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.admin.commserver.imdb;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sipfoundry.sipxconfig.admin.commserver.SipxReplicationContext;
import org.sipfoundry.sipxconfig.common.ApplicationInitializedEvent;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.common.event.DaoEventListener;
import org.sipfoundry.sipxconfig.service.ServiceConfigurator;
import org.sipfoundry.sipxconfig.service.SipxSaaService;
import org.sipfoundry.sipxconfig.service.SipxService;
import org.sipfoundry.sipxconfig.service.SipxServiceManager;
import org.sipfoundry.sipxconfig.setting.Group;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

public class ReplicationTrigger implements ApplicationListener, DaoEventListener {
    protected static final Log LOG = LogFactory.getLog(ReplicationTrigger.class);

    private SipxReplicationContext m_replicationContext;
    private SipxServiceManager m_sipxServiceManager;
    private ServiceConfigurator m_serviceConfigurator;

    /** no replication at start-up by default */
    private boolean m_replicateOnStartup;

    @Required
    public void setReplicationContext(SipxReplicationContext replicationContext) {
        m_replicationContext = replicationContext;
    }

    @Required
    public void setSipxServiceManager(SipxServiceManager sipxServiceManager) {
        m_sipxServiceManager = sipxServiceManager;
    }

    @Required
    public void setServiceConfigurator(ServiceConfigurator serviceConfigurator) {
        m_serviceConfigurator = serviceConfigurator;
    }

    public boolean isReplicateOnStartup() {
        return m_replicateOnStartup;
    }

    public void setReplicateOnStartup(boolean replicateOnStartup) {
        m_replicateOnStartup = replicateOnStartup;
    }

    public void onSave(Object entity) {
        onSaveOrDelete(entity);
    }

    public void onDelete(Object entity) {
        onSaveOrDelete(entity);
    }

    /**
     * Override ApplicationListener.onApplicationEvent so we can handle events. Perform data
     * replication every time the app starts up.
     */
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationInitializedEvent && isReplicateOnStartup()) {
            LOG.info("Replicating all data sets after application has initialized");
            m_replicationContext.generateAll();
        }
    }

    void onSaveOrDelete(Object entity) {
        Class c = entity.getClass();
        if (Group.class.equals(c)) {
            Group group = (Group) entity;
            if ("user".equals(group.getResource())) {
                m_replicationContext.generate(DataSet.PERMISSION);
                m_replicationContext.generate(DataSet.USER_LOCATION);
                m_replicationContext.generate(DataSet.CALLER_ALIAS);
            }
        } else if (User.class.equals(c)) {
            m_replicationContext.generateAll();
            SipxService sipxSaaService = m_sipxServiceManager.getServiceByBeanId(SipxSaaService.BEAN_ID);
            m_serviceConfigurator.replicateServiceConfig(sipxSaaService);
        }
    }
}
