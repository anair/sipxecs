/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.cdr;

import junit.framework.TestCase;

public class CdrSearchTest extends TestCase {
    
    // See XCF-1767 and code for details
    public void testNullModeUsesDefaultMode() {
        CdrSearch search = new CdrSearch();
        search.setMode(null);
        assertSame(CdrSearch.Mode.NONE, search.getMode());
    }
    
    public void testGetSqlEmpty() {
        CdrSearch search = new CdrSearch();
        StringBuilder sql = new StringBuilder();
        assertFalse(sql.toString(), search.appendGetSql(sql));
        assertEquals("", sql.toString());
    }

    public void testGetSqlCaller() {
        CdrSearch search = new CdrSearch();
        search.setTerm("abc");
        search.setMode(CdrSearch.Mode.CALLER);
        StringBuilder sql = new StringBuilder();
        assertTrue(sql.toString(), search.appendGetSql(sql));
        assertEquals(" AND (caller_aor LIKE '%abc%')", sql.toString());
    }

    public void testGetSqlCallee() {
        CdrSearch search = new CdrSearch();
        search.setTerm("abc");
        search.setMode(CdrSearch.Mode.CALLEE);
        StringBuilder sql = new StringBuilder();
        assertTrue(sql.toString(), search.appendGetSql(sql));
        assertEquals(" AND (callee_aor LIKE '%abc%')", sql.toString());
    }

    public void testGetSqlAny() {
        CdrSearch search = new CdrSearch();
        search.setTerm("abc");
        search.setMode(CdrSearch.Mode.ANY);
        StringBuilder sql = new StringBuilder();
        assertTrue(sql.toString(), search.appendGetSql(sql));
        assertEquals(" AND (callee_aor || caller_aor LIKE '%abc%')", sql.toString());
    }

    public void testGetOrderBySql() {
        CdrSearch search = new CdrSearch();
        StringBuilder sql = new StringBuilder();
        assertTrue(sql.toString(), search.appendOrderBySql(sql));
        assertEquals(" ORDER BY start_time ASC", sql.toString());
    }

    public void testGetOrderBySqlDesc() {
        CdrSearch search = new CdrSearch();
        search.setOrder("caller", false);
        StringBuilder sql = new StringBuilder();
        assertTrue(sql.toString(), search.appendOrderBySql(sql));
        assertEquals(" ORDER BY caller_aor DESC", sql.toString());
    }
}
