/*
 * 
 * 
 * Copyright (C) 2009 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxivr;


// Copy from sipXconfig-neoconf-vm  Perhaps we can share one day?

/**
 * Final output format
 * <pre>
 * &lt;prefs&gt;
 *   &lt;activegreeting&gt;outofoffice&lt;/activegreeting&gt;
 *   &lt;notification&gt;
 *       &lt;contact type="email" attachments="no"&gt;dhubler@pingtel.com&lt;/contact&gt;
 *   &lt;/notification&gt;
 * &lt;/prefs&gt;
 * </pre>
 */
public class MailboxPreferences {
    public static final String EMAIL_PROP = "emailAddress";
    private GreetingType m_activeGreeting = GreetingType.NONE;
    private String m_emailAddress;
    
    private boolean m_useTLS;
    private Integer m_IMAPPortNum;
    private String  m_IMAPServer;
    private boolean m_Synchronize;
    private String  m_emailPassword;
    
    private boolean m_attachVoicemailToEmail;
    private String m_alternateEmailAddress;
    private boolean m_attachVoicemailToAlternateEmail;
         
    public enum GreetingType {
        NONE("none"), 
        STANDARD("standard"), 
        OUT_OF_OFFICE("outofoffice"), 
        EXTENDED_ABSENCE("extendedabsence");
        
        private String m_id;
        
        GreetingType(String id) {
            m_id = id;
        }
        
        public String getId() {
            return m_id;
        }
        
        public static GreetingType valueOfById(String id) {
            for (GreetingType greeting : GreetingType.values()) {
                if (greeting.getId().equals(id)) {
                    return greeting;
                }
            }
            throw new IllegalArgumentException("id not recognized " + id);
        }
    }
    
    public GreetingType getActiveGreeting() {
        return m_activeGreeting;
    }
    
    public void setActiveGreeting(GreetingType greetingType) {
        m_activeGreeting = greetingType;
    }
    
    public boolean isAttachVoicemailToEmail() {
        return m_attachVoicemailToEmail;
    }
    
    public void setAttachVoicemailToEmail(boolean attachVoicemailToEmail) {
        m_attachVoicemailToEmail = attachVoicemailToEmail;
    }
    
    public String getEmailAddress() {
        return m_emailAddress;
    }
    
    public String getEmailUserName() {
        return m_emailAddress.split("@")[0];
    }
    
    public void setEmailAddress(String emailAddress) {
        m_emailAddress = emailAddress;
    }

    public String getAlternateEmailAddress() {
        return m_alternateEmailAddress;
    }

    public void setAlternateEmailAddress(String alternateEmailAddress) {
        m_alternateEmailAddress = alternateEmailAddress;
    }

    public boolean isAttachVoicemailToAlternateEmail() {
        return m_attachVoicemailToAlternateEmail;
    }

    public void setAttachVoicemailToAlternateEmail(boolean attachVoicemailToAlternateEmail) {
        m_attachVoicemailToAlternateEmail = attachVoicemailToAlternateEmail;
    }
    
    public boolean synchronize() {
        return m_Synchronize;
    }
    
    public void setSynchronize(boolean synchronize) {
        m_Synchronize = synchronize;
    }
    
    public String getemailPassword() {
        return m_emailPassword;
    }
    
    public void setemailPassword(String emailPassword) {
        m_emailPassword = emailPassword;
    }
    
    public String getIMAPServer() {
        return m_IMAPServer;
    }
    
    public void setIMAPServer(String IMAPServer) {
        m_IMAPServer = IMAPServer;
    }
    
    public Integer getIMAPPortNum() {
        return m_IMAPPortNum;
    }
    
    public void setIMAPPortNum(Integer IMAPPortNum) {
        m_IMAPPortNum = IMAPPortNum;
    }
    
    public boolean useTLS() {
        return m_useTLS;
    }
    
    public void setUseTLS(boolean useTLS) {
        m_useTLS = useTLS;
    }
    
}
