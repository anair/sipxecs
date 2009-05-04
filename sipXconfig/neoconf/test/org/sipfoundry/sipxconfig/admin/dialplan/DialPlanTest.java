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

import java.util.ArrayList;
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
            new InternalRule(), new CustomDialingRule(), new InternationalRule(), new CustomDialingRule()
        };

        List<CustomDialingRule> actual = DialPlan.getDialingRuleByType(Arrays.asList(candidates),
                CustomDialingRule.class);
        assertEquals(2, actual.size());
        assertSame(candidates[1], actual.get(0));
        assertSame(candidates[3], actual.get(1));
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
        List<DialingRule> defaultRules = out.getGenerationRules();
        assertTrue(defaultRules.size() > 0);
        assertTrue(defaultRules.get(defaultRules.size() - 1) instanceof VoicemailRedirectRule);
    }

    public void testRemoveEmptyRules() {
        DialPlan out = new DialPlan();
        DialingRule a = new InternalRule();
        DialingRule b = new CustomDialingRule();
        DialingRule c = new AttendantRule();
        DialingRule d = new AttendantRule();

        out.setRules(new ArrayList<DialingRule>(Arrays.asList(a, b, null, c, null, d)));
        assertEquals(6, out.getRules().size());

        out.removeEmptyRules();

        List<DialingRule> rules = out.getRules();
        assertEquals(4, out.getRules().size());
        assertSame(a, rules.get(0));
        assertEquals(0, rules.get(0).getPosition());
        assertSame(b, rules.get(1));
        assertEquals(1, rules.get(1).getPosition());
        assertSame(c, rules.get(2));
        assertEquals(2, rules.get(2).getPosition());
        assertSame(d, rules.get(3));
        assertEquals(3, rules.get(3).getPosition());
    }
}
