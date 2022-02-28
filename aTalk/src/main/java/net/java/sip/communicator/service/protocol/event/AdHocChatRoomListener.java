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

/**
 * A listener which dispatches events notifying ad-hoc chat rooms who have been created, joined and
 * destroyed.
 *
 * @author Valentin Martinet
 */
public interface AdHocChatRoomListener
{
	/**
	 * Called when we receive an <code>AdHocChatRoomCreatedEvent</code>.
	 *
	 * @param evt
	 *        the <code>AdHocChatRoomCreatedEvent</code>
	 */
	public void adHocChatRoomCreated(AdHocChatRoomCreatedEvent evt);

	/**
	 * Called when we receive an <code>AdHocChatRoomDestroyedEvent</code>.
	 *
	 * @param evt
	 *        the <code>AdHocChatRoomDestroyedEvent</code>
	 */
	public void adHocChatRoomDestroyed(AdHocChatRoomDestroyedEvent evt);
}
