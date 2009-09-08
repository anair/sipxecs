package org.sipfoundry.callcontroller;

import org.apache.log4j.Logger;
import org.restlet.Context;
import org.restlet.Filter;
import org.restlet.Route;
import org.restlet.Router;
import org.restlet.data.Request;
import org.sipfoundry.sipxrest.Plugin;
import org.sipfoundry.sipxrest.RestServer;



public class CallControllerPlugin extends Plugin {

    @Override
    public void attachContext(Filter filter, Context context, Router router) {
        filter.setNext(new CallControllerRestlet(context));
        String suffix = String.format("/{%s}/{%s}", CallControllerParams.CALLING_PARTY, CallControllerParams.CALLED_PARTY);
        Route route = router.attach(this.getMetaInf().getUriPrefix() + suffix,filter);
        route.extractQuery(CallControllerParams.METHOD, CallControllerParams.METHOD, true);
        route.extractQuery(CallControllerParams.AGENT,CallControllerParams.AGENT,true);
        route.extractQuery(CallControllerParams.FORWARDING_ALLOWED, CallControllerParams.FORWARDING_ALLOWED, true);
        route.extractQuery(CallControllerParams.SUBJECT, CallControllerParams.SUBJECT, true);
       

    }

    @Override
    public String getAgent(Request request) {
        String retval =  (String) request.getAttributes().get(CallControllerParams.AGENT);
        if ( retval == null ){
            retval =  (String) request.getAttributes().get(CallControllerParams.CALLING_PARTY);
        } 
        
        return retval;
    }

}
