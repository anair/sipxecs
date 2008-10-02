/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;
import org.sipfoundry.sipxconfig.admin.NameInUseException;
import org.sipfoundry.sipxconfig.alias.AliasManager;
import org.sipfoundry.sipxconfig.common.SpecialUser.SpecialUserType;
import org.sipfoundry.sipxconfig.common.event.DaoEventListener;
import org.sipfoundry.sipxconfig.common.event.DaoEventPublisher;
import org.sipfoundry.sipxconfig.domain.DomainManager;
import org.sipfoundry.sipxconfig.permission.PermissionName;
import org.sipfoundry.sipxconfig.setting.Group;
import org.sipfoundry.sipxconfig.setting.SettingDao;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.orm.hibernate3.HibernateCallback;

public abstract class CoreContextImpl extends SipxHibernateDaoSupport implements CoreContext,
        DaoEventListener {
    
    public static final String CONTEXT_BEAN_NAME = "coreContextImpl";
    private static final int SIP_PASSWORD_LEN = 8;
    private static final String USERNAME_PROP_NAME = "userName";
    private static final String VALUE = "value";
    /** nothing special about this name */
    private static final String ADMIN_GROUP_NAME = "administrators";
    private static final String QUERY_USER_BY_NAME_OR_ALIAS = "userByNameOrAlias";
    private static final String QUERY_USER_IDS_BY_NAME_OR_ALIAS = "userIdsByNameOrAlias";
    private static final String QUERY_USER = "from User";
    private static final String QUERY_PARAM_GROUP_ID = "groupId";

    private DomainManager m_domainManager;
    private SettingDao m_settingDao;
    private DaoEventPublisher m_daoEventPublisher;
    private AliasManager m_aliasManager;
    private boolean m_debug;

    /** limit number of users */
    private int m_maxUserCount = -1;

    public CoreContextImpl() {
        super();
    }

    /**
     * Implemented by Spring lookup-method injection
     */
    public abstract User newUser();

    public boolean getDebug() {
        return m_debug;
    }

    public void setDebug(boolean debug) {
        m_debug = debug;
    }

    public String getAuthorizationRealm() {
        return m_domainManager.getAuthorizationRealm();
    }

    public void setMaxUserCount(int maxUserCount) {
        m_maxUserCount = maxUserCount;
    }

    public String getDomainName() {
        return m_domainManager.getDomain().getName();
    }

    public void setDaoEventPublisher(DaoEventPublisher daoEventPublisher) {
        m_daoEventPublisher = daoEventPublisher;
    }

    public void setAliasManager(AliasManager aliasManager) {
        m_aliasManager = aliasManager;
    }

    public boolean saveUser(User user) {
        boolean newUserName = user.isNew();
        String dup = checkForDuplicateNameOrAlias(user);
        if (dup != null) {
            throw new NameInUseException(dup);
        }

        checkMaxUsers(user, m_maxUserCount);

        if (!user.isNew()) {
            String origUserName = (String) getOriginalValue(user, USERNAME_PROP_NAME);
            if (!origUserName.equals(user.getUserName())) {
                newUserName = true;
                String origPintoken = (String) getOriginalValue(user, "pintoken");
                if (origPintoken.equals(user.getPintoken())) {
                    throw new ChangePintokenRequiredException(
                            "When changing user name, you must also change PIN");
                }
            }
        }

        getHibernateTemplate().saveOrUpdate(user);
        return newUserName;
    }

    /**
     * Check that the system has been restricted to a certain number of users
     * 
     * @param maxUserCount -1 or represent infinite number
     */
    void checkMaxUsers(User user, int maxUserCount) {
        // allow edits to the Nth (or beyond) user
        if (!user.isNew()) {
            return;
        }

        if (maxUserCount < 0) {
            return;
        }

        int count = getUsersCount();
        if (count >= maxUserCount) {
            throw new MaxUsersException(m_maxUserCount);
        }
    }

    static class MaxUsersException extends UserException {
        MaxUsersException(int maxCount) {
            super("You cannot exceed the maximum number of allowed users: " + maxCount);
        }
    }

    public static class ChangePintokenRequiredException extends UserException {
        public ChangePintokenRequiredException(String msg) {
            super(msg);
        }
    }

    public void deleteUser(User user) {
        getHibernateTemplate().delete(user);
    }

    public void deleteUsers(Collection<Integer> userIds) {
        if (userIds.isEmpty()) {
            // no users to delete => nothing to do
            return;
        }
        List<User> users = new ArrayList<User>(userIds.size());
        for (Integer id : userIds) {
            User user = loadUser(id);
            users.add(user);
            m_daoEventPublisher.publishDelete(user);
        }
        getHibernateTemplate().deleteAll(users);
    }

    public void deleteUsersByUserName(Collection<String> userNames) {
        if (userNames.isEmpty()) {
            // no users to delete => nothing to do
            return;
        }
        List users = new ArrayList(userNames.size());
        for (String userName : userNames) {
            User user = loadUserByUserName(userName);
            users.add(user);
            m_daoEventPublisher.publishDelete(user);
        }
        getHibernateTemplate().deleteAll(users);
    }

    public User loadUser(Integer id) {
        User user = (User) getHibernateTemplate().load(User.class, id);

        return user;
    }

    public User loadUserByUserName(String userName) {
        return loadUserByUniqueProperty(USERNAME_PROP_NAME, userName);
    }

    private User loadUserByUniqueProperty(String propName, String propValue) {
        final Criterion expression = Restrictions.eq(propName, propValue);

        HibernateCallback callback = new HibernateCallback() {
            public Object doInHibernate(Session session) {
                Criteria criteria = session.createCriteria(User.class).add(expression);
                return criteria.list();
            }
        };
        List users = getHibernateTemplate().executeFind(callback);
        User user = (User) DaoUtils.requireOneOrZero(users, expression.toString());

        return user;
    }

    public User loadUserByAlias(String alias) {
        return loadUserByNamedQueryAndNamedParam("userByAlias", VALUE, alias);
    }

    public User loadUserByUserNameOrAlias(String userNameOrAlias) {
        return loadUserByNamedQueryAndNamedParam(QUERY_USER_BY_NAME_OR_ALIAS, VALUE,
                userNameOrAlias);
    }

    /**
     * Check whether the user has a username or alias that collides with an existing username or
     * alias. Check for internal collisions as well, for example, the user has an alias that is
     * the same as the username. (Duplication within the aliases is not possible because the
     * aliases are stored as a Set.) If there is a collision, then return the bad name (username
     * or alias). Otherwise return null. If there are multiple collisions, then it's arbitrary
     * which name is returned.
     * 
     * @param user user to test
     * @return name that collides
     */
    public String checkForDuplicateNameOrAlias(User user) {
        String result = null;

        // Check for duplication within the user itself
        List names = new ArrayList(user.getAliases());
        String userName = user.getUserName();
        names.add(userName);
        result = checkForDuplicateString(names);
        if (result == null) {
            // Check whether the userName is a duplicate.
            if (!m_aliasManager.canObjectUseAlias(user, userName)) {
                result = userName;
            } else {
                // Check the aliases and return any duplicate as a bad name.
                for (String alias : user.getAliases()) {
                    if (!m_aliasManager.canObjectUseAlias(user, alias)) {
                        result = alias;
                        break;
                    }
                }
            }
        }

        return result;
    }

    /**
     * Given a collection of strings, look for duplicates. Return the first duplicate found, or
     * null if all strings are unique.
     */
    String checkForDuplicateString(Collection<String> strings) {
        Set<String> set = new TreeSet<String>();
        for (String str : strings) {
            if (!set.add(str)) {
                return str;
            }
        }
        return null;
    }

    private User loadUserByNamedQueryAndNamedParam(String queryName, String paramName,
            Object value) {
        Collection usersColl = getHibernateTemplate().findByNamedQueryAndNamedParam(queryName,
                paramName, value);
        Set users = new HashSet(usersColl); // eliminate duplicates
        if (users.size() > 1) {
            throw new IllegalStateException(
                    "The database has more than one user matching the query " + queryName
                            + ", paramName = " + paramName + ", value = " + value);
        }
        User user = null;
        if (users.size() > 0) {
            user = (User) users.iterator().next();
        }
        return user;
    }

    /**
     * Return all users matching the userTemplate example. Empty properties of userTemplate are
     * ignored in the search. The userName property matches either the userName or aliases
     * properties.
     */
    public List<User> loadUserByTemplateUser(final User userTemplate) {
        HibernateCallback callback = new HibernateCallback() {
            public Object doInHibernate(Session session) {
                UserLoader loader = new UserLoader(session);
                return loader.loadUsers(userTemplate);
            }
        };
        List<User> users = getHibernateTemplate().executeFind(callback);
        return users;
    }

    public List<User> loadUsers() {
        return getHibernateTemplate().loadAll(User.class);
    }

    public int getUsersCount() {
        return getUsersInGroupCount(null);
    }

    public int getUsersInGroupCount(Integer groupId) {
        return getBeansInGroupCount(User.class, groupId);
    }

    public int getUsersInGroupWithSearchCount(final Integer groupId, final String searchString) {
        int numUsers = 0;
        if (!StringUtils.isEmpty(searchString)) {
            HibernateCallback callback = new HibernateCallback() {
                public Object doInHibernate(Session session) {
                    UserLoader loader = new UserLoader(session);
                    return loader.countUsers(searchString, groupId);
                }
            };
            Integer count = (Integer) getHibernateTemplate().execute(callback);
            numUsers = count.intValue();
        } else {
            numUsers = getUsersInGroupCount(groupId);
        }
        return numUsers;
    }

    public List<User> loadUsersByPage(final String search, final Integer groupId,
            final int firstRow, final int pageSize, final String orderBy,
            final boolean orderAscending) {
        HibernateCallback callback = new HibernateCallback() {
            public Object doInHibernate(Session session) {
                UserLoader loader = new UserLoader(session);
                return loader.loadUsersByPage(search, groupId, firstRow, pageSize, orderBy,
                        orderAscending);
            }
        };
        List<User> users = getHibernateTemplate().executeFind(callback);
        return users;
    }

    public void clear() {
        Collection c = getHibernateTemplate().find(QUERY_USER);
        getHibernateTemplate().deleteAll(c);
    }

    /**
     * Create a superadmin user with an empty pin. This is used to recover from the loss of all
     * users from the database.
     */
    public void createAdminGroupAndInitialUserTask() {
        createAdminGroupAndInitialUser(null);
    }

    /**
     * Create a superadmin user with the specified pin.
     * 
     * Map an empty pin to an empty pintoken as a special hack allowing the empty pin to be used
     * when an insecure, easy to remember pin is needed. Previously we used 'password' rather than
     * the empty string, relying on another hack that allowed the password and pintoken to be the
     * same. That hack is gone so setting the pintoken to 'password' would no longer work because
     * the password would then be the inverse hash of 'password' rather than 'password'.
     */
    public void createAdminGroupAndInitialUser(String pin) {
        Group adminGroup = m_settingDao.getGroupByName(User.GROUP_RESOURCE_ID, ADMIN_GROUP_NAME);
        if (adminGroup == null) {
            adminGroup = new Group();
            adminGroup.setName(ADMIN_GROUP_NAME);
            adminGroup.setResource(User.GROUP_RESOURCE_ID);
            adminGroup.setDescription("Users with superadmin privileges");
        }
        PermissionName.SUPERADMIN.setEnabled(adminGroup, true);
        PermissionName.TUI_CHANGE_PIN.setEnabled(adminGroup, false);

        m_settingDao.saveGroup(adminGroup);

        User admin = loadUserByUserName(User.SUPERADMIN);
        if (admin == null) {
            admin = new User();
            admin.setUserName(User.SUPERADMIN);
            
            // currently superadmin cannot invite to a conference without a valid sip password
            admin.setSipPassword(RandomStringUtils.randomAlphanumeric(SIP_PASSWORD_LEN));
            
            if (StringUtils.isEmpty(pin)) {
                admin.setPintoken("");
            } else {
                admin.setPin(pin, getAuthorizationRealm());
            }
        } else {
            // if superadmin user already exists make sure it has superadmin permission
            admin.setPermission(PermissionName.SUPERADMIN, true);
        }

        admin.addGroup(adminGroup);
        saveUser(admin);
    }

    public void setSettingDao(SettingDao settingDao) {
        m_settingDao = settingDao;
    }

    public List<Group> getGroups() {
        return m_settingDao.getGroups(USER_GROUP_RESOURCE_ID);
    }

    public Group getGroupByName(String userGroupName, boolean createIfNotFound) {
        if (createIfNotFound) {
            return m_settingDao.getGroupCreateIfNotFound(USER_GROUP_RESOURCE_ID, userGroupName);
        }
        return m_settingDao.getGroupByName(USER_GROUP_RESOURCE_ID, userGroupName);
    }

    public Collection getAliasMappings() {
        List aliases = new ArrayList();
        for (User user : loadUsers()) {
            aliases.addAll(user.getAliasMappings(getDomainName()));
        }
        return aliases;
    }

    public Collection<User> getGroupMembers(Group group) {
        Collection<User> users = getHibernateTemplate().findByNamedQueryAndNamedParam(
                "userGroupMembers", QUERY_PARAM_GROUP_ID, group.getId());
        return users;
    }

    public Collection<String> getGroupMembersNames(Group group) {
        Collection<String> userNames = getHibernateTemplate().findByNamedQueryAndNamedParam(
                "userNamesGroupMembers", QUERY_PARAM_GROUP_ID, group.getId());
        return userNames;
    }

    public void onDelete(Object entity) {
        if (entity instanceof Group) {
            Group group = (Group) entity;
            getHibernateTemplate().update(group);
            if (User.GROUP_RESOURCE_ID.equals(group.getResource())) {
                Collection<User> users = getGroupMembers(group);
                for (User user : users) {
                    Object[] ids = new Object[] {
                        group.getId()
                    };
                    DataCollectionUtil.removeByPrimaryKey(user.getGroups(), ids);
                    Set supervisors = user.getSupervisorForGroups();
                    if (supervisors != null) {
                        DataCollectionUtil.removeByPrimaryKey(supervisors, ids);
                    }

                    saveUser(user);
                }

                Collection<User> groupSupervisors = getGroupSupervisors(group);
                for (User user : groupSupervisors) {
                    Set supervisors = user.getSupervisorForGroups();
                    if (supervisors != null) {
                        Object[] ids = new Object[] {
                            group.getId()
                        };
                        DataCollectionUtil.removeByPrimaryKey(supervisors, ids);
                    }

                    saveUser(user);
                }
                // TODO
            }
        }
    }

    public void onSave(Object entity_) {
    }

    public boolean isAliasInUse(String alias) {
        // Look for the ID of a user with a user ID or user alias matching the specified SIP
        // alias.
        // If there is one, then the alias is in use.
        List objs = getHibernateTemplate().findByNamedQueryAndNamedParam(
                QUERY_USER_IDS_BY_NAME_OR_ALIAS, VALUE, alias);
        return SipxCollectionUtils.safeSize(objs) > 0;
    }

    public Collection getBeanIdsOfObjectsWithAlias(String alias) {
        Collection ids = getHibernateTemplate().findByNamedQueryAndNamedParam(
                QUERY_USER_IDS_BY_NAME_OR_ALIAS, VALUE, alias);
        Collection bids = BeanId.createBeanIdCollection(ids, User.class);
        return bids;
    }

    public void addToGroup(Integer groupId, Collection ids) {
        DaoUtils.addToGroup(getHibernateTemplate(), groupId, User.class, ids);
    }

    public void removeFromGroup(Integer groupId, Collection ids) {
        DaoUtils.removeFromGroup(getHibernateTemplate(), groupId, User.class, ids);
    }

    public List<User> getGroupSupervisors(Group group) {
        List<User> objs = getHibernateTemplate().findByNamedQueryAndNamedParam(
                "groupSupervisors", QUERY_PARAM_GROUP_ID, group.getId());
        return objs;
    }

    public List<User> getUsersThatISupervise(User supervisor) {
        List<User> objs = getHibernateTemplate().findByNamedQueryAndNamedParam(
                "usersThatISupervise", "supervisorId", supervisor.getId());
        return objs;
    }

    public void setDomainManager(DomainManager domainManager) {
        m_domainManager = domainManager;
    }

    /**
     * Given a collection of extensions, looks for invalid user or user without a specified
     * permission. Throw a exception if an invalid extension found.
     * 
     * @param list of user aliases
     * @param permission permission to check
     * @throws ExtensionException if at least one of the aliases does not represent a valid user
     *         with permission enabled
     */
    public void checkForValidExtensions(Collection<String> aliases, PermissionName permission) {
        Collection<String> invalidExtensions = new ArrayList<String>();
        for (String extension : aliases) {
            User user = loadUserByUserNameOrAlias(extension);
            if (user == null) {
                invalidExtensions.add(extension);
            } else if (!user.hasPermission(permission)) {
                invalidExtensions.add(extension);
            }
        }
        if (!invalidExtensions.isEmpty()) {
            throw new ExtensionException(permission, invalidExtensions);
        }
    }

    static class ExtensionException extends UserException {
        private static final String ERROR = "The following extensions do not exist or do not have {0} permission: {1}.";

        ExtensionException(PermissionName permission, Collection<String> invalidExtensions) {
            super(ERROR, permission.getName(), StringUtils.join(invalidExtensions, ", "));
        }
    }

    public User getSpecialUser(SpecialUserType specialUserType) {
        List<SpecialUser> specialUsersOfType = getHibernateTemplate()
                .findByNamedQueryAndNamedParam("specialUserByType", "specialUserType",
                        specialUserType.name());
        SpecialUser specialUser = (SpecialUser) DataAccessUtils.singleResult(specialUsersOfType);
        if (specialUser == null) {
            return null;
        }

        User newUser = newUser();
        newUser.setUserName(specialUser.getUserName());
        newUser.setSipPassword(specialUser.getSipPassword());
        return newUser;
    }

    public void initializeSpecialUsers() {
        for (SpecialUserType type : SpecialUserType.values()) {
            User specialUser = getSpecialUser(type);
            if (specialUser == null) {
                SpecialUser newSpecialUser = new SpecialUser(type);
                getHibernateTemplate().saveOrUpdate(newSpecialUser);
            }
        }
    }
}
