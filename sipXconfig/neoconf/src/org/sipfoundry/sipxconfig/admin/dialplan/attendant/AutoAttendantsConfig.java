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
import org.dom4j.QName;
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
    private static final String NAMESPACE = "http://www.sipfoundry.org/sipX/schema/xml/autoattendants-00-00";

    private DialPlanContext m_dialPlanContext;

    @Override
    public Document getDocument() {
        Document document = FACTORY.createDocument();
        QName autoAttendantsName = FACTORY.createQName("autoattendants", NAMESPACE);
        Element aasEl = document.addElement(autoAttendantsName);
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

        // FIXME: initialTimeout parameter is actually misnamed
        // "overallDigitTimeout" which is incorrectly described,
        // as VoiceXML doesn't have such a concept.
        addSettingValueMillis(dtmfEl, "initialTimeout", autoAttendant, "dtmf/overallDigitTimeout");
        String idt = "dtmf/interDigitTimeout"; // To prevent checkStyle warning
        addSettingValueMillis(dtmfEl, "interDigitTimeout", autoAttendant, idt);
        // FIXME: extraDigitTimeout needs to be added. For now use interDigitTimeout
        addSettingValueMillis(dtmfEl, "extraDigitTimeout", autoAttendant, idt);
        addSettingValue(dtmfEl, "maximumDigits", autoAttendant, "dtmf/maxDigits");

        Element irEl = aaEl.addElement("invalidResponse");
        addSettingValue(irEl, "noInputCount", autoAttendant, "onfail/noinputCount");
        addSettingValue(irEl, "invalidResponseCount", autoAttendant, "onfail/nomatchCount");
        Boolean transfer = (Boolean) autoAttendant.getSettingTypedValue("onfail/transfer");
        irEl.addElement("transferOnFailures").setText(transfer.toString());
        if (transfer.booleanValue()) {
            addSettingValue(irEl, "transferUrl", autoAttendant, "onfail/transfer-extension");
            // FIXME: this should be the full path of the uploaded transferPrompt
            addSettingValue(irEl, "transferPrompt", autoAttendant, "onfail/transfer-prompt");
        }
    }

    /**
     * Retrieves the setting value, rescales it to millis (multiplying by 1000), and adds to
     * element.
     *
     * @param parent element to which new element is added
     * @param name the name of newly added element
     * @param bean the bean from which setting value is read
     * @param settingName the name of the setting; in this case it has to be integer setting
     *        expressed in seconds
     */
    private void addSettingValueMillis(Element parent, String name, BeanWithSettings bean, String settingName) {
        Integer value = (Integer) bean.getSettingTypedValue(settingName);
        if (value != null) {
            long millisValue = value * 1000;
            parent.addElement(name).setText(Long.toString(millisValue));
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
