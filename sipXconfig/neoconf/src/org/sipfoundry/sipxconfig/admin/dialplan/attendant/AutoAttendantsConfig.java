/*
 *
 *
 * Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 */
package org.sipfoundry.sipxconfig.admin.dialplan.attendant;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.Element;
import org.sipfoundry.sipxconfig.admin.dialplan.AttendantMenu;
import org.sipfoundry.sipxconfig.admin.dialplan.AttendantMenuAction;
import org.sipfoundry.sipxconfig.admin.dialplan.AttendantMenuItem;
import org.sipfoundry.sipxconfig.admin.dialplan.AttendantRule;
import org.sipfoundry.sipxconfig.admin.dialplan.AutoAttendant;
import org.sipfoundry.sipxconfig.admin.dialplan.DialPlanContext;
import org.sipfoundry.sipxconfig.admin.dialplan.attendant.WorkingTime.WorkingHours;
import org.sipfoundry.sipxconfig.admin.dialplan.config.XmlFile;
import org.sipfoundry.sipxconfig.common.DialPad;
import org.sipfoundry.sipxconfig.setting.BeanWithSettings;

public class AutoAttendantsConfig extends XmlFile {

    // please note: US locale always...
    private static final SimpleDateFormat HOLIDAY_FORMAT = new SimpleDateFormat("dd-MMM-yyyy", Locale.US);

    private DialPlanContext m_dialPlanContext;

    @Override
    public Document getDocument() {
        Document document = FACTORY.createDocument();
        Element aasEl = document.addElement("autoattendants");
        List<AutoAttendant> autoAttendants = m_dialPlanContext.getAutoAttendants();
        for (AutoAttendant autoAttendant : autoAttendants) {
            generateAttendants(aasEl, autoAttendant);
        }

        Element schedulesEl = aasEl.addElement("schedules");
        List<AttendantRule> attendantRules = m_dialPlanContext.getAttendantRules();
        for (AttendantRule attendantRule : attendantRules) {
            generateSchedule(schedulesEl, attendantRule);
        }
        return document;
    }

    private void generateSchedule(Element schedulesEl, AttendantRule attendantRule) {
        Element scheduleEl = schedulesEl.addElement("schedule");
        Holiday holidayAttendant = attendantRule.getHolidayAttendant();
        if (holidayAttendant.isEnabled()) {
            Element holidayEl = scheduleEl.addElement("holiday");
            addName(holidayEl, holidayAttendant.getAttendant());
            for (Date date : holidayAttendant.getDates()) {
                holidayEl.addElement("date").setText(HOLIDAY_FORMAT.format(date));
            }
        }
        WorkingTime workingTimeAttendant = attendantRule.getWorkingTimeAttendant();
        if (workingTimeAttendant.isEnabled()) {
            Element regularHoursEl = scheduleEl.addElement("regularhours");
            addName(regularHoursEl, workingTimeAttendant.getAttendant());
            WorkingHours[] workingHours = workingTimeAttendant.getWorkingHours();
            for (WorkingHours hours : workingHours) {
                Element dayEl = regularHoursEl.addElement(hours.getDay().getName().toLowerCase());
                dayEl.addElement("from").setText(hours.getStartTime());
                dayEl.addElement("to").setText(hours.getStopTime());
            }
        }
        ScheduledAttendant afterHoursAttendant = attendantRule.getAfterHoursAttendant();
        if (afterHoursAttendant.isEnabled()) {
            Element afterHoursEl = scheduleEl.addElement("afterhours");
            addName(afterHoursEl, afterHoursAttendant.getAttendant());
        }
    }

    private void generateAttendants(Element aasEl, AutoAttendant autoAttendant) {
        Element aaEl = aasEl.addElement("autoattendant");
        addName(aaEl, autoAttendant);
        aaEl.addElement("prompt").setText(autoAttendant.getPromptFile().getPath());

        Element miEl = aaEl.addElement("menuItems");
        AttendantMenu menu = autoAttendant.getMenu();
        Map<DialPad, AttendantMenuItem> menuItems = menu.getMenuItems();
        for (Map.Entry<DialPad, AttendantMenuItem> entry : menuItems.entrySet()) {
            generateMenuItem(miEl, entry.getKey(), entry.getValue());
        }

        Element dtmfEl = aaEl.addElement("dtmf");

        // FIXME: no initialTimeout parameter
        // addSettingValue(dtmfEl, "initialTimeout", autoAttendant, "dtmf/???");
        addSettingValue(dtmfEl, "interDigitTimeout", autoAttendant, "dtmf/interDigitTimeout");
        // FIXME: we have overallDigitTimeout parameter
        // addSettingValue(dtmfEl, "extraDigitTimeout", autoAttendant, "dtmf/???");
        addSettingValue(dtmfEl, "maximumDigits", autoAttendant, "dtmf/maxDigits");

        Element irEl = aaEl.addElement("invalidResponse");
        addSettingValue(irEl, "noInputCount", autoAttendant, "onfail/noinputCount");
        addSettingValue(irEl, "invalidResponseCount", autoAttendant, "onfail/nomatchCount");
        Boolean transfer = (Boolean) autoAttendant.getSettingTypedValue("onfail/transfer");
        irEl.addElement("transferOnFailures").setText(transfer.toString());
        if (transfer.booleanValue()) {
            addSettingValue(irEl, "transferUrl", autoAttendant, "onfail/transfer-extension");
            addSettingValue(irEl, "transferPrompt", autoAttendant, "onfail/transfer-prompt");
        }
    }

    private void addSettingValue(Element parent, String name, BeanWithSettings bean, String settingName) {
        String value = bean.getSettingValue(settingName);
        if (value != null) {
            parent.addElement(name).setText(value);
        }
    }

    private void addName(Element aaEl, AutoAttendant autoAttendant) {
        aaEl.addElement("name").setText(autoAttendant.getName());
    }

    private void generateMenuItem(Element misEl, DialPad dialPad, AttendantMenuItem menuItem) {
        Element miEl = misEl.addElement("menuItem");
        miEl.addElement("dialPad").setText(dialPad.getName());
        AttendantMenuAction action = menuItem.getAction();
        miEl.addElement("action").setText(action.getName());
        if (action.isAttendantParameter()) {
            miEl.addElement("parameter").setText(menuItem.getParameter());
        }
        if (action.isExtensionParameter()) {
            miEl.addElement("extension").setText(menuItem.getParameter());
        }
    }

    public void generate(DialPlanContext dialPlanContext) {
        m_dialPlanContext = dialPlanContext;
    }
}
