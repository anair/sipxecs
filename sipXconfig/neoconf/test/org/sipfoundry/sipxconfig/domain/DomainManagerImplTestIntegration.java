/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.domain;

import org.dbunit.Assertion;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ReplacementDataSet;
import org.sipfoundry.sipxconfig.IntegrationTestCase;
import org.sipfoundry.sipxconfig.domain.DomainManager.DomainNotInitializedException;

public class DomainManagerImplTestIntegration extends IntegrationTestCase {
    
    private DomainManager m_context;

    protected void init() throws Exception {
        loadDataSetXml("ClearDb.xml");        
    }
    
    public void testGetDomain() throws Exception {
        init();
        Domain d = m_context.getDomain();
        assertNotNull(d);        
    }
    
    public void testGetEmptyDomain() throws Exception {
        init();
        loadDataSetXml("domain/NoDomainSeed.xml");        
        try {
            m_context.getDomain();
            fail();
        } catch (DomainNotInitializedException expected) {
            assertTrue(true);
        }
    }    

    public void testSaveNewDomain() throws Exception {
        init();
        Domain d = new Domain();
        d.setName("robin");
        d.setSharedSecret("secret");
        m_context.saveDomain(d);
        ReplacementDataSet ds = loadReplaceableDataSetFlat("domain/DomainUpdateExpected.xml");
        ds.addReplacementObject("[domain_id]", d.getId());
        ITable actual = ds.getTable("domain");
        ITable expected = getConnection().createDataSet().getTable("domain");
        Assertion.assertEquals(expected, actual);
    }

    public void testUpdateDomain() throws Exception {
        init();
        Domain domain = m_context.getDomain();
        domain.setName("robin");
        domain.setSharedSecret("secret");
        m_context.saveDomain(domain);        
        
        ReplacementDataSet ds = loadReplaceableDataSetFlat("domain/DomainUpdateExpected.xml");
        ds.addReplacementObject("[domain_id]", domain.getId());
        ITable actual = ds.getTable("domain");
        ITable expected = getConnection().createDataSet().getTable("domain");
        Assertion.assertEquals(expected, actual);
    }
    
    public void setDomainManager(DomainManager domainManager) {
        m_context = domainManager;
    }
}
