/*
 *
 *
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 *
 */
package org.sipfoundry.sipxconfig.admin;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

/**
 * Common base class for all configuration files generators that use Velocity templating engine
 * TemplateConfigurationFile
 */
public abstract class TemplateConfigurationFile implements ConfigurationFile {
    private static final Log LOG = LogFactory.getLog(TemplateConfigurationFile.class);

    private VelocityEngine m_velocityEngine;
    private String m_templateLocation;

    public TemplateConfigurationFile() {
        super();
    }

    public void setVelocityEngine(VelocityEngine velocityEngine) {
        m_velocityEngine = velocityEngine;
    }

    public String getTemplate() {
        return m_templateLocation;
    }

    public void setTemplate(String template) {
        m_templateLocation = template;
    }

    public final void write(Writer output) throws IOException {
        try {
            VelocityContext context = setupContext();
            m_velocityEngine.mergeTemplate(getTemplate(), context, output);
            output.flush();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Overwrite to set-up Velocity context
     */
    protected VelocityContext setupContext() {
        VelocityContext context = new VelocityContext();
        context.put("dollar", "$");
        return context;
    }

    public final String getFileContent() {
        try {
            StringWriter out = new StringWriter();
            write(out);
            return out.toString();
        } catch (IOException e) {
            LOG.error("Rethrowing unexpected: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
