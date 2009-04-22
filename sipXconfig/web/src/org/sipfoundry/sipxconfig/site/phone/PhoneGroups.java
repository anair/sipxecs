/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.site.phone;

import java.util.List;
import java.util.Map;

import org.apache.tapestry.IPage;
import org.apache.tapestry.IRequestCycle;
import org.apache.tapestry.event.PageBeginRenderListener;
import org.apache.tapestry.event.PageEvent;
import org.apache.tapestry.html.BasePage;
import org.sipfoundry.sipxconfig.phone.Phone;
import org.sipfoundry.sipxconfig.phone.PhoneContext;
import org.sipfoundry.sipxconfig.setting.SettingDao;
import org.sipfoundry.sipxconfig.site.setting.EditGroup;

public abstract class PhoneGroups extends BasePage implements PageBeginRenderListener {

    public static final String PAGE = "phone/PhoneGroups";

    public abstract void setGroups(List groups);

    public abstract List getGroups();

    public abstract PhoneContext getPhoneContext();

    public abstract SettingDao getSettingContext();

    public IPage addGroup(IRequestCycle cycle) {
        EditGroup page = (EditGroup) cycle.getPage(EditGroup.PAGE);
        page.newGroup("phone", PAGE);
        return page;
    }

    public Map getMemberCounts() {
        Map memberCount = getSettingContext().getGroupMemberCountIndexedByGroupId(Phone.class);

        return memberCount;
    }

    public IPage editPhoneGroup(IRequestCycle cycle, Integer groupId) {
        PhoneModels page = (PhoneModels) cycle.getPage(PhoneModels.PAGE);
        page.setGroupId(groupId);
        return page;
    }

    public void pageBeginRender(PageEvent event_) {
        PhoneContext context = getPhoneContext();
        setGroups(context.getGroups());
    }

    public IPage showGroupMembers(IRequestCycle cycle, Integer groupId) {
        ManagePhones page = (ManagePhones) cycle.getPage(ManagePhones.PAGE);
        page.setGroupId(groupId);
        return page;
    }
}
