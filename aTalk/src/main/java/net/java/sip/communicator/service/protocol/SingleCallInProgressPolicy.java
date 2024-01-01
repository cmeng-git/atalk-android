/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import static net.java.sip.communicator.service.protocol.OperationSetBasicTelephony.HANGUP_REASON_BUSY_HERE;

import net.java.sip.communicator.service.calendar.CalendarService;
import net.java.sip.communicator.service.protocol.event.CallChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallChangeListener;
import net.java.sip.communicator.service.protocol.event.CallEvent;
import net.java.sip.communicator.service.protocol.event.CallListener;
import net.java.sip.communicator.service.protocol.event.CallPeerEvent;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.service.configuration.ConfigurationService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * Imposes the policy to have one call in progress i.e. to put existing calls on hold when a new
 * call enters in progress.
 *
 * @author Lyubomir Marinov
 * @author Damian Minkov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class SingleCallInProgressPolicy
{
    /**
     * Account property to enable per account rejecting calls if the account presence is in DND or OnThePhone status.
     */
    private static final String ACCOUNT_PROPERTY_REJECT_IN_CALL_ON_DND = "RejectIncomingCallsWhenDnD";

    /**
     * The name of the configuration property which specifies whether call waiting is disabled i.e.
     * whether it should reject new incoming calls when there are other calls already in progress.
     */
    private static final String PNAME_CALL_WAITING_DISABLED = "protocol.CallWaitingDisabled";

    /**
     * The name of the configuration property which specifies whether
     * <code>OnThePhoneStatusPolicy</code> is enabled i.e. whether it should set the presence statuses
     * of online accounts to &quot;On the phone&quot; when at least one <code>Call</code> is in progress.
     */
    private static final String PNAME_ON_THE_PHONE_STATUS_ENABLED = "protocol.OnThePhoneStatusPolicy.enabled";

    /**
     * Global property which will enable rejecting incoming calls for all accounts, if the account
     * is in DND or OnThePhone status.
     */
    private static final String PNAME_REJECT_IN_CALL_ON_DND = "protocol." + ACCOUNT_PROPERTY_REJECT_IN_CALL_ON_DND;

    /**
     * The name of the configuration property which specifies whether <code>SingleCallInProgressPolicy</code>
     * is enabled i.e. whether it should put existing calls on hold when a new call enters in progress.
     */
    private static final String PNAME_SINGLE_CALL_IN_PROGRESS_POLICY_ENABLED
            = "protocol.SingleCallInProgressPolicy.enabled";

    /**
     * The <code>BundleContext</code> to the Calls of which this policy applies.
     */
    private final BundleContext bundleContext;

    /**
     * The <code>Call</code>s this policy manages i.e. put on hold when one of them enters in progress.
     */
    private final List<Call> calls = new ArrayList<>();

    /**
     * The listener utilized by this policy to discover new <code>Call</code> and track their in-progress state.
     */
    private final SingleCallInProgressPolicyListener listener = new SingleCallInProgressPolicyListener();

    /**
     * The implementation of the policy to have the presence statuses of online accounts (i.e. registered
     * <code>ProtocolProviderService</code>s) set to &quot;On the phone&quot; when at least one <code>Call</code> is in progress.
     */
    private final OnThePhoneStatusPolicy onThePhoneStatusPolicy = new OnThePhoneStatusPolicy();

    /**
     * Initializes a new <code>SingleCallInProgressPolicy</code> instance which will apply to the
     * <code>Call</code>s of a specific <code>BundleContext</code>.
     *
     * @param bundleContext the <code>BundleContext</code> to the <code>Call<code>s of which the new policy should apply
     */
    public SingleCallInProgressPolicy(BundleContext bundleContext)
    {
        this.bundleContext = bundleContext;

        if (ProtocolProviderActivator.getConfigurationService()
                .getBoolean(PNAME_SINGLE_CALL_IN_PROGRESS_POLICY_ENABLED, true)) {
            this.bundleContext.addServiceListener(listener);
        }
    }

    /**
     * Registers a specific <code>Call</code> with this policy in order to have the rules of the latter apply to the former.
     *
     * @param call the <code>Call</code> to register with this policy in order to have the rules of the
     * latter apply to the former
     */
    private void addCallListener(Call call)
    {
        Timber.log(TimberLog.FINER, "Add call change listener");

        synchronized (calls) {
            if (!calls.contains(call)) {
                CallState callState = call.getCallState();
                if ((callState != null) && !callState.equals(CallState.CALL_ENDED)) {
                    calls.add(call);
                }
            }
        }
        call.addCallChangeListener(listener);
    }

    /**
     * Registers a specific <code>OperationSetBasicTelephony</code> with this policy in order to have
     * the rules of the latter apply to the <code>Call</code>s created by the former.
     *
     * @param telephony the <code>OperationSetBasicTelephony</code> to register with this policy in order to have
     * the rules of the latter apply to the <code>Call</code>s created by the former
     */
    private void addOperationSetBasicTelephonyListener(
            OperationSetBasicTelephony<? extends ProtocolProviderService> telephony)
    {
        Timber.log(TimberLog.FINER, "Call listener added to provider.");
        telephony.addCallListener(listener);
    }

    /**
     * Handles changes in the state of a <code>Call</code> this policy applies to in order to detect
     * when new calls become in-progress and when the other calls should be put on hold.
     *
     * @param ev a <code>CallChangeEvent</code> value which describes the <code>Call</code> and the change in its state
     */
    private void callStateChanged(CallChangeEvent ev)
    {
        Call call = ev.getSourceCall();
        Timber.log(TimberLog.FINER, "Call state changed.");

        if (CallState.CALL_INITIALIZATION.equals(ev.getOldValue())
                && CallState.CALL_IN_PROGRESS.equals(call.getCallState())) {
            CallConference conference = call.getConference();

            synchronized (calls) {
                for (Call otherCall : calls) {
                    if (!call.equals(otherCall)
                            && CallState.CALL_IN_PROGRESS.equals(otherCall.getCallState())) {
                        /*
                         * Only put on hold calls which are visually distinctive from the specified
                         * call i.e. do not put on hold calls which participate in the same
                         * telephony conference as the specified call.
                         */
                        boolean putOnHold;
                        CallConference otherConference = otherCall.getConference();

                        if (conference == null)
                            putOnHold = (otherConference == null);
                        else
                            putOnHold = (conference != otherConference);
                        if (putOnHold)
                            putOnHold(otherCall);
                    }
                }
            }
        }
        else if (CallState.CALL_ENDED.equals(ev.getNewValue())) {
            this.handleCallEvent(CallEvent.CALL_ENDED, call);
        }

        /*
         * Forward to onThePhoneStatusPolicy for which we are proxying the Call-related events.
         */
        onThePhoneStatusPolicy.callStateChanged(ev);
    }

    /**
     * Performs end-of-life cleanup associated with this instance e.g. removes added listeners.
     */
    public void dispose()
    {
        bundleContext.removeServiceListener(listener);
    }

    /**
     * Handles the start and end of the <code>Call</code>s this policy applies to in order to have them
     * or stop having them put the other existing calls on hold when the former change their states
     * to <code>CallState.CALL_IN_PROGRESS</code>.
     * <p>
     * Also handles call rejection via "busy here" according to the call policy.
     * </p>
     *
     * @param type one of {@link CallEvent#CALL_ENDED}, {@link CallEvent#CALL_INITIATED} and
     * {@link CallEvent#CALL_RECEIVED} which describes the type of the event to be handled
     * @param ev a <code>CallEvent</code> value which describes the change and the <code>Call</code> associated with it
     */
    private void handleCallEvent(int type, Call call)
    {
        Timber.log(TimberLog.FINER, "Call event fired.");

        switch (type) {
            case CallEvent.CALL_ENDED:
                removeCallListener(call);
                break;

            case CallEvent.CALL_INITIATED:
            case CallEvent.CALL_RECEIVED:
                addCallListener(call);
                break;
        }
        /*
         * Forward to onThePhoneStatusPolicy for which we are proxying the Call-related events.
         */
        onThePhoneStatusPolicy.handleCallEvent(type, call);
    }

    /**
     * Notifies this instance that an incoming <code>Call</code> has been received.
     *
     * @param ev a <code>CallEvent</code> which describes the received incoming <code>Call</code>
     */
    private void incomingCallReceived(CallEvent ev)
    {
        Call call = ev.getSourceCall();

        // check whether we should hangup this call saying we are busy already on call
        if (CallState.CALL_INITIALIZATION.equals(call.getCallState())) {
            ConfigurationService config = ProtocolProviderActivator.getConfigurationService();

            if (config.getBoolean(PNAME_CALL_WAITING_DISABLED, false)) {
                boolean rejectCallWithBusyHere = false;

                synchronized (calls) {
                    for (Call otherCall : calls) {
                        if (!call.equals(otherCall)
                                && CallState.CALL_IN_PROGRESS.equals(otherCall.getCallState())) {
                            rejectCallWithBusyHere = true;
                            break;
                        }
                    }
                }
                if (rejectCallWithBusyHere) {
                    rejectCallWithBusyHere(call);
                    return;
                }
            }

            ProtocolProviderService provider = call.getProtocolProvider();

            if (config.getBoolean(PNAME_REJECT_IN_CALL_ON_DND, false)
                    || provider.getAccountID().getAccountPropertyBoolean(
                    ACCOUNT_PROPERTY_REJECT_IN_CALL_ON_DND, false)) {
                OperationSetPresence presence = provider.getOperationSet(OperationSetPresence.class);

                // if our provider has no presence op set, lets search for connected provider which will have
                if (presence == null) {
                    // There is no presence OpSet. Let's check the connected CUSAX provider if available
                    OperationSetCusaxUtils cusaxOpSet = provider.getOperationSet(OperationSetCusaxUtils.class);
                    if (cusaxOpSet != null) {
                        ProtocolProviderService linkedCusaxProvider = cusaxOpSet.getLinkedCusaxProvider();
                        if (linkedCusaxProvider != null) {
                            // we found the provider, let's take its presence opset
                            presence = linkedCusaxProvider.getOperationSet(OperationSetPresence.class);
                        }

                    }
                }

                if (presence != null) {
                    int presenceStatus = (presence == null) ? PresenceStatus.AVAILABLE_THRESHOLD
                            : presence.getPresenceStatus().getStatus();

                    // between AVAILABLE and EXTENDED AWAY (>20, <= 31) are the busy statuses as DND and On the phone
                    if (presenceStatus > PresenceStatus.ONLINE_THRESHOLD
                            && presenceStatus <= PresenceStatus.EXTENDED_AWAY_THRESHOLD) {
                        rejectCallWithBusyHere(call);
                        return;
                    }
                }
            }
        }
        handleCallEvent(CallEvent.CALL_RECEIVED, call);
    }

    /**
     * Puts the <code>CallPeer</code>s of a specific <code>Call</code> on hold.
     *
     * @param call the <code>Call</code> the <code>CallPeer</code>s of which are to be put on hold
     */
    private void putOnHold(Call call)
    {
        OperationSetBasicTelephony<?> telephony
                = call.getProtocolProvider().getOperationSet(OperationSetBasicTelephony.class);

        if (telephony != null) {
            for (Iterator<? extends CallPeer> peerIter = call.getCallPeers(); peerIter.hasNext(); ) {
                CallPeer peer = peerIter.next();
                CallPeerState peerState = peer.getState();

                if (!CallPeerState.DISCONNECTED.equals(peerState)
                        && !CallPeerState.FAILED.equals(peerState)
                        && !CallPeerState.isOnHold(peerState)) {
                    try {
                        telephony.putOnHold(peer);
                    } catch (OperationFailedException ex) {
                        Timber.e(ex, "Failed to put %s on hold.", peer);
                    }
                }
            }
        }
    }

    /**
     * Rejects a <code>call</code> with busy here code.
     *
     * @param call the call to reject.
     */
    private void rejectCallWithBusyHere(Call call)
    {
        // We're interested in one-to-one incoming calls.
        if (call.getCallPeerCount() == 1) {
            CallPeer peer = call.getCallPeers().next();

            OperationSetBasicTelephony<?> telephony
                    = call.getProtocolProvider().getOperationSet(OperationSetBasicTelephony.class);

            if (telephony != null) {
                try {
                    telephony.hangupCallPeer(peer, HANGUP_REASON_BUSY_HERE, null);
                } catch (OperationFailedException ex) {
                    Timber.e(ex, "Failed to reject %s", peer);
                }
            }
        }
    }

    /**
     * Unregisters a specific <code>Call</code> from this policy in order to have the rules of the
     * latter no longer applied to the former.
     *
     * @param call the <code>Call</code> to unregister from this policy in order to have the rules of the
     * latter no longer apply to the former
     */
    private void removeCallListener(Call call)
    {
        Timber.log(TimberLog.FINER, "Remove call change listener.");

        call.removeCallChangeListener(listener);
        synchronized (calls) {
            calls.remove(call);
        }
    }

    /**
     * Unregisters a specific <code>OperationSetBasicTelephony</code> from this policy in order to have
     * the rules of the latter no longer apply to the <code>Call</code>s created by the former.
     *
     * @param telephony the <code>OperationSetBasicTelephony</code> to unregister from this policy in order to
     * have the rules of the latter apply to the <code>Call</code>s created by the former
     */
    private void removeOperationSetBasicTelephonyListener(
            OperationSetBasicTelephony<? extends ProtocolProviderService> telephony)
    {
        telephony.removeCallListener(listener);
    }

    /**
     * Handles the registering and unregistering of <code>OperationSetBasicTelephony</code> instances in
     * order to apply or unapply the rules of this policy to the <code>Call</code>s originating from them.
     *
     * @param ev a <code>ServiceEvent</code> value which described a change in an OSGi service and which is
     * to be examined for the registering or unregistering of a
     * <code>ProtocolProviderService</code> and thus a <code>OperationSetBasicTelephony</code>
     */
    private void serviceChanged(ServiceEvent ev)
    {
        Object service = bundleContext.getService(ev.getServiceReference());
        if (service instanceof ProtocolProviderService) {
            Timber.log(TimberLog.FINER, "Protocol provider service changed.");

            OperationSetBasicTelephony<?> telephony
                    = ((ProtocolProviderService) service).getOperationSet(OperationSetBasicTelephony.class);

            if (telephony != null) {
                switch (ev.getType()) {
                    case ServiceEvent.REGISTERED:
                        addOperationSetBasicTelephonyListener(telephony);
                        break;
                    case ServiceEvent.UNREGISTERING:
                        removeOperationSetBasicTelephonyListener(telephony);
                        break;
                }
            }
            else {
                Timber.log(TimberLog.FINER, "The protocol provider service doesn't support " + "telephony.");
            }
        }
    }

    /**
     * Implements the policy to have the presence statuses of online accounts (i.e. registered
     * <code>ProtocolProviderService</code>s) set to &quot;On the phone&quot; when at least one <code>Call</code> is in progress.
     *
     * @author Lyubomir Marinov
     */
    private class OnThePhoneStatusPolicy
    {
        /**
         * The regular expression which removes whitespace from the <code>statusName</code> property
         * value of <code>PresenceStatus</code> instances in order to recognize the
         * <code>PresenceStatus</code> which represents &quot;On the phone&quot;.
         */
        private final Pattern presenceStatusNameWhitespace = Pattern.compile("\\p{Space}");

        /**
         * The <code>PresenceStatus</code>es of <code>ProtocolProviderService</code>s before they were
         * changed to &quot;On the phone&quot; remembered so that they can be restored after the
         * last <code>Call</code> in progress ends.
         */
        private final Map<ProtocolProviderService, PresenceStatus> presenceStatuses
                = Collections.synchronizedMap(new WeakHashMap<>());

        /**
         * Notifies this instance that the <code>callState</code> of a specific <code>Call</code> has changed.
         *
         * @param ev a <code>CallChangeEvent</code> which represents the details of the notification such
         * as the affected <code>Call</code> and its old and new <code>CallState</code>s
         */
        public void callStateChanged(CallChangeEvent ev)
        {
            Timber.log(TimberLog.FINER, "Call state changed.[2]");

            Call call = ev.getSourceCall();
            Object oldCallState = ev.getOldValue();
            Object newCallState = call.getCallState();

            if ((CallState.CALL_INITIALIZATION.equals(oldCallState)
                    && CallState.CALL_IN_PROGRESS.equals(newCallState))
                    || (CallState.CALL_IN_PROGRESS.equals(oldCallState)
                    && CallState.CALL_ENDED.equals(newCallState))) {
                run();
            }
            else {
                Timber.log(TimberLog.FINER, "Not applicable call state.");
            }
        }

        /**
         * Finds the first <code>PresenceStatus</code> among the set of <code>PresenceStatus</code>es
         * supported by a specific <code>OperationSetPresence</code> which represents &quot; On the phone&quot;.
         *
         * @param presence the <code>OperationSetPresence</code> which represents the set of supported
         * <code>PresenceStatus</code>es
         * @return the first <code>PresenceStatus</code> among the set of <code>PresenceStatus</code>es
         * supported by <code>presence</code> which represents &quot;On the phone&quot; if such
         * a <code>PresenceStatus</code> was found; otherwise, <code>null</code>
         */
        private PresenceStatus findOnThePhonePresenceStatus(OperationSetPresence presence)
        {
            for (PresenceStatus presenceStatus : presence.getSupportedStatusSet()) {
                if (presenceStatusNameWhitespace.matcher(presenceStatus.getStatusName())
                        .replaceAll("").equalsIgnoreCase("OnThePhone")) {
                    return presenceStatus;
                }
            }
            return null;
        }

        private PresenceStatus forgetPresenceStatus(ProtocolProviderService pps)
        {
            return presenceStatuses.remove(pps);
        }

        private void forgetPresenceStatuses()
        {
            presenceStatuses.clear();
        }

        /**
         * Notifies this instance that a new outgoing <code>Call</code> was initiated, an incoming
         * <code>Call</code> was received or an existing <code>Call</code> ended.
         *
         * @param type one of {@link CallEvent#CALL_ENDED}, {@link CallEvent#CALL_INITIATED} and
         * {@link CallEvent#CALL_RECEIVED} which describes the type of the event to be handled
         * @param call the <code>Call</code> instance.
         */
        public void handleCallEvent(int type, Call call)
        {
            run();
        }

        /**
         * Determines whether there is at least one existing <code>Call</code> which is currently in
         * progress i.e. determines whether the local user is currently on the phone.
         *
         * @return <code>true</code> if there is at least one existing <code>Call</code> which is currently
         * in progress i.e. if the local user is currently on the phone; otherwise, <code>false</code>
         */
        private boolean isOnThePhone()
        {
            synchronized (calls) {
                for (Call call : calls) {
                    if (CallState.CALL_IN_PROGRESS.equals(call.getCallState()))
                        return true;
                }
            }
            return false;
        }

        /**
         * Invokes {@link OperationSetPresence#publishPresenceStatus(PresenceStatus, String)} on a
         * specific <code>OperationSetPresence</code> with a specific <code>PresenceStatus</code> and catches any exceptions.
         *
         * @param presence the <code>OperationSetPresence</code> on which the method is to be invoked
         * @param presenceStatus the <code>PresenceStatus</code> to provide as the respective method argument value
         */
        private void publishPresenceStatus(OperationSetPresence presence, PresenceStatus presenceStatus)
        {
            try {
                presence.publishPresenceStatus(presenceStatus, null);
            } catch (Throwable t) {
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
            }
        }

        private PresenceStatus rememberPresenceStatus(ProtocolProviderService pps, PresenceStatus presenceStatus)
        {
            return presenceStatuses.put(pps, presenceStatus);
        }

        /**
         * Finds the first <code>PresenceStatus</code> among the set of <code>PresenceStatus</code>es
         * supported by a specific <code>OperationSetPresence</code> which represents &quot; In meeting&quot;.
         *
         * @param presence the <code>OperationSetPresence</code> which represents the set of supported <code>PresenceStatus</code>es
         * @return the first <code>PresenceStatus</code> among the set of <code>PresenceStatus</code>es
         * supported by <code>presence</code> which represents &quot;In meeting&quot; if such a
         * <code>PresenceStatus</code> was found; otherwise, <code>null</code>
         */
        private PresenceStatus findInMeetingPresenceStatus(OperationSetPresence presence)
        {
            for (PresenceStatus presenceStatus : presence.getSupportedStatusSet()) {
                if (presenceStatusNameWhitespace.matcher(presenceStatus.getStatusName())
                        .replaceAll("").equalsIgnoreCase("InAMeeting")) {
                    return presenceStatus;
                }
            }
            return null;
        }

        /**
         * Applies this policy to the current state of the application.
         */
        private void run()
        {
            Timber.log(TimberLog.FINER, "On the phone status policy run.");
            if (!ProtocolProviderActivator.getConfigurationService().getBoolean(
                    PNAME_ON_THE_PHONE_STATUS_ENABLED, false)) {
                Timber.log(TimberLog.FINER, "On the phone status is not enabled.");
                forgetPresenceStatuses();
                return;
            }

            ServiceReference[] ppsRefs;
            try {
                ppsRefs = bundleContext.getServiceReferences(ProtocolProviderService.class.getName(), null);
            } catch (InvalidSyntaxException ise) {
                Timber.log(TimberLog.FINER, "Can't access protocol providers refences.");
                ppsRefs = null;
            }

            if ((ppsRefs == null) || (ppsRefs.length == 0)) {
                forgetPresenceStatuses();
            }
            else {
                boolean isOnThePhone = isOnThePhone();
                CalendarService calendar = ProtocolProviderActivator.getCalendarService();

                if (!isOnThePhone && calendar != null
                        && calendar.onThePhoneStatusChanged(presenceStatuses)) {

                    Timber.log(TimberLog.FINER, "We are not on the phone.");
                    forgetPresenceStatuses();
                    return;
                }

                for (ServiceReference<?> ppsRef : ppsRefs) {
                    ProtocolProviderService pps = (ProtocolProviderService) bundleContext.getService(ppsRef);

                    if (pps == null) {
                        Timber.log(TimberLog.FINER, "Provider is null.");
                        continue;
                    }

                    OperationSetPresence presence = pps.getOperationSet(OperationSetPresence.class);
                    if (presence == null) {
                        Timber.log(TimberLog.FINER, "Presence is null.");
                        /*
                         * "On the phone" is a PresenceStatus so it is available only to accounts
                         * which support presence in the first place.
                         */
                        forgetPresenceStatus(pps);
                    }
                    else if (pps.isRegistered()) {
                        Timber.log(TimberLog.FINER, "Provider is registered.");
                        PresenceStatus onThePhonePresenceStatus = findOnThePhonePresenceStatus(presence);
                        if (onThePhonePresenceStatus == null) {
                            Timber.log(TimberLog.FINER, "Can't find on the phone status.");
                            /*
                             * If do not know how to define "On the phone" for an OperationSetPresence,
                             * then we'd better not mess with it in the first place.
                             */
                            forgetPresenceStatus(pps);
                        }
                        else if (isOnThePhone) {
                            Timber.log(TimberLog.FINER, "Setting the status to on the phone.");
                            PresenceStatus presenceStatus = presence.getPresenceStatus();

                            if (presenceStatus == null) {
                                Timber.log(TimberLog.FINER, "Presence status is null.");
                                /*
                                 * It is strange that an OperationSetPresence does not have a
                                 * PresenceStatus so it may be safer to not mess with it.
                                 */
                                forgetPresenceStatus(pps);
                            }
                            else if (!onThePhonePresenceStatus.equals(presenceStatus)) {
                                Timber.log(TimberLog.FINER, "On the phone status is published.");
                                publishPresenceStatus(presence, onThePhonePresenceStatus);

                                if (presenceStatus.equals(findInMeetingPresenceStatus(presence))
                                        && calendar != null) {
                                    Map<ProtocolProviderService, PresenceStatus> statuses
                                            = calendar.getRememberedStatuses();
                                    for (ProtocolProviderService provider : statuses.keySet())
                                        rememberPresenceStatus(provider, statuses.get(provider));
                                }
                                else if (onThePhonePresenceStatus.equals(presence.getPresenceStatus())) {
                                    rememberPresenceStatus(pps, presenceStatus);
                                }
                                else {
                                    forgetPresenceStatus(pps);
                                }
                            }
                            else {
                                Timber.log(TimberLog.FINER, "Currently the status is on the phone.");
                            }
                        }
                        else {
                            Timber.log(TimberLog.FINER, "Unset on the phone status.");
                            PresenceStatus presenceStatus = forgetPresenceStatus(pps);

                            if ((presenceStatus != null)
                                    && onThePhonePresenceStatus.equals(presence.getPresenceStatus())) {
                                Timber.log(TimberLog.FINER, "Unset on the phone status.[2]");
                                publishPresenceStatus(presence, presenceStatus);
                            }
                        }
                    }
                    else {
                        Timber.log(TimberLog.FINER, "Protocol provider is not registered");
                        /*
                         * Offline accounts do not get their PresenceStatus modified for the purposes of "On the phone".
                         */
                        forgetPresenceStatus(pps);
                    }
                }
            }
        }
    }

    /**
     * Implements the listeners interfaces used by this policy.
     *
     * @author Lyubomir Marinov
     */
    private class SingleCallInProgressPolicyListener implements CallChangeListener, CallListener, ServiceListener
    {
        /**
         * Stops tracking the state of a specific <code>Call</code> and no longer tries to put it on hold when it ends.
         *
         * @see CallListener#callEnded(CallEvent)
         */
        public void callEnded(CallEvent ev)
        {
            /*
             * Not using call ended, cause the CallListener is removed
             * when protocol disconnects and it can happen that this is
             * before the callEnded event in case of running call during
             * removing an account and this can lead to leaking calls.
             */
        }

        /**
         * Does nothing because adding <code>CallPeer<code>s to <code>Call</code>s isn't related to the
         * policy to put existing calls on hold when a new call becomes in-progress and just
         * implements <code>CallChangeListener</code>.
         *
         * @see CallChangeListener#callPeerAdded(CallPeerEvent)
         */
        public void callPeerAdded(CallPeerEvent ev)
        {
            /*
             * Not of interest, just implementing CallChangeListener in which only
             * #callStateChanged(CallChangeEvent) is of interest.
             */
        }

        /**
         * Does nothing because removing <code>CallPeer<code>s to <code>Call</code>s isn't related to the policy to put
         * existing calls on hold when a new call becomes in-progress and just implements <code>CallChangeListener</code>.
         *
         * @see CallChangeListener#callPeerRemoved(CallPeerEvent)
         */
        public void callPeerRemoved(CallPeerEvent ev)
        {
            /*
             * Not of interest, just implementing CallChangeListener in which only
             * #callStateChanged(CallChangeEvent) is of interest.
             */
        }

        /**
         * Upon a <code>Call</code> changing its state to <code>CallState.CALL_IN_PROGRESS</code>, puts the
         * other existing <code>Call</code>s on hold.
         *
         * @param ev the <code>CallChangeEvent</code> that we are to deliver.
         * @see CallChangeListener#callStateChanged(CallChangeEvent)
         */
        public void callStateChanged(CallChangeEvent ev)
        {
            // we are interested only in CALL_STATE_CHANGEs
            if (ev.getEventType().equals(CallChangeEvent.CALL_STATE_CHANGE))
                SingleCallInProgressPolicy.this.callStateChanged(ev);
        }

        /**
         * Remembers an incoming <code>Call</code> so that it can put the other existing <code>Call</code>s
         * on hold when it changes its state to <code>CallState.CALL_IN_PROGRESS</code>.
         *
         * @see CallListener#incomingCallReceived(CallEvent)
         */
        public void incomingCallReceived(CallEvent ev)
        {
            SingleCallInProgressPolicy.this.incomingCallReceived(ev);
        }

        /**
         * Remembers an outgoing <code>Call</code> so that it can put the other existing <code>Call</code>s
         * on hold when it changes its state to <code>CallState.CALL_IN_PROGRESS</code>.
         *
         * @see CallListener#outgoingCallCreated(CallEvent)
         */
        public void outgoingCallCreated(CallEvent ev)
        {
            SingleCallInProgressPolicy.this.handleCallEvent(CallEvent.CALL_INITIATED, ev.getSourceCall());
        }

        /**
         * Starts/stops tracking the new <code>Call</code>s originating from a specific
         * <code>ProtocolProviderService</code> when it registers/unregisters in order to take them into
         * account when putting existing calls on hold upon a new call entering its in-progress state.
         *
         * @param ev the <code>ServiceEvent</code> event describing a change in the state of a service
         * registration which may be a <code>ProtocolProviderService</code> supporting
         * <code>OperationSetBasicTelephony</code> and thus being able to create new <code>Call</code>s
         */
        public void serviceChanged(ServiceEvent ev)
        {
            Timber.log(TimberLog.FINER, "Service changed.");
            SingleCallInProgressPolicy.this.serviceChanged(ev);
        }
    }
}
