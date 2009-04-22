/*
 *
 *
 * Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 */
package org.sipfoundry.sipxconfig.service.freeswitch;

import org.sipfoundry.sipxconfig.common.CoreContext;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.domain.DomainManager;
import org.sipfoundry.sipxconfig.service.SipxFreeswitchService;
import org.sipfoundry.sipxconfig.service.SipxServiceManager;
import org.sipfoundry.sipxconfig.service.SipxServiceTestBase;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.sipfoundry.sipxconfig.common.SpecialUser.SpecialUserType.MEDIA_SERVER;

public class SofiaConfigurationTest extends SipxServiceTestBase {

    public void testWrite() throws Exception {
        SipxFreeswitchService service = new SipxFreeswitchService();
        service.setModelDir("freeswitch");
        service.setModelName("freeswitch.xml");
        initCommonAttributes(service);

        SipxServiceManager sipxServiceManager = createMock(SipxServiceManager.class);
        sipxServiceManager.getServiceByBeanId(SipxFreeswitchService.BEAN_ID);
        expectLastCall().andReturn(service);

        DomainManager domainManager = createMock(DomainManager.class);
        domainManager.getDomain();
        expectLastCall().andReturn(createDefaultDomain());
        domainManager.getAuthorizationRealm();
        expectLastCall().andReturn("realm.example.com");

        User user = new User();
        user.setSipPassword("1234");
        user.setUserName(MEDIA_SERVER.getUserName());

        CoreContext coreContext = createMock(CoreContext.class);
        coreContext.getSpecialUser(MEDIA_SERVER);
        expectLastCall().andReturn(user);

        replay(sipxServiceManager, domainManager, coreContext);

        SofiaConfiguration configuration = new SofiaConfiguration();
        configuration.setSipxServiceManager(sipxServiceManager);
        configuration.setTemplate("freeswitch/sofia.conf.xml.vm");
        configuration.setDomainManager(domainManager);
        configuration.setCoreContext(coreContext);

        assertCorrectFileGeneration(configuration, "sofia.conf.test.xml");

        verify(sipxServiceManager, domainManager, coreContext);
    }
}
