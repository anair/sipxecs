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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import ognl.Ognl;
import ognl.OgnlException;

import org.apache.commons.lang.StringUtils;
import org.apache.tapestry.form.IPropertySelectionModel;
import org.sipfoundry.sipxconfig.device.DeviceDescriptor;

public class VendorFilteredDeviceSelectionModel implements IPropertySelectionModel {

    private transient Object m_parsedLabelExpression;

    private transient Object m_parsedValueExpression;

    private Object[] m_objects;

    private String m_labelExpression;

    private String m_valueExpression;

    private String m_vendorFilter;

    public void setVendorFilter(String filter) {
        m_vendorFilter = filter;
    }

    public void setCollection(Collection objects) {
        List<Object> returnedObjectsList = new ArrayList<Object>();
        Object[] objectsArray = objects.toArray();
        for (Object object : objectsArray) {
            DeviceDescriptor descriptor = (DeviceDescriptor) object;
            if (descriptor != null && descriptor.getVendor() != null && descriptor.getVendor().equals(m_vendorFilter)) {
                returnedObjectsList.add(object);
            }
        }
        m_objects = returnedObjectsList.toArray();
    }

    /**
     * Will run expression on each object in collection to build label text
     */
    public void setLabelExpression(String labelOgnlExpression) {
        m_labelExpression = labelOgnlExpression;
    }

    /**
     * Will run expression on each object in collection to read/write value to PropertySelection
     * value expression
     */
    public void setValueExpression(String valueExpression) {
        m_valueExpression = valueExpression;
    }

    public int getOptionCount() {
        return m_objects.length;
    }

    public Object getOption(int index) {
        if (StringUtils.isBlank(m_valueExpression)) {
            return m_objects[index];
        }

        try {
            parseValueExpression();
            Object objValue = Ognl.getValue(m_parsedValueExpression, m_objects[index]);
            return objValue;
        } catch (OgnlException e) {
            throw new RuntimeException(e);
        }
    }

    public String getLabel(int index) {
        // source adapted from OgnlTableColumnEvaluator

        // If no expression is given, then this is dummy column. Return something.
        if (StringUtils.isBlank(m_labelExpression)) {
            return StringUtils.EMPTY;
        }

        try {
            parseLabelExpression();
            Object objValue = Ognl.getValue(m_parsedLabelExpression, m_objects[index]);
            return safeToString(objValue);
        } catch (OgnlException e) {
            throw new RuntimeException(e);
        }
    }

    private void parseLabelExpression() throws OgnlException {
        if (m_parsedLabelExpression == null) {
            m_parsedLabelExpression = Ognl.parseExpression(m_labelExpression);
        }
    }

    private void parseValueExpression() throws OgnlException {
        if (m_parsedValueExpression == null) {
            m_parsedValueExpression = Ognl.parseExpression(m_valueExpression);
        }
    }

    private String safeToString(Object o) {
        return o == null ? StringUtils.EMPTY : o.toString();
    }

    /** based off StringPropertySelectionModel */
    public String getValue(int index) {
        return Integer.toString(index);
    }

    /** based off StringPropertySelectionModel */
    public Object translateValue(String value) {
        int index = Integer.parseInt(value);
        return getOption(index);
    }

    public boolean isDisabled(int index) {
        return false;
    }
}
