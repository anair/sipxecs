/*
 *
 *
 * Copyright (C) 2008 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 *
 */
package org.sipfoundry.sipxconfig.admin.commserver;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;
import org.sipfoundry.sipxconfig.common.SipxHibernateDaoSupport;
import org.sipfoundry.sipxconfig.common.event.DaoEventPublisher;
import org.sipfoundry.sipxconfig.service.SipxService;
import org.springframework.orm.hibernate3.HibernateCallback;

import static org.springframework.dao.support.DataAccessUtils.singleResult;

public class LocationsManagerImpl extends SipxHibernateDaoSupport<Location> implements LocationsManager {

    private static final String LOCATION_PROP_NAME = "fqdn";
    private static final String LOCATION_PROP_PRIMARY = "primary";

    private DaoEventPublisher m_daoEventPublisher;

    public void setDaoEventPublisher(DaoEventPublisher daoEventPublisher) {
        m_daoEventPublisher = daoEventPublisher;
    }

    /** Return the replication URLs, retrieving them on demand */
    public Location[] getLocations() {
        List<Location> locationList = getHibernateTemplate().loadAll(Location.class);
        Location[] locationArray = new Location[locationList.size()];
        locationList.toArray(locationArray);
        return locationArray;
    }

    public Location getLocation(int id) {
        return (Location) getHibernateTemplate().load(Location.class, id);
    }

    private List<Location> getLocationsByBundle(String bundle) {
        List<Location> locations = getHibernateTemplate().
            findByNamedQueryAndNamedParam("locationsByBundle", "locationBundle", bundle);
        return locations;
    }

    public Location getLocationByFqdn(String fqdn) {
        return loadLocationByUniqueProperty(LOCATION_PROP_NAME, fqdn);
    }

    public Location getLocationByAddress(String address) {
        return loadLocationByUniqueProperty("address", address);
    }

    private Location loadLocationByUniqueProperty(String propName, Object propValue) {
        final Criterion expression = Restrictions.eq(propName, propValue);

        HibernateCallback callback = new HibernateCallback() {
            public Object doInHibernate(Session session) {
                Criteria criteria = session.createCriteria(Location.class).add(expression);
                return criteria.list();
            }
        };
        List<Location> locations = getHibernateTemplate().executeFind(callback);
        Location location = (Location) singleResult(locations);

        return location;
    }

    public void storeMigratedLocation(Location location) {
        getHibernateTemplate().saveOrUpdate(location);
    }

    public void storeLocation(Location location) {
        if (location.isNew()) {
            getHibernateTemplate().save(location);
            m_daoEventPublisher.publishSave(location);
        } else {
            m_daoEventPublisher.publishSave(location);
            getHibernateTemplate().update(location);
        }        
    }

    public void deleteLocation(Location location) {
        m_daoEventPublisher.publishDelete(location);
        getHibernateTemplate().delete(location);
    }

    public Location getPrimaryLocation() {
        return loadLocationByUniqueProperty(LOCATION_PROP_PRIMARY, true);
    }

    public List <Location> getLocationsForService(SipxService service) {
        List<Location> locations = new ArrayList<Location>();
        for (Location location : getLocations()) {
            if (location.isServiceInstalled(service)) {
                locations.add(location);
            }
        }
        return locations;
    }
}
