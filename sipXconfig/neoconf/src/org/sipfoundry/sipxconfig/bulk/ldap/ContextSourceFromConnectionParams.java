/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.bulk.ldap;

import org.springframework.ldap.support.LdapContextSource;

/**
 * Will resolve required context properties from LdapManager which in turn gets them
 * from database.
 * 
 * Base LdapContextSource assumes connection parameters are known at spring configuration
 * time and that is not our situation
 */
public class ContextSourceFromConnectionParams extends LdapContextSource {
    private LdapManager m_ldapManager;

    public void setLdapManager(LdapManager ldapManager) {
        m_ldapManager = ldapManager;
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        LdapConnectionParams params = m_ldapManager.getConnectionParams();
        applyParameters(params);
    }
    
    public void applyParameters(LdapConnectionParams params) {
        params.applyToContext(this);        
        try {
            super.afterPropertiesSet();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
