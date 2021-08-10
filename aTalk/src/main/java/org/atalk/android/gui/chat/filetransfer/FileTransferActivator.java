/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.android.gui.chat.filetransfer;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;

import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.osgi.framework.*;

import java.util.Date;

import timber.log.Timber;

/**
 * Android FileTransferActivator activator which registers <tt>ScFileTransferListener</tt>
 * for each protocol service provider specific to this system. It listens in to any incoming
 * fileTransferRequestReceived and generate a message to be display in the respective chatPanel
 * for user action.
 * Note: Each protocol must registered only once, otherwise multiple file received messages get
 * generated for every fileTransferRequestReceived.
 *
 * @author Eng Chong Meng
 */
public class FileTransferActivator implements BundleActivator, ServiceListener, ScFileTransferListener
{
    /**
     * The BundleContext that we got from the OSGI bus.
     */
    private BundleContext bundleContext;

    /**
     * Starts the service. Check the current registered protocol providers which supports
     * FileTransfer and adds a listener to them.
     *
     * @param bc BundleContext
     */
    public void start(BundleContext bc)
            throws Exception
    {
        bundleContext = bc;
        bundleContext.addServiceListener(this);

        ServiceReference[] ppsRefs = null;
        try {
            ppsRefs = bundleContext.getServiceReferences(ProtocolProviderService.class.getName(), null);
        } catch (InvalidSyntaxException ex) {
            ex.printStackTrace();
        }
        if ((ppsRefs != null) && (ppsRefs.length != 0)) {
            for (ServiceReference<ProtocolProviderService> ppsRef : ppsRefs) {
                ProtocolProviderService pps = bundleContext.getService(ppsRef);
                handleProviderAdded(pps);
            }
        }
    }

    /**
     * Stops the service.
     *
     * @param bc BundleContext
     */
    public void stop(BundleContext bc)
            throws Exception
    {
        bundleContext = bc;
        bundleContext.removeServiceListener(this);

        ServiceReference[] ppsRefs = null;
        try {
            ppsRefs = bundleContext.getServiceReferences(ProtocolProviderService.class.getName(), null);
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
        }

        if ((ppsRefs != null) && (ppsRefs.length != 0)) {
            for (ServiceReference<ProtocolProviderService> ppsRef : ppsRefs) {
                ProtocolProviderService pps = bundleContext.getService(ppsRef);
                handleProviderRemoved(pps);
            }
        }
    }

    /**
     * When new protocol provider is registered we check if it does supports FileTransfer and
     * add a listener to it if so.
     *
     * @param event ServiceEvent received when there is a service changed
     */
    public void serviceChanged(ServiceEvent event)
    {
        ServiceReference serviceRef = event.getServiceReference();
        // if the event is caused by a bundle being stopped, we don't want to know
        if (serviceRef.getBundle().getState() == Bundle.STOPPING)
            return;

        Object sService = bundleContext.getService(serviceRef);
        // we don't care if the source service is not a protocol provider
        if (sService instanceof ProtocolProviderService) {
            switch (event.getType()) {
                case ServiceEvent.REGISTERED:
                    this.handleProviderAdded((ProtocolProviderService) sService);
                    break;

                case ServiceEvent.UNREGISTERING:
                    this.handleProviderRemoved((ProtocolProviderService) sService);
                    break;
            }
        }
    }

    /**
     * Used to attach the File Transfer Service to existing or just registered protocol provider.
     * Checks if the provider has implementation of OperationSetFileTransfer
     *
     * @param provider ProtocolProviderService
     */
    private void handleProviderAdded(ProtocolProviderService provider)
    {
        OperationSetFileTransfer opSetFileTransfer = provider.getOperationSet(OperationSetFileTransfer.class);

        if (opSetFileTransfer != null) {
            opSetFileTransfer.addFileTransferListener(this);
        }
        else {
            Timber.log(TimberLog.FINER, "Service did not have a file transfer op. set: %s", provider.toString());
        }
    }

    /**
     * Removes the specified provider from the list of currently known providers
     *
     * @param provider the ProtocolProviderService that has been unregistered.
     */
    private void handleProviderRemoved(ProtocolProviderService provider)
    {
        OperationSetFileTransfer opSetFileTransfer = provider.getOperationSet(OperationSetFileTransfer.class);

        if (opSetFileTransfer != null) {
            opSetFileTransfer.removeFileTransferListener(this);
        }
    }

    /**
     * Called when a new <tt>IncomingFileTransferRequest</tt> has been received.
     *
     * @param event the <tt>FileTransferRequestEvent</tt> containing the newly received request and other details.
     */
    @Override
    public void fileTransferRequestReceived(FileTransferRequestEvent event)
    {
        IncomingFileTransferRequest request = event.getRequest();
        OperationSetFileTransfer opSet = event.getFileTransferOperationSet();

        Contact sender = request.getSender();
        Date date = event.getTimestamp();
        ChatPanel chatPanel = ChatSessionManager.createChatForContact(sender);
        if (chatPanel != null)
            chatPanel.addFTRequest(opSet, request, date);
    }

    /**
     * Nothing to do here, because we already know when a file transfer is created.
     *
     * @param event the <tt>FileTransferCreatedEvent</tt> that notified us
     */
    public void fileTransferCreated(FileTransferCreatedEvent event)
    {
    }

    /**
     * Called when a new <tt>IncomingFileTransferRequest</tt> has been rejected. Nothing to do
     * here, because we are the one who rejects the request.
     *
     * @param event the <tt>FileTransferRequestEvent</tt> containing the received request which was rejected.
     */
    public void fileTransferRequestRejected(FileTransferRequestEvent event)
    {
    }

    /**
     * Called when an <tt>IncomingFileTransferRequest</tt> has been canceled from the contact who send it.
     *
     * @param event the <tt>FileTransferRequestEvent</tt> containing the request which was canceled.
     */
    public void fileTransferRequestCanceled(FileTransferRequestEvent event)
    {
    }
}