/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.site.common;

import org.apache.tapestry.BaseComponent;
import org.apache.tapestry.annotations.ComponentClass;
import org.apache.tapestry.annotations.Parameter;
import org.apache.tapestry.components.Block;

@ComponentClass(allowBody = true, allowInformalParameters = false)
public abstract class Cloud extends BaseComponent {
    @Parameter(required = true)
    public abstract Block getHeader();

    @Parameter(required = true)
    public abstract Block getFooter();
}
