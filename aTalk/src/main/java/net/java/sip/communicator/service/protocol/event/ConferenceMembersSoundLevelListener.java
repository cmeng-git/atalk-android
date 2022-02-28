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
 * Notifies interested parties in <code>ConferenceMember</code>s sound level changes. When a
 * <code>CallPeer</code> is participating in the conference also as a <code>ConferenceMember</code> its
 * audio level would be included in the map of received levels.
 *
 * @author Yana Stamcheva
 */
public interface ConferenceMembersSoundLevelListener
{
	/**
	 * Indicates that a change has occurred in the sound level of some of the
	 * <code>ConferenceMember</code>s coming from a given <code>CallPeer</code>. It's presumed that all
	 * <code>ConferenceMember</code>s NOT contained in the event have a 0 sound level.
	 *
	 * @param event
	 *        the <code>ConferenceMembersSoundLevelEvent</code> containing the new level
	 */
	public void soundLevelChanged(ConferenceMembersSoundLevelEvent event);
}
