/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.site.admin;

import java.util.List;

import org.apache.tapestry.BaseComponent;
import org.apache.tapestry.IRequestCycle;
import org.apache.tapestry.annotations.InjectObject;
import org.apache.tapestry.annotations.Parameter;
import org.apache.tapestry.callback.ICallback;
import org.sipfoundry.sipxconfig.admin.commserver.Location;
import org.sipfoundry.sipxconfig.admin.commserver.LocationsManager;
import org.sipfoundry.sipxconfig.admin.monitoring.MRTGTarget;
import org.sipfoundry.sipxconfig.admin.monitoring.MonitoringContext;
import org.sipfoundry.sipxconfig.components.SelectMap;

public abstract class MonitoringTargetsConfigurationPanel extends BaseComponent {

    @InjectObject(value = "spring:locationsManager")
    public abstract LocationsManager getLocationsManager();

    @InjectObject(value = "spring:monitoringContext")
    public abstract MonitoringContext getMonitoringContext();

    @Parameter
    public abstract ICallback getCallback();

    @Parameter(required = true)
    public abstract Location getLocationBean();

    public abstract void setLocationBean(Location location);

    public abstract void setTargets(List<MRTGTarget> targets);

    public abstract List<MRTGTarget> getTargets();

    public abstract String getHost();

    public abstract void setHost(String host);

    public abstract void setSelections(SelectMap selections);

    public abstract SelectMap getSelections();

    public void prepareForRender(IRequestCycle cycle) {
        setHost(getLocationBean().getFqdn());
        List<MRTGTarget> targets = getMonitoringContext().getTargetsFromTemplate();
        setTargets(targets);
        List<MRTGTarget> selectedTargets = getMonitoringContext().getTargetsForHost(getHost());
        SelectMap selections = new SelectMap();
        for (MRTGTarget selectedTarget : selectedTargets) {
            selections.setSelected(selectedTarget.getTitle(), true);
        }
        setSelections(selections);

        super.prepareForRender(cycle);
    }
}
