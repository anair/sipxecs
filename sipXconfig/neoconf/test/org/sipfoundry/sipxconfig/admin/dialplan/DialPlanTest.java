/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.admin.dialplan;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

public class DialPlanTest extends TestCase {

    public void testSetOperator() {
        DialPlan plan = new DialPlan();
        AttendantRule arule = new AttendantRule();
        plan.addRule(arule);
        assertNull(arule.getAfterHoursAttendant().getAttendant());
        AutoAttendant operator = new AutoAttendant();
        plan.setOperator(operator);
        assertSame(operator, arule.getAfterHoursAttendant().getAttendant());
    }

    public void testGetDialingRuleByType() {
        DialingRule[] candidates = new DialingRule[] {
            new InternalRule(), new CustomDialingRule(), new InternationalRule(),
            new CustomDialingRule()
        };

        CustomDialingRule[] actual = DialPlan.getDialingRuleByType(Arrays.asList(candidates),
                CustomDialingRule.class);
        assertEquals(2, actual.length);
        assertSame(candidates[1], actual[0]);
        assertSame(candidates[3], actual[1]);
    }
    
    public void testAddRule() {        
        DialingRule a = new InternalRule();
        DialingRule b = new CustomDialingRule();
        DialingRule c = new AttendantRule();
        DialingRule d = new AttendantRule();

        DialPlan plan = new DialPlan();
        plan.addRule(a);
        plan.addRule(b);
        assertEquals(2, plan.getRules().size());
        assertEquals(0, a.getPosition());
        assertEquals(1, b.getPosition());
        
        plan.addRule(0, c);
        assertEquals(3, plan.getRules().size());
        assertEquals(0, c.getPosition());
        assertEquals(1, a.getPosition());
        assertEquals(2, b.getPosition());
        
        plan.addRule(-1, d);
        assertEquals(4, plan.getRules().size());
        assertEquals(0, c.getPosition());
        assertEquals(1, a.getPosition());
        assertEquals(2, b.getPosition());
        assertEquals(3, d.getPosition());        
    }

    public void testGetAttendantRules() {
        DialPlan plan = new DialPlan();
        assertTrue(plan.getAttendantRules().isEmpty());
        DialingRule[] rules = new DialingRule[] {
            new AttendantRule(), new CustomDialingRule(), new AttendantRule()
        };
        plan.setRules(Arrays.asList(rules));
        assertEquals(2, plan.getAttendantRules().size());
    }
    
    public void testGetGenerationRules() {
        // VoicemailRedirect rule should always be present and be last in the
        // list of rules
        DialPlan out = new DialPlan();
        List<DialingRule> defaultRules = out .getGenerationRules();
        assertTrue(defaultRules.size() > 0);
        assertTrue(defaultRules.get(defaultRules.size() - 1) instanceof VoicemailRedirectRule);
    }
}
