package org.sipfoundry.sipxconfig.conference;

import java.util.List;

import junit.framework.JUnit4TestAdapter;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class FreeswitchApiResultParserTest {

    private FreeswitchApiResultParser m_parser;

    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(FreeswitchApiResultParserTest.class);
      }

    @Before
    public void createParser() {
        m_parser = new FreeswitchApiResultParserImpl();
    }

    @Test
    public void testGetActiveConferenceCount() {
        String resultString = "No active conferences.";
        Assert.assertEquals(0, m_parser.getActiveConferenceCount(resultString));

        resultString =
            "Conference myconf2 (1 member)\n" +
            "4;sofia/eng.bluesocket.com/202@192.168.100.233;c6c34057-3844-43de-abbd-816fc64e1926;cardassia;202;hear|speak|floor;0;0;300\n" +
            "Conference myconf1 (2 members)\n" +
            "2;sofia/eng.bluesocket.com/201@192.168.100.233;f69d2b1f-4841-40a4-8e0f-847d1aeef2f0;201;201;hear|speak;0;0;300\n" +
            "1;sofia/eng.bluesocket.com/200@192.168.100.233;a5b6cdbe-7cbf-48a7-a52d-98b871eb2491;Joe Attardi;200;hear|speak|floor;0;0;300\n";

        Assert.assertEquals(2, m_parser.getActiveConferenceCount(resultString));
    }

    @Test
    public void testGetActiveConferences() {
        String resultString =
            "Conference myconf3 (1 member)\n" +
            "4;sofia/eng.bluesocket.com/202@192.168.100.233;c6c34057-3844-43de-abbd-816fc64e1926;cardassia;202;hear|speak|floor;0;0;300\n" +
            "Conference myconf2 (2 members)\n" +
            "2;sofia/eng.bluesocket.com/201@192.168.100.233;f69d2b1f-4841-40a4-8e0f-847d1aeef2f0;201;201;hear|speak;0;0;300\n" +
            "1;sofia/eng.bluesocket.com/200@192.168.100.233;a5b6cdbe-7cbf-48a7-a52d-98b871eb2491;Joe Attardi;200;hear|speak|floor;0;0;300\n" +
            "Conference myconf1 (1 member locked)\n" +
            "1;sofia/eng.bluesocket.com/200@192.168.100.233;a5b6cdbe-7cbf-48a7-a52d-98b871eb2491;Joe Attardi;200;hear|speak|floor;0;0;300\n";

        List<ActiveConference> activeConferences = m_parser.getActiveConferences(resultString);

        Assert.assertEquals(3, activeConferences.size());

        String[] expectedConferenceNames = {"myconf3", "myconf2", "myconf1"};
        int[] expectedMembers = {1, 2, 1};
        boolean[] expectedLocked = {false, false, true};
        for (int n = 0; n < activeConferences.size(); n++) {
            ActiveConference conference = activeConferences.get(n);
            Assert.assertEquals(expectedConferenceNames[n], conference.getName());
            Assert.assertEquals(expectedMembers[n], conference.getMembers());
            Assert.assertEquals(expectedLocked[n], conference.isLocked());
        }
    }
    
    @Test
    public void testGetConferenceMembers() {
        String resultString =
            "2;sofia/eng.bluesocket.com/201@192.168.100.233;f69d2b1f-4841-40a4-8e0f-847d1aeef2f0;201;201;hear|speak;0;0;300\n" +
            "1;sofia/eng.bluesocket.com/200@192.168.100.233;a5b6cdbe-7cbf-48a7-a52d-98b871eb2491;Joe Attardi;200;hear|speak|floor;0;0;300\n";
        
        List<ActiveConferenceMember> members = m_parser.getConferenceMembers(resultString);
        Assert.assertEquals(2, members.size());
        Assert.assertEquals("201 (201@192.168.100.233)", members.get(0).getName());
        Assert.assertEquals("Joe Attardi (200@192.168.100.233)", members.get(1).getName());
    }

    @Test(expected = NoSuchMemberException.class)
    public void testVerifyMemberActionInvalid() {
        ActiveConferenceMember member = new ActiveConferenceMember();
        member.setName("201 (201@192.168.100.233)");
        String resultString = "Non-Existant ID 4\n";
        m_parser.verifyMemberAction(resultString, member);        
    }
    
    @Test
    public void testVerifyMemberActionValid() {
        ActiveConferenceMember member = new ActiveConferenceMember();
        member.setName("201 (201@192.168.100.233)");        
        String resultString = "OK mute 1\n";
        Assert.assertTrue(m_parser.verifyMemberAction(resultString, member));
    }
    
    @Test(expected = NoSuchConferenceException.class)
    public void testVerifyConferenceActionInvalid() {
        Conference conference = new Conference();
        conference.setName("conference-301");
        String resultString = "Conference conference-301 not found\n";
        m_parser.verifyConferenceAction(resultString, conference);
    }
    
    @Test
    public void testVerifyConferenceActionValid() {
        Conference conference = new Conference();
        conference.setName("conference-301");
        String resultString =
            "2;sofia/eng.bluesocket.com/201@192.168.100.233;f69d2b1f-4841-40a4-8e0f-847d1aeef2f0;201;201;hear|speak;0;0;300\n" +
            "1;sofia/eng.bluesocket.com/200@192.168.100.233;a5b6cdbe-7cbf-48a7-a52d-98b871eb2491;Joe Attardi;200;hear|speak|floor;0;0;300\n";
        Assert.assertTrue(m_parser.verifyConferenceAction(resultString, conference));
    }
}
