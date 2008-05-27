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

import java.util.Arrays;
import java.util.Collections;

import org.custommonkey.xmlunit.XMLTestCase;
import org.dom4j.Document;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.sipfoundry.sipxconfig.TestHelper;
import org.sipfoundry.sipxconfig.XmlUnitHelper;
import org.sipfoundry.sipxconfig.admin.callgroup.CallGroup;
import org.sipfoundry.sipxconfig.admin.callgroup.CallGroupContext;
import org.sipfoundry.sipxconfig.common.CoreContext;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.permission.PermissionManagerImpl;
import org.sipfoundry.sipxconfig.permission.PermissionName;
import org.sipfoundry.sipxconfig.setting.Group;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

public class PermissionsTest extends XMLTestCase {

    public void testGenerateEmpty() throws Exception {
        PermissionManagerImpl pm = new PermissionManagerImpl();
        pm.setModelFilesContext(TestHelper.getModelFilesContext());

        User testUser = new User();
        testUser.setPermissionManager(pm);

        CoreContext coreContext = createMock(CoreContext.class);
        coreContext.getDomainName();
        expectLastCall().andReturn("host.company.com");
        coreContext.loadUsers();
        expectLastCall().andReturn(Collections.EMPTY_LIST);
        coreContext.newUser();
        expectLastCall().andReturn(testUser).anyTimes();

        CallGroupContext callGroupContext = createMock(CallGroupContext.class);
        callGroupContext.getCallGroups();
        expectLastCall().andReturn(Collections.EMPTY_LIST);

        replay(coreContext, callGroupContext);

        Permissions permissions = new Permissions();
        permissions.setCoreContext(coreContext);
        permissions.setCallGroupContext(callGroupContext);

        Document document = permissions.generate();

        org.w3c.dom.Document domDoc = XmlUnitHelper.getDomDoc(document);
        assertXpathEvaluatesTo("permission", "/items/@type", domDoc);
        assertXpathExists("/items/item", domDoc);
        // 5 permissions per special user
        assertXpathEvaluatesTo("sip:~~id~park@host.company.com", "/items/item/identity", domDoc);
        assertXpathEvaluatesTo("sip:~~id~park@host.company.com", "/items/item[5]/identity",
                domDoc);
        assertXpathEvaluatesTo("sip:~~id~media@host.company.com", "/items/item[6]/identity",
                domDoc);
        assertXpathEvaluatesTo("sip:~~id~media@host.company.com", "/items/item[10]/identity",
                domDoc);
        assertXpathEvaluatesTo("sip:~~id~acd@host.company.com", "/items/item[11]/identity",
                domDoc);
        assertXpathEvaluatesTo("sip:~~id~acd@host.company.com", "/items/item[15]/identity",
                domDoc);
        assertXpathEvaluatesTo("sip:~~id~config@host.company.com", "/items/item[16]/identity",
                domDoc);
        assertXpathEvaluatesTo("sip:~~id~config@host.company.com", "/items/item[20]/identity",
                domDoc);
        assertXpathNotExists("/items/item[21]", domDoc);

        verify(coreContext, callGroupContext);
    }

    public void testCallGroupPerms() throws Exception {
        PermissionManagerImpl pm = new PermissionManagerImpl();
        pm.setModelFilesContext(TestHelper.getModelFilesContext());

        User testUser = new User();
        testUser.setPermissionManager(pm);

        CoreContext coreContext = createMock(CoreContext.class);
        coreContext.getDomainName();
        expectLastCall().andReturn("host.company.com");
        coreContext.loadUsers();
        expectLastCall().andReturn(Collections.EMPTY_LIST);
        coreContext.newUser();
        expectLastCall().andReturn(testUser).anyTimes();

        CallGroup callGroup1 = new CallGroup();
        callGroup1.setName("sales");
        callGroup1.setEnabled(true);
        CallGroup callGroup2 = new CallGroup();
        callGroup2.setName("marketing");
        callGroup2.setEnabled(true);
        CallGroup callGroup3 = new CallGroup();
        callGroup3.setName("disabled");

        CallGroupContext callGroupContext = createMock(CallGroupContext.class);
        callGroupContext.getCallGroups();
        expectLastCall().andReturn(Arrays.asList(callGroup1, callGroup2, callGroup3));

        replay(coreContext, callGroupContext);

        Permissions permissions = new Permissions();
        permissions.setCoreContext(coreContext);
        permissions.setCallGroupContext(callGroupContext);

        Document document = permissions.generate();

        org.w3c.dom.Document domDoc = XmlUnitHelper.getDomDoc(document);

        // 5 permissions per special user - 4 special users == 20
        assertXpathExists("/items/item[21]", domDoc);
        assertXpathEvaluatesTo("sip:sales@host.company.com", "/items/item[21]/identity", domDoc);
        assertXpathEvaluatesTo("sip:sales@host.company.com", "/items/item[25]/identity", domDoc);
        assertXpathEvaluatesTo("sip:marketing@host.company.com", "/items/item[26]/identity",
                domDoc);
        assertXpathEvaluatesTo("sip:marketing@host.company.com", "/items/item[30]/identity",
                domDoc);

        verify(coreContext, callGroupContext);
    }

    public void testAddUser() throws Exception {
        Document document = DocumentFactory.getInstance().createDocument();
        Element items = document.addElement("items");

        PermissionManagerImpl pm = new PermissionManagerImpl();
        pm.setModelFilesContext(TestHelper.getModelFilesContext());

        User user = new User();
        user.setPermissionManager(pm);

        Group g = new Group();
        PermissionName.INTERNATIONAL_DIALING.setEnabled(g, false);
        PermissionName.LONG_DISTANCE_DIALING.setEnabled(g, false);
        PermissionName.TOLL_FREE_DIALING.setEnabled(g, false);
        PermissionName.LOCAL_DIALING.setEnabled(g, true);
        PermissionName.SIPX_VOICEMAIL.setEnabled(g, false);
        PermissionName.EXCHANGE_VOICEMAIL.setEnabled(g, true);

        user.addGroup(g);
        user.setUserName("goober");

        Permissions permissions = new Permissions();
        permissions.addUser(items, user, "sipx.sipfoundry.org");

        org.w3c.dom.Document domDoc = XmlUnitHelper.getDomDoc(document);
        assertXpathEvaluatesTo("sip:goober@sipx.sipfoundry.org", "/items/item/identity", domDoc);
        assertXpathEvaluatesTo("LocalDialing", "/items/item/permission", domDoc);

        assertXpathEvaluatesTo("sip:goober@sipx.sipfoundry.org", "/items/item[4]/identity",
                domDoc);
        assertXpathEvaluatesTo("ExchangeUMVoicemailServer", "/items/item[4]/permission", domDoc);

        assertXpathEvaluatesTo("sip:~~vm~goober@sipx.sipfoundry.org", "/items/item[5]/identity",
                domDoc);
        assertXpathEvaluatesTo("ExchangeUMVoicemailServer", "/items/item[5]/permission", domDoc);
    }
}
