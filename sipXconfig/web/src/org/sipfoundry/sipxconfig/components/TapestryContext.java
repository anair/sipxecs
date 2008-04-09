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

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Locale;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.hivemind.ApplicationRuntimeException;
import org.apache.hivemind.Messages;
import org.apache.tapestry.IActionListener;
import org.apache.tapestry.IAsset;
import org.apache.tapestry.IComponent;
import org.apache.tapestry.IRequestCycle;
import org.apache.tapestry.form.IPropertySelectionModel;
import org.apache.tapestry.valid.IValidationDelegate;
import org.apache.tapestry.valid.ValidatorException;
import org.sipfoundry.sipxconfig.common.NamedObject;
import org.sipfoundry.sipxconfig.common.UserException;
import org.sipfoundry.sipxconfig.site.skin.SkinControl;

/**
 * Tapestry utilities available to web pages
 */
public class TapestryContext {
    public static final String CONTEXT_BEAN_NAME = "tapestry";

    private static final int SECONDS_PER_MINUTE = 60;

    private HivemindContext m_hivemindContext;

    private SkinControl m_skinControl;

    /**
     * Set to false to suppress rendering base tag which is important with some older HTTP
     * proxies.
     */
    private boolean m_renderBaseTag;

    /**
     * Session timeout for an admin session in minutes.
     */
    private int m_adminSessionTimeout;

    /**
     * Session timeout for a user session in minutes.
     */
    private int m_userSessionTimeout;

    public void setHivemindContext(HivemindContext hivemindContext) {
        m_hivemindContext = hivemindContext;
    }

    public HivemindContext getHivemindContext() {
        return m_hivemindContext;
    }

    /**
     * Add a option to the dropdown model with a label to instruct the user to make a selection.
     * If not item is selected, your business object method will be explicitly set to null
     */
    public IPropertySelectionModel instructUserToSelect(IPropertySelectionModel model,
            Messages messages) {
        return addExtraOption(model, messages, "prompt.select");
    }

    /**
     * Add a option to the dropdown model with a label to instruct the user to make a selection.
     * If not item is selected, your business object method will be explicitly set to null
     */
    public IPropertySelectionModel addExtraOption(IPropertySelectionModel model,
            Messages messages, String extraKey) {
        ExtraOptionModelDecorator decorated = new ExtraOptionModelDecorator();
        decorated.setExtraLabel(messages.getMessage(extraKey));
        decorated.setExtraOption(null);
        decorated.setModel(model);

        return decorated;
    }

    /**
     * Translates UserExceptions into form errors instead redirecting to an error page.
     */
    public IActionListener treatUserExceptionAsValidationError(IValidationDelegate validator,
            IActionListener listener) {
        return new UserExceptionAdapter(validator, listener);
    }

    /**
     * Translates UserExceptions into form errors instead redirecting to an error page.
     */
    public IActionListener treatUserExceptionAsValidationError(IComponent component,
            IActionListener listener) {
        return new UserExceptionAdapter(TapestryUtils.getValidator(component), listener);
    }

    /**
     * Translates UserExceptions into localized form errors instead redirecting to an error page.
     */
    public IActionListener treatUserExceptionAsValidationError(IValidationDelegate validator,
            IActionListener listener, Messages messages) {
        return new UserExceptionAdapter(validator, listener, messages);
    }

    /**
     * Translates UserExceptions into localized form errors instead redirecting to an error page.
     */
    public IActionListener treatUserExceptionAsValidationError(IComponent component,
            IActionListener listener, Messages messages) {
        return new UserExceptionAdapter(TapestryUtils.getValidator(component), listener, messages);
    }
            
    static class UserExceptionAdapter implements IActionListener {

        private IActionListener m_listener;

        private IValidationDelegate m_validator;
        
        private Messages m_messages;
        
        UserExceptionAdapter(IValidationDelegate validator, IActionListener listener) {
            this(validator, listener, null);
        }

        UserExceptionAdapter(IValidationDelegate validator, IActionListener listener, Messages messages) {
            m_listener = listener;
            m_validator = validator;
            m_messages = messages;
        }

