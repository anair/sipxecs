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

import org.sipfoundry.sipxconfig.IntegrationTestCase;
import org.sipfoundry.sipxconfig.admin.commserver.SipxReplicationContext;
import org.sipfoundry.sipxconfig.common.ApplicationInitializedEvent;
import org.sipfoundry.sipxconfig.common.CoreContext;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.service.ServiceConfigurator;
import org.sipfoundry.sipxconfig.service.SipxSaaService;
import org.sipfoundry.sipxconfig.service.SipxService;
import org.sipfoundry.sipxconfig.service.SipxServiceManager;
import org.sipfoundry.sipxconfig.setting.Group;
import org.sipfoundry.sipxconfig.setting.SettingDao;
import org.sipfoundry.sipxconfig.test.TestUtil;

import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

public class ReplicationTriggerTestIntegration extends IntegrationTestCase {

    private ReplicationTrigger m_trigger;
    private SipxReplicationContext m_originalSipxReplicationContext;
    private SettingDao m_dao;
    private CoreContext m_coreContext;
    private ServiceConfigurator m_originalServiceConfigurator;
    private SipxServiceManager m_originalSipxServiceManager;

    public void setReplicationTrigger(ReplicationTrigger trigger) {
        m_trigger = trigger;
    }

    public void setSettingDao(SettingDao dao) {
        m_dao = dao;
    }

    public void setCoreContext(CoreContext coreContext) {
        m_coreContext = coreContext;
    }

    public void setSipxReplicationContext(SipxReplicationContext sipxReplicationContext) {
        m_originalSipxReplicationContext = sipxReplicationContext;
    }

    public void setServiceConfigurator(ServiceConfigurator serviceConfigurator) {
        m_originalServiceConfigurator = serviceConfigurator;
    }

    public void setSipxServiceManager(SipxServiceManager sipxServiceManager) {
        m_originalSipxServiceManager = sipxServiceManager;
    }

    @Override
    protected void onTearDownAfterTransaction() throws Exception {
        // restore trigger state...
        m_trigger.setReplicationContext(m_originalSipxReplicationContext);
        m_trigger.setSipxServiceManager(m_originalSipxServiceManager);
        m_trigger.setServiceConfigurator(m_originalServiceConfigurator);
    }

    /**
     * Test that saving a user group in db actually triggers a replication
     */
    public void testNewUserGroup() throws Exception {
        SipxReplicationContext replicationContext = createStrictMock(SipxReplicationContext.class);
        replicationContext.generate(DataSet.PERMISSION);
        replicationContext.generate(DataSet.USER_LOCATION);
        replicationContext.generate(DataSet.CALLER_ALIAS);
        replay(replicationContext);
        m_trigger.setReplicationContext(replicationContext);

        Group g = new Group();
        g.setName("replicationTriggerTest");
        g.setResource(User.GROUP_RESOURCE_ID);
        m_dao.saveGroup(g);

        verify(replicationContext);
    }

    public void testUpdateUserGroup() throws Exception {
        loadDataSet("admin/commserver/imdb/UserGroupSeed.db.xml");

        SipxReplicationContext replicationContext = createStrictMock(SipxReplicationContext.class);
        replicationContext.generate(DataSet.PERMISSION);
        replicationContext.generate(DataSet.USER_LOCATION);
        replicationContext.generate(DataSet.CALLER_ALIAS);
        replay(replicationContext);

        m_trigger.setReplicationContext(replicationContext);
        Group g = m_dao.getGroup(new Integer(1000));
        m_dao.saveGroup(g);

        verify(replicationContext);
    }

    public void testReplicateSipxSaaConfig() throws Exception {
        loadDataSet("common/UserGroupSeed.db.xml");

        SipxReplicationContext replicationContext = createStrictMock(SipxReplicationContext.class);
        replicationContext.generateAll();
        replay(replicationContext);
        m_trigger.setReplicationContext(replicationContext);

        ServiceConfigurator serviceConfigurator = createStrictMock(ServiceConfigurator.class);
        serviceConfigurator.replicateServiceConfig(isA(SipxService.class));
        replay(serviceConfigurator);
        m_trigger.setServiceConfigurator(serviceConfigurator);

        SipxSaaService sipxSaaService = new SipxSaaService();
        sipxSaaService.setBeanId(SipxSaaService.BEAN_ID);
        SipxServiceManager sipxServiceManager = TestUtil.getMockSipxServiceManager(true, sipxSaaService);
        m_trigger.setSipxServiceManager(sipxServiceManager);

        User testUser = m_coreContext.loadUser(1001);
        testUser.setIsShared(true);
        m_coreContext.saveUser(testUser);

        verify(replicationContext, serviceConfigurator);
    }

    /**
     * Test that replication happens at app startup if the replicateOnStartup property is set
     */
    public void testReplicateOnStartup() throws Exception {
        SipxReplicationContext replicationContext = createStrictMock(SipxReplicationContext.class);
        replicationContext.generateAll();
        replay(replicationContext);
        m_trigger.setReplicationContext(replicationContext);

        m_trigger.setReplicateOnStartup(true);
        m_trigger.onApplicationEvent(new ApplicationInitializedEvent(new Object()));

        verify(replicationContext);
    }

    /**
     * Test that replication doesn't happen at app startup if the replicateOnStartup property is
     * off
     */
    public void testNoReplicateOnStartup() throws Exception {
        SipxReplicationContext replicationContext = createStrictMock(SipxReplicationContext.class);
        replay(replicationContext);
        m_trigger.setReplicationContext(replicationContext);

        m_trigger.setReplicateOnStartup(false);
        m_trigger.onApplicationEvent(new ApplicationInitializedEvent(new Object()));

        verify(replicationContext);
    }
}
