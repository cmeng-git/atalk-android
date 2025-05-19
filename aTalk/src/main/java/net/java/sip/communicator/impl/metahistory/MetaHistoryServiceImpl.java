/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.metahistory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import net.java.sip.communicator.service.callhistory.CallHistoryService;
import net.java.sip.communicator.service.callhistory.CallPeerRecord;
import net.java.sip.communicator.service.callhistory.CallRecord;
import net.java.sip.communicator.service.callhistory.event.CallHistorySearchProgressListener;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.history.event.HistorySearchProgressListener;
import net.java.sip.communicator.service.history.event.ProgressEvent;
import net.java.sip.communicator.service.metahistory.MetaHistoryService;
import net.java.sip.communicator.service.msghistory.MessageHistoryService;
import net.java.sip.communicator.service.msghistory.event.MessageHistorySearchProgressListener;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveredEvent;
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageReceivedEvent;
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent;
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

import timber.log.Timber;

/**
 * The Meta History Service is wrapper around the other known history services. Query them all at
 * once, sort the result and return all merged records in one collection.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class MetaHistoryServiceImpl implements MetaHistoryService, ServiceListener {
    /**
     * The BundleContext that we got from the OSGI bus.
     */
    private BundleContext bundleContext = null;

    /**
     * Caching of the used services
     */
    private final Hashtable<String, Object> services = new Hashtable<>();

    private final List<HistorySearchProgressListener> progressListeners = new ArrayList<>();

    /**
     * Returns all the records for the descriptor after the given date.
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param startDate Date the date of the first record to return
     *
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    public Collection<Object> findByStartDate(String[] services, Object descriptor, Date startDate) {
        MessageProgressWrapper listenWrapper = new MessageProgressWrapper(services.length);

        TreeSet<Object> result = new TreeSet<>(new RecordsComparator());
        for (int i = 0; i < services.length; i++) {
            String name = services[i];
            Object serv = getService(name);
            if (serv instanceof MessageHistoryService) {
                MessageHistoryService mhs = (MessageHistoryService) serv;
                listenWrapper.setIx(i);
                mhs.addSearchProgressListener(listenWrapper);

                if (descriptor instanceof MetaContact) {
                    result.addAll(mhs.findByStartDate((MetaContact) descriptor, startDate));
                }
                else if (descriptor instanceof ChatRoom) {
                    result.addAll(mhs.findByStartDate((ChatRoom) descriptor, startDate));
                }
                mhs.removeSearchProgressListener(listenWrapper);
            }
            else if (serv instanceof CallHistoryService) {
                CallHistoryService chs = (CallHistoryService) serv;
                listenWrapper.setIx(i);
                chs.addSearchProgressListener(listenWrapper);
                result.addAll(chs.findByStartDate(startDate));
                chs.removeSearchProgressListener(listenWrapper);
            }
        }
        listenWrapper.fireLastProgress(startDate, null, null);
        return result;
    }

    /**
     * Returns all the records before the given date
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param endDate Date the date of the last record to return
     *
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    public Collection<Object> findByEndDate(String[] services, Object descriptor, Date endDate) {
        MessageProgressWrapper listenWrapper = new MessageProgressWrapper(services.length);

        TreeSet<Object> result = new TreeSet<>(new RecordsComparator());
        for (int i = 0; i < services.length; i++) {
            String name = services[i];
            Object serv = getService(name);
            if (serv instanceof MessageHistoryService) {
                MessageHistoryService mhs = (MessageHistoryService) serv;
                listenWrapper.setIx(i);
                mhs.addSearchProgressListener(listenWrapper);

                if (descriptor instanceof MetaContact) {
                    result.addAll(mhs.findByEndDate((MetaContact) descriptor, endDate));
                }
                else if (descriptor instanceof ChatRoom) {
                    result.addAll(mhs.findByEndDate((ChatRoom) descriptor, endDate));
                }
                mhs.removeSearchProgressListener(listenWrapper);
            }
            else if (serv instanceof CallHistoryService) {
                CallHistoryService chs = (CallHistoryService) serv;
                listenWrapper.setIx(i);
                chs.addSearchProgressListener(listenWrapper);
                result.addAll(chs.findByEndDate(endDate));
                chs.removeSearchProgressListener(listenWrapper);
            }
        }
        listenWrapper.fireLastProgress(null, endDate, null);
        return result;
    }

    /**
     * Returns all the records between the given dates
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param startDate Date the date of the first record to return
     * @param endDate Date the date of the last record to return
     *
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    public Collection<Object> findByPeriod(String[] services, Object descriptor, Date startDate, Date endDate) {
        MessageProgressWrapper listenWrapper = new MessageProgressWrapper(services.length);

        LinkedList<Object> result = new LinkedList<>();
        for (int i = 0; i < services.length; i++) {
            String name = services[i];
            Object serv = getService(name);
            if (serv instanceof MessageHistoryService) {
                MessageHistoryService mhs = (MessageHistoryService) serv;
                listenWrapper.setIx(i);
                mhs.addSearchProgressListener(listenWrapper);

                if (descriptor instanceof MetaContact) {
                    result.addAll(mhs.findByPeriod((MetaContact) descriptor, startDate, endDate));
                }
                else if (descriptor instanceof ChatRoom) {
                    result.addAll(mhs.findByPeriod((ChatRoom) descriptor, startDate, endDate));
                }
                mhs.removeSearchProgressListener(listenWrapper);
            }
            else if (serv instanceof CallHistoryService) {
                CallHistoryService chs = (CallHistoryService) serv;
                listenWrapper.setIx(i);
                chs.addSearchProgressListener(listenWrapper);
                result.addAll(chs.findByPeriod(startDate, endDate));
                chs.removeSearchProgressListener(listenWrapper);
            }
        }
        listenWrapper.fireLastProgress(startDate, endDate, null);

        Collections.sort(result, new RecordsComparator());
        return result;
    }

    /**
     * Returns all the records between the given dates and having the given keywords
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param startDate Date the date of the first record to return
     * @param endDate Date the date of the last record to return
     * @param keywords array of keywords
     *
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    public Collection<Object> findByPeriod(String[] services, Object descriptor, Date startDate,
            Date endDate, String[] keywords) {
        return findByPeriod(services, descriptor, startDate, endDate, keywords, false);
    }

    /**
     * Returns all the records between the given dates and having the given keywords
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param startDate Date the date of the first record to return
     * @param endDate Date the date of the last record to return
     * @param keywords array of keywords
     * @param caseSensitive is keywords search case sensitive
     *
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    public Collection<Object> findByPeriod(String[] services, Object descriptor, Date startDate,
            Date endDate, String[] keywords, boolean caseSensitive) {
        MessageProgressWrapper listenWrapper = new MessageProgressWrapper(services.length);

        TreeSet<Object> result = new TreeSet<>(new RecordsComparator());
        for (int i = 0; i < services.length; i++) {
            String name = services[i];
            Object serv = getService(name);
            if (serv instanceof MessageHistoryService) {
                MessageHistoryService mhs = (MessageHistoryService) serv;
                listenWrapper.setIx(i);
                mhs.addSearchProgressListener(listenWrapper);

                if (descriptor instanceof MetaContact) {
                    result.addAll(mhs.findByPeriod((MetaContact) descriptor, startDate, endDate, keywords, caseSensitive));
                }
                else if (descriptor instanceof ChatRoom) {
                    result.addAll(mhs.findByPeriod((ChatRoom) descriptor, startDate, endDate, keywords, caseSensitive));
                }
                mhs.removeSearchProgressListener(listenWrapper);
            }
            else if (serv instanceof CallHistoryService) {
                CallHistoryService chs = (CallHistoryService) serv;
                listenWrapper.setIx(i);
                chs.addSearchProgressListener(listenWrapper);
                Collection<CallRecord> cs = chs.findByPeriod(startDate, endDate);

                for (CallRecord callRecord : cs) {
                    if (matchCallPeer(callRecord.getPeerRecords(), keywords, caseSensitive))
                        result.add(callRecord);
                }
                chs.removeSearchProgressListener(listenWrapper);
            }
        }
        listenWrapper.fireLastProgress(startDate, endDate, keywords);

        return result;
    }

    /**
     * Returns all the records having the given keyword
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param keyword keyword
     *
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    public Collection<Object> findByKeyword(String[] services, Object descriptor, String keyword) {
        return findByKeyword(services, descriptor, keyword, false);
    }

    /**
     * Returns all the records having the given keyword
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param keyword keyword
     * @param caseSensitive is keywords search case sensitive
     *
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    public Collection<Object> findByKeyword(String[] services, Object descriptor, String keyword, boolean caseSensitive) {
        return findByKeywords(services, descriptor, new String[]{keyword}, caseSensitive);
    }

    /**
     * Returns all the records having the given keywords
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param keywords keyword
     *
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    public Collection<Object> findByKeywords(String[] services, Object descriptor, String[] keywords) {
        return findByKeywords(services, descriptor, keywords, false);
    }

    /**
     * Returns all the records having the given keywords
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param keywords keyword
     * @param caseSensitive is keywords search case sensitive
     *
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    public Collection<Object> findByKeywords(String[] services, Object descriptor, String[] keywords, boolean caseSensitive) {
        MessageProgressWrapper listenWrapper = new MessageProgressWrapper(services.length);

        TreeSet<Object> result = new TreeSet<>(new RecordsComparator());
        for (int i = 0; i < services.length; i++) {
            String name = services[i];
            Object serv = getService(name);
            if (serv instanceof MessageHistoryService) {
                MessageHistoryService mhs = (MessageHistoryService) serv;
                listenWrapper.setIx(i);
                mhs.addSearchProgressListener(listenWrapper);

                if (descriptor instanceof MetaContact) {
                    result.addAll(mhs.findByKeywords((MetaContact) descriptor, keywords, caseSensitive));
                }
                else if (descriptor instanceof ChatRoom) {
                    result.addAll(mhs.findByKeywords((ChatRoom) descriptor, keywords, caseSensitive));
                }
                mhs.removeSearchProgressListener(listenWrapper);
            }
            else if (serv instanceof CallHistoryService) {
                CallHistoryService chs = (CallHistoryService) serv;
                listenWrapper.setIx(i);
                chs.addSearchProgressListener(listenWrapper);

                // this will get all call records
                Collection<CallRecord> cs = chs.findByEndDate(new Date());

                for (CallRecord callRecord : cs) {
                    if (matchCallPeer(callRecord.getPeerRecords(), keywords, caseSensitive))
                        result.add(callRecord);
                }
                chs.removeSearchProgressListener(listenWrapper);
            }
        }
        listenWrapper.fireLastProgress(null, null, keywords);
        return result;
    }

    /**
     * Returns the supplied number of recent records.
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param count messages count
     *
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    public Collection<Object> findLast(String[] services, Object descriptor, int count) {
        MessageProgressWrapper listenWrapper = new MessageProgressWrapper(services.length);
        TreeSet<Object> result = new TreeSet<>(new RecordsComparator());

        for (int i = 0; i < services.length; i++) {
            String name = services[i];
            Object serv = getService(name);
            if (serv instanceof MessageHistoryService) {
                MessageHistoryService mhs = (MessageHistoryService) serv;
                listenWrapper.setIx(i);
                mhs.addSearchProgressListener(listenWrapper);

                // will also get fileHistory for metaContact and chatRoom
                if (descriptor instanceof MetaContact) {
                    result.addAll(mhs.findLast((MetaContact) descriptor, count));
                }
                else if (descriptor instanceof ChatRoom) {
                    result.addAll(mhs.findLast((ChatRoom) descriptor, count));
                }
                mhs.removeSearchProgressListener(listenWrapper);
            }
            else if (serv instanceof CallHistoryService) {
                CallHistoryService chs = (CallHistoryService) serv;
                listenWrapper.setIx(i);
                chs.addSearchProgressListener(listenWrapper);
                result.addAll(chs.findLast(count));
                chs.removeSearchProgressListener(listenWrapper);
            }
        }
        listenWrapper.fireLastProgress(null, null, null);

        LinkedList<Object> resultAsList = new LinkedList<>(result);
        int startIndex = resultAsList.size() - count;

        if (startIndex < 0)
            startIndex = 0;
        return resultAsList.subList(startIndex, resultAsList.size());
    }

    /**
     * Returns the supplied number of recent records after the given date
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param date messages after date
     * @param count messages count
     *
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    public Collection<Object> findFirstMessagesAfter(String[] services, Object descriptor, Date date, int count) {
        MessageProgressWrapper listenWrapper = new MessageProgressWrapper(services.length);

        TreeSet<Object> result = new TreeSet<>(new RecordsComparator());
        for (int i = 0; i < services.length; i++) {
            String name = services[i];
            Object serv = getService(name);
            if (serv instanceof MessageHistoryService) {
                MessageHistoryService mhs = (MessageHistoryService) serv;
                listenWrapper.setIx(i);
                mhs.addSearchProgressListener(listenWrapper);

                if (descriptor instanceof MetaContact) {
                    result.addAll(mhs.findFirstMessagesAfter((MetaContact) descriptor, date, count));
                }
                else if (descriptor instanceof ChatRoom) {
                    result.addAll(mhs.findFirstMessagesAfter((ChatRoom) descriptor, date, count));
                }
                mhs.removeSearchProgressListener(listenWrapper);
            }
            else if (serv instanceof CallHistoryService) {
                CallHistoryService chs = (CallHistoryService) serv;
                listenWrapper.setIx(i);
                chs.addSearchProgressListener(listenWrapper);

                Collection<CallRecord> col = chs.findByStartDate(date);
                if (col.size() > count) {
                    // before we make a sublist make sure there are sorted in the right order
                    List<CallRecord> l = new LinkedList<>(col);
                    Collections.sort(l, new RecordsComparator());
                    result.addAll(l.subList(0, count));
                }
                else
                    result.addAll(col);
                chs.removeSearchProgressListener(listenWrapper);
            }
        }
        listenWrapper.fireLastProgress(date, null, null);
        LinkedList<Object> resultAsList = new LinkedList<>(result);

        int toIndex = count;
        if (toIndex > resultAsList.size())
            toIndex = resultAsList.size();

        return resultAsList.subList(0, toIndex);
    }

    /**
     * Returns the supplied number of recent records before the given date
     *
     * @param services the services classNames we will query
     * @param descriptor CallPeer address(String), MetaContact or ChatRoom.
     * @param date messages before date
     * @param count messages count
     *
     * @return Collection sorted result that consists of records returned from the services we wrap
     */
    public Collection<Object> findLastMessagesBefore(String[] services, Object descriptor, Date date, int count) {
        MessageProgressWrapper listenWrapper = new MessageProgressWrapper(services.length);
        TreeSet<Object> result = new TreeSet<>(new RecordsComparator());
        for (int i = 0; i < services.length; i++) {
            String name = services[i];
            Object serv = getService(name);
            if (serv instanceof MessageHistoryService) {
                MessageHistoryService mhs = (MessageHistoryService) serv;
                listenWrapper.setIx(i);
                mhs.addSearchProgressListener(listenWrapper);

                if (descriptor instanceof MetaContact) {
                    result.addAll(mhs.findLastMessagesBefore((MetaContact) descriptor, date, count));
                }
                else if (descriptor instanceof ChatRoom) {
                    result.addAll(mhs.findLastMessagesBefore((ChatRoom) descriptor, date, count));
                }
                mhs.removeSearchProgressListener(listenWrapper);
            }
            else if (serv instanceof CallHistoryService) {
                CallHistoryService chs = (CallHistoryService) serv;
                listenWrapper.setIx(i);
                chs.addSearchProgressListener(listenWrapper);

                Collection<CallRecord> col = chs.findByEndDate(date);
                if (col.size() > count) {
                    List<CallRecord> l = new LinkedList<>(col);
                    result.addAll(l.subList(l.size() - count, l.size()));
                }
                else
                    result.addAll(col);
                chs.removeSearchProgressListener(listenWrapper);
            }
        }
        listenWrapper.fireLastProgress(date, null, null);

        LinkedList<Object> resultAsList = new LinkedList<>(result);
        int startIndex = resultAsList.size() - count;
        if (startIndex < 0)
            startIndex = 0;

        return resultAsList.subList(startIndex, resultAsList.size());
    }

    /**
     * Adding progress listener for monitoring progress of search process
     *
     * @param listener HistorySearchProgressListener
     */
    public void addSearchProgressListener(HistorySearchProgressListener listener) {
        synchronized (progressListeners) {
            if (!progressListeners.contains(listener))
                progressListeners.add(listener);
        }
    }

    /**
     * Removing progress listener
     *
     * @param listener HistorySearchProgressListener
     */
    public void removeSearchProgressListener(HistorySearchProgressListener listener) {
        synchronized (progressListeners) {
            progressListeners.remove(listener);
        }
    }

    private Object getService(String name) {
        Object serv = services.get(name);

        if (serv == null) {
            ServiceReference refHistory = bundleContext.getServiceReference(name);
            serv = bundleContext.getService(refHistory);
        }
        return serv;
    }

    private boolean matchAnyCallPeer(List<CallPeerRecord> cps, String[] keywords,
            boolean caseSensitive) {
        for (CallPeerRecord callPeer : cps) {
            for (String k : keywords) {
                if (caseSensitive && callPeer.getPeerAddress().contains(k))
                    return true;
                else if (callPeer.getPeerAddress().toLowerCase().contains(k.toLowerCase()))
                    return true;
            }
        }
        return false;
    }

    private boolean matchCallPeer(List<CallPeerRecord> cps, String[] keywords,
            boolean caseSensitive) {
        for (CallPeerRecord callPeer : cps) {
            boolean match = false;
            for (String k : keywords) {
                if (caseSensitive) {
                    if (callPeer.getPeerAddress().contains(k)) {
                        match = true;
                    }
                    else {
                        match = false;
                        break;
                    }
                }
                else if (callPeer.getPeerAddress().toLowerCase().contains(k.toLowerCase())) {
                    match = true;
                }
                else {
                    match = false;
                    break;
                }
            }
            if (match)
                return true;
        }
        return false;
    }

    public void serviceChanged(ServiceEvent serviceEvent) {
        if (serviceEvent.getType() == ServiceEvent.UNREGISTERING) {
            Object sService = bundleContext.getService(serviceEvent.getServiceReference());
            services.remove(sService.getClass().getName());
        }
    }

    /**
     * starts the service.
     *
     * @param bc BundleContext
     */
    public void start(BundleContext bc) {
        Timber.d("Starting the call history implementation.");
        this.bundleContext = bc;
        services.clear();

        // start listening for newly register or removed services
        bc.addServiceListener(this);
    }

    /**
     * stops the service.
     *
     * @param bc BundleContext
     */
    public void stop(BundleContext bc) {
        bc.removeServiceListener(this);
        services.clear();
    }

    /**
     * Used to compare various records to be ordered in TreeSet according their timestamp.
     */
    private static class RecordsComparator implements Comparator<Object> {
        private Date getDate(Object o) {
            Date date = new Date(0);
            if (o instanceof MessageDeliveredEvent)
                date = ((MessageDeliveredEvent) o).getTimestamp();
            else if (o instanceof MessageReceivedEvent)
                date = ((MessageReceivedEvent) o).getTimestamp();
            else if (o instanceof ChatRoomMessageDeliveredEvent)
                date = ((ChatRoomMessageDeliveredEvent) o).getTimestamp();
            else if (o instanceof ChatRoomMessageReceivedEvent)
                date = ((ChatRoomMessageReceivedEvent) o).getTimestamp();
            else if (o instanceof CallRecord)
                date = ((CallRecord) o).getStartTime();
            else if (o instanceof FileRecord)
                date = ((FileRecord) o).getDate();
            return date;
        }

        public int compare(Object o1, Object o2) {
            Date date1 = getDate(o1);
            Date date2 = getDate(o2);
            return date1.compareTo(date2);
        }
    }

    private class MessageProgressWrapper implements MessageHistorySearchProgressListener, CallHistorySearchProgressListener {
        private final int count;

        private int ix;

        public MessageProgressWrapper(int count) {
            this.count = count;
        }

        public void setIx(int ix) {
            this.ix = ix;
        }

        private void fireProgress(int origProgress, int maxVal, Date startDate, Date endDate, String[] keywords) {
            ProgressEvent ev = new ProgressEvent(MetaHistoryServiceImpl.this, startDate, endDate, keywords);

            double part1 = origProgress / ((double) maxVal * count);
            double convProgress = part1 * HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE
                    + ix * HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE / count;

            ev.setProgress((int) convProgress);
            fireEvent(ev);
        }

        private void fireEvent(ProgressEvent ev) {
            Iterable<HistorySearchProgressListener> listeners;
            synchronized (progressListeners) {
                listeners = new ArrayList<>(progressListeners);
            }
            for (HistorySearchProgressListener listener : listeners)
                listener.progressChanged(ev);
        }

        public void fireLastProgress(Date startDate, Date endDate, String[] keywords) {
            ProgressEvent ev = new ProgressEvent(MetaHistoryServiceImpl.this, startDate, endDate, keywords);
            ev.setProgress(HistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE);

            fireEvent(ev);
        }

        public void progressChanged(
                net.java.sip.communicator.service.msghistory.event.ProgressEvent evt) {
            fireProgress(evt.getProgress(), MessageHistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE,
                    evt.getStartDate(), evt.getEndDate(), evt.getKeywords());
        }

        public void progressChanged(
                net.java.sip.communicator.service.callhistory.event.ProgressEvent evt) {
            fireProgress(evt.getProgress(), CallHistorySearchProgressListener.PROGRESS_MAXIMUM_VALUE,
                    evt.getStartDate(), evt.getEndDate(), null);
        }
    }
}
