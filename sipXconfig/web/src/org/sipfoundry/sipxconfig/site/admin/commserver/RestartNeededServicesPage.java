/*
 *
 *
 * Copyright (C) 2009 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.site.admin.commserver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tapestry.IAsset;
import org.apache.tapestry.annotations.Asset;
import org.apache.tapestry.annotations.Bean;
import org.apache.tapestry.annotations.InjectObject;
import org.apache.tapestry.event.PageBeginRenderListener;
import org.apache.tapestry.event.PageEvent;
import org.apache.tapestry.html.BasePage;
import org.sipfoundry.sipxconfig.admin.commserver.Location;
import org.sipfoundry.sipxconfig.admin.commserver.LocationsManager;
import org.sipfoundry.sipxconfig.admin.commserver.RestartNeededService;
import org.sipfoundry.sipxconfig.admin.commserver.SipxProcessContext;
import org.sipfoundry.sipxconfig.components.SelectMap;
import org.sipfoundry.sipxconfig.components.SipxValidationDelegate;
import org.sipfoundry.sipxconfig.service.SipxService;
import org.sipfoundry.sipxconfig.service.SipxServiceManager;

import static org.sipfoundry.sipxconfig.components.LocalizationUtils.getMessage;

public abstract class RestartNeededServicesPage extends BasePage implements PageBeginRenderListener {
    public static final Object PAGE = "admin/commserver/RestartNeededServicesPage";

    @InjectObject("spring:sipxProcessContext")
    public abstract SipxProcessContext getSipxProcessContext();

    @InjectObject("spring:locationsManager")
    public abstract LocationsManager getLocationsManager();

    @InjectObject("spring:sipxServiceManager")
    public abstract SipxServiceManager getSipxServiceManager();

    @Bean
    public abstract SipxValidationDelegate getValidator();

    @Bean
    public abstract SelectMap getSelections();

    @Asset("/images/server.png")
    public abstract IAsset getServerIcon();

    @Asset("/images/service_restart.png")
    public abstract IAsset getRestartIcon();

    public abstract RestartNeededService getCurrentRow();

    public abstract Collection<RestartNeededService> getRestartNeededServices();

    public abstract void setRestartNeededServices(Collection<RestartNeededService> restartNeededServices);

    public void pageBeginRender(PageEvent event) {
        if (getRestartNeededServices() == null) {
            setRestartNeededServices(getSipxProcessContext().getRestartNeededServices());
        }
    }

    public String getServiceLabel() {
        String serviceBeanId = getCurrentRow().getServiceBeanId();
        String key = "label." + serviceBeanId;
        return getMessage(getMessages(), key, serviceBeanId);
    }

    public void restart() {
        Collection<RestartNeededService> beans = getSelections().getAllSelected();
        if (beans == null) {
            return;
        }

        // Restart needed services on each affected location
        for (Location location : createLocationToServiceMap(beans).keySet()) {
            List<SipxService> restartServices = createLocationToServiceMap(beans).get(location);
            getSipxProcessContext().manageServices(location, restartServices, SipxProcessContext.Command.RESTART);
        }

        // Forces a page refresh
        setRestartNeededServices(null);
    }

    private Map<Location, List<SipxService>> createLocationToServiceMap(Collection<RestartNeededService> beans) {
        Map<Location, List<SipxService>> map = new HashMap<Location, List<SipxService>>();
        for (RestartNeededService bean : beans) {
            Location location = getLocationsManager().getLocationByFqdn(bean.getLocation());
            if (location == null) {
                continue;
            }
            List<SipxService> services = map.get(location);
            if (services == null) {
                services = new ArrayList<SipxService>();
                map.put(location, services);
            }
            SipxService service = getSipxServiceManager().getServiceByBeanId(bean.getServiceBeanId());
            services.add(service);
        }
        return map;
    }

    public void ignore() {
        Collection<RestartNeededService> beans = getSelections().getAllSelected();
        if (beans == null) {
            return;
        }

        getSipxProcessContext().unmarkServicesToRestart(beans);

        // Forces a page refresh
        setRestartNeededServices(null);
    }
}
