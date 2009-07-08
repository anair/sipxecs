package org.sipfoundry.sipcallwatcher;

import java.util.EventObject;

public class ResourceStateEvent extends EventObject {
    private static final long serialVersionUID = -7228675062866489237L;
    private ResourceState state;
    private String user ;
    
    public ResourceStateEvent(Object source) {
        super(source);
    }
    
    public ResourceStateEvent (Subscriber subscriber, String user, ResourceState newState) {
        this(subscriber);
        this.state = newState;
        this.user = user;
    }

    /**
     * @return the state
     */
    public ResourceState getState() {
        return state;
    }

    /**
     * @param user the user to set
     */
    public void setUser(String user) {
        this.user = user;
    }

    /**
     * @return the user
     */
    public String getUser() {
        return user;
    }



}
