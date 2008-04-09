/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.components;

import junit.framework.TestCase;

import org.apache.tapestry.IPage;
import org.apache.tapestry.IRequestCycle;
import org.apache.tapestry.PageRedirectException;
import org.apache.tapestry.callback.ICallback;
import org.apache.tapestry.components.Block;
import org.apache.tapestry.engine.IEngineService;
import org.apache.tapestry.event.PageEvent;
import org.apache.tapestry.web.WebRequest;
import org.easymock.EasyMock;
import org.sipfoundry.sipxconfig.common.CoreContext;
import org.sipfoundry.sipxconfig.common.CoreContextImpl;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.common.VersionInfo;
import org.sipfoundry.sipxconfig.site.ApplicationLifecycle;
import org.sipfoundry.sipxconfig.site.ApplicationLifecycleImpl;
import org.sipfoundry.sipxconfig.site.UserSession;
import org.sipfoundry.sipxconfig.site.skin.SkinControl;

public class BorderTest extends TestCase {

    public void testLogin() {
        IPage dummyPage = EasyMock.createNiceControl().createMock(IPage.class);
        Border restricted = new MockBorder(true, true, new UserSession());
        try {
            restricted.pageValidate(new PageEvent(dummyPage, null));
            fail("should redirect");
        } catch (PageRedirectException e) {
            assertEquals("LoginPage", e.getTargetPageName());
        }
    }

    public void testLoginNotRequired() {
        Border nologin = new MockBorder(true, false, new UserSession());
        nologin.pageValidate(null);
    }

    public void testRestricted() {
        Border restricted = new MockBorder(true, true, new MockUserSession(false));

        try {
            restricted.pageValidate(null);
            fail("should redirect to login page");
        } catch (PageRedirectException e) {
            assertEquals("Home", e.getTargetPageName());
        }
    }

    public void testRestrictedAdmin() {
        Border restricted = new MockBorder(true, true, new MockUserSession(true));

        try {
            restricted.pageValidate(null);
        } catch (PageRedirectException e) {
            fail("unexpected expected");
        }
    }

    public void testUnrestricted() {
        Border unrestricted = new MockBorder(false, true, new MockUserSession(false));

        try {
            unrestricted.pageValidate(null);
        } catch (PageRedirectException e) {
            fail("unexpected expected");
        }
    }

    public void testUnrestrictedAdmin() {
        Border unrestricted = new MockBorder(false, true, new MockUserSession(true));

        try {
            unrestricted.pageValidate(null);
        } catch (PageRedirectException e) {
            fail("unexpected expected");
        }
    }

    private static class MockUserSession extends UserSession {
        private final boolean m_admin;

        MockUserSession(boolean admin) {
            m_admin = admin;
        }

        public Integer getUserId() {
            return new Integer(5);
        }

        public boolean isAdmin() {
            return m_admin;
        }

    }

    private static class MockBorder extends Border {
        private final boolean m_restricted;
        private final boolean m_loginRequired;
        private final UserSession m_userSession;
        
        MockBorder(boolean restricted, boolean loginRequired, UserSession userSession) {
            m_restricted = restricted;
            m_loginRequired = loginRequired;
            m_userSession = userSession;
        }

        public void setNavigationBlock(Block block) {

        }

        public Block getNavigationBlock() {
            return null;
        }

        public boolean isLoginRequired() {
            return m_loginRequired;
        }

        public boolean isRestricted() {
            return m_restricted;
        }

        public UserSession getUserSession() {
            return m_userSession;
        }

        public ICallback getLoginCallback() {
            return null;
        }

        public ApplicationLifecycle getApplicationLifecycle() {
            return new ApplicationLifecycleImpl();
        }

        protected void redirectToLogin(IPage page, IRequestCycle cycle) {
            throw new PageRedirectException("LoginPage");
        }

        public IEngineService getRestartService() {
            return null;
        }

        public CoreContext getCoreContext() {
            return new CoreContextImpl() {
                public int getUsersCount() {
                    return 1;
                }

                public User newUser() {
                    return null;
                }

            };
        }

        public SkinControl getSkin() {
            return null;
        }
        public String getClientId() {
            return m_clientId;
        }

        public void setClientId(String id) {
            m_clientId = id;
        }

        public String getBaseUrl() {
            return null;
        }

        public String getHelpLink(Integer... versionIds) {
            return null;
        }

        public WebRequest getRequest() {
            return null;
        }

        public TapestryContext getTapestry() {
            return null;
        }

        public boolean getUseDojo() {
            return false;
        }

        public VersionInfo getVersionInfo() {
            return null;
        }

        public void setBaseUrl(String baseUrl) {
        }
    }
}
