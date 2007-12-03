/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.site.admin.configdiag;

import java.text.DateFormat;
import java.text.Format;
import java.util.Date;
import java.util.List;

import org.apache.tapestry.IPage;
import org.apache.tapestry.annotations.Bean;
import org.apache.tapestry.annotations.InjectObject;
import org.apache.tapestry.annotations.InjectPage;
import org.apache.tapestry.annotations.Persist;
import org.apache.tapestry.bean.EvenOdd;
import org.apache.tapestry.event.PageBeginRenderListener;
import org.apache.tapestry.event.PageEvent;
import org.apache.tapestry.html.BasePage;
import org.sipfoundry.sipxconfig.admin.configdiag.CompositeExecutionTask;
import org.sipfoundry.sipxconfig.admin.configdiag.ConfigurationDiagnostic;
import org.sipfoundry.sipxconfig.admin.configdiag.ConfigurationDiagnosticContext;

public abstract class ConfigurationDiagnosticPage extends BasePage implements
        PageBeginRenderListener {
    public static final String PAGE = "ConfigurationDiagnosticPage";
    public static final String SESSION = "session";

    @InjectObject(value = "spring:configurationDiagnosticContext")
    public abstract ConfigurationDiagnosticContext getConfigurationDiagnosticContext();

    @InjectObject(value = "spring:compositeExecutionTask")
    public abstract CompositeExecutionTask getCompositeExecutionTask();

    @InjectPage(value = ConfigurationDiagnosticDetailPage.PAGE)
    public abstract ConfigurationDiagnosticDetailPage getconfigurationDiagnosticDetailPage();

    @Bean
    public abstract EvenOdd getRowClass();

    @Persist(value = SESSION)
    public abstract List<ConfigurationDiagnostic> getConfigurationTests();

    public abstract void setConfigurationTests(List<ConfigurationDiagnostic> configurationTests);

    public abstract ConfigurationDiagnostic getConfigurationTest();

    @Persist(value = SESSION)
    public abstract Format getDateFormat();

    public abstract void setDateFormat(Format format);

    public void pageBeginRender(PageEvent event) {
        if (getConfigurationTests() != null) {
            return;
        }
        setConfigurationTests(getConfigurationDiagnosticContext().getConfigurationTests());
        Format dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT,
                getLocale());
        setDateFormat(dateFormat);
    }

    public IPage displayDetail(ConfigurationDiagnostic diagnostic) {
        ConfigurationDiagnosticDetailPage page = getconfigurationDiagnosticDetailPage();
        page.setConfigurationDiagnostic(diagnostic);
        return page;
    }

    public void runAllTests() {
        CompositeExecutionTask task = getCompositeExecutionTask();
        task.removeAll();
        for (ConfigurationDiagnostic configurationTest : getConfigurationTests()) {
            getCompositeExecutionTask().addExecutionTask(configurationTest);
        }
        task.execute();
    }

    public Object getLastRunTime() {
        ConfigurationDiagnostic configuationDiagnostic = getConfigurationTest();
        Date lastRunTime = configuationDiagnostic.getStartTime();
        if (lastRunTime == null) {
            return getMessages().getMessage("notRun");
        }
        return getDateFormat().format(lastRunTime);
    }
}