        public void actionTriggered(IComponent component, IRequestCycle cycle) {
            try {
                m_listener.actionTriggered(component, cycle);
            } catch (ApplicationRuntimeException are) {
                UserException cause = getUserExceptionCause(are);
                if (cause != null) {
                    recordUserException(cause);
                } else {
                    throw are;
                }
            } catch (UserException ue) {
                recordUserException(ue);
            }
        }

        /**
         * Starting with Tapestry 4, Listeners wrap exceptions with ApplicationRuntimeException.
         * We have to prepare for many levels of exceptions as listeners are often wrapped by
         * other listeners
         */
        UserException getUserExceptionCause(ApplicationRuntimeException e) {
            Throwable t = e.getCause();
            if (t instanceof UserException) {
                return (UserException) t;
            }
            if (t instanceof ApplicationRuntimeException && t != e) {
                // recurse
                return getUserExceptionCause((ApplicationRuntimeException) t);
            }
            return null;
        }

        private void recordUserException(UserException e) {
            if (m_messages != null && e.getCause() != null) {
                m_validator.record(new ValidatorException(m_messages.format(e.getMessage(),
                        e.getCause().getMessage())));
            } else if (m_messages != null) {
                m_validator.record(new ValidatorException(m_messages.getMessage(e.getMessage())));
            } else {
                m_validator.record(new ValidatorException(e.getMessage()));
            }
        }

        public String getMethodName() {
            return m_listener.getMethodName();
        }
    }

    /**
     * Join a list of names objects into a string give a delimiter.
     * 
     * Example: <code>
     *  <span jwcid="@Insert" value="ognl:tapestry.joinNamed(items, ', ')"/>
     * </code>
     */
    public String joinNamed(Collection namedItems, String delim) {
        Collection names = CollectionUtils.collect(namedItems, new NamedObject.ToName());
        return StringUtils.join(names.iterator(), delim);
    }

    /**
     * Example: <span jwcid="@Insert" value="someDate" format="tapestry.date(locale)"/>
     */
    public DateFormat date(Locale locale) {
        return TapestryUtils.getDateFormat(locale);
    }

    public IAsset[] getStylesheets(IComponent component) {
        String userAgent = component.getPage().getRequestCycle().getInfrastructure().getRequest()
                .getHeader("user-agent");
        return m_skinControl.getStylesheetAssets(userAgent);
    }

    public void setSkinControl(SkinControl skinControl) {
        m_skinControl = skinControl;
    }

    public boolean isLicenseRequired() {
        return null != m_skinControl.getAsset(SkinControl.ASSET_LICENSE);
    }

    public void setRenderBaseTag(boolean renderBaseTag) {
        m_renderBaseTag = renderBaseTag;
    }

    public boolean isRenderBaseTag() {
        return m_renderBaseTag;
    }

    public void setAdminSessionTimeout(int adminSessionTimeout) {
        m_adminSessionTimeout = adminSessionTimeout;
    }

    public void setUserSessionTimeout(int userSessionTimeout) {
        m_userSessionTimeout = userSessionTimeout;
    }

    /**
     * Returns desired session timeout
     * 
     * @param admin - true if it is an admin session
     * @return session timeout in seconds
     */
    public int getMaxInactiveInterval(boolean admin) {
        int minutes = admin ? m_adminSessionTimeout : m_userSessionTimeout;
        return minutes * SECONDS_PER_MINUTE;
    }

    /**
     * Retrieves text of the license usually provided by OEM plugin. Only works if
     * isLicenseRequired returns true.
     */
    public String getLicense() {
        IAsset asset = m_skinControl.getAsset(SkinControl.ASSET_LICENSE);
        InputStream licenseStream = asset.getResourceAsStream();
        try {
            return IOUtils.toString(licenseStream, "UTF-8");
        } catch (IOException e) {
            throw new UserException(e);
        } finally {
            IOUtils.closeQuietly(licenseStream);
        }
    }
}
