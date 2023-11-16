/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.service.protocol.event;

import net.java.sip.communicator.service.protocol.CallPeer;

/**
 * An event listener that should be implemented by parties interested in remote control feature (i.e
 * desktop sharing).
 *
 * @author Sebastien Vincent
 */
public interface RemoteControlListener
{
	/**
	 * This method is called when remote control has been granted.
	 *
	 * @param event
	 *        <code>RemoteControlGrantedEvent</code>
	 */
	public void remoteControlGranted(RemoteControlGrantedEvent event);

	/**
	 * This method is called when remote control has been revoked.
	 *
	 * @param event
	 *        <code>RemoteControlRevokedEvent</code>
	 */
	public void remoteControlRevoked(RemoteControlRevokedEvent event);

	/**
	 * Returns the remote-controlled <code>CallPeer</code>.
	 *
	 * @return the remote-controlled <code>CallPeer</code>
	 */
	public CallPeer getCallPeer();
}
