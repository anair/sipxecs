package org.sipfoundry.sipxbridge;

/**
 * Symhis little utility is because java 5 does not support concurrent sets.
 * 
 */
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class ConcurrentSet implements Set<Sym> {

    ConcurrentHashMap<String, Sym> map = new ConcurrentHashMap<String, Sym>();
    private Bridge bridge;
    
    public ConcurrentSet(Bridge bridge) {
        this.bridge = bridge;
    }

    public boolean add(Sym element) {
        map.put(element.getId(), element);
        if ( element.getBridge() != null  && element.getBridge() != this.bridge) {
            element.getBridge().removeSym(element);
        }
        element.setBridge(this.bridge);
        return true;
    }

    public boolean addAll(Collection<? extends Sym> collection) {
        for (Sym t : collection) {
            this.add(t);
        }
        return true;
    }

    public void clear() {
        map.clear();
    }

    public boolean contains(Object obj) {
        return map.containsKey(((Sym)obj).getId());

    }

    public boolean containsAll(Collection<?> collection) {
        Collection<Sym> set = map.values();
        return set.containsAll(collection);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public Iterator<Sym> iterator() {

        return map.values().iterator();
    }

    public boolean remove(Object obj) {
        this.map.remove(((Sym)obj).getId());
        ((Sym)obj).setBridge(null);
        return true;
    }

    public boolean removeAll(Collection<?> collection) {
        for (Object obj : collection) {
            this.map.remove(obj);
        }
        return true;
    }

    public boolean retainAll(Collection<?> collection) {
        throw new UnsupportedOperationException("Unsupported");
    }

    public int size() {
        return map.size();
    }

    public Object[] toArray() {

        return this.map.keySet().toArray();
    }

    public  Sym[] toArray(Sym[] array) {
        return this.map.keySet().toArray(array);
    }

    public <T> T[] toArray(T[] array) {
        return map.values().toArray(array);
    }

}
