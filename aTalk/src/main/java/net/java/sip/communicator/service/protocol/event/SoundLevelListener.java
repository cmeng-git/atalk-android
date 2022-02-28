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

import java.util.EventListener;

/**
 * Notifies interested parties in sound level changes of the main audio stream coming from a given
 * <code>CallPeer</code>.
 * <p>
 * What does this mean in the different cases: 1) In the case of a <code>CallPeer</code>, which is not a
 * conference focus and has no <code>ConferenceMember</code>s we would be notified of any change in the
 * sound level of the stream coming from this peer. 2) In the case of a <code>CallPeer</code>, which is
 * also a conference focus and is participating in the conference as a <code>ConferenceMember</code> the
 * level would be the aggregated level of all <code>ConferenceMember</code>s levels including the one
 * corresponding to the peer itself. 3) In the case of a <code>CallPeer</code>, which is also a
 * conference focus, but is NOT participating in the conference as a <code>ConferenceMember</code>
 * (server) the level would be the aggregated level of all <code>ConferenceMember</code>s.
 *
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 */
public interface SoundLevelListener extends EventListener
{

    /**
     * Indicates that a change has occurred in the audio stream level coming from a given
     * <code>CallPeer</code>. In the case of conference focus the audio stream level would be the total
     * level including all <code>ConferenceMember</code>s levels.
     * <p>
     * In contrast to the conventions of Java and Jitsi, <code>SoundLevelListener</code> does not fire
     * an <code>EventObject</code> (i.e. <code>SoundLevelChangeEvent</code>) in order to try to reduce the
     * number of allocations related to sound level changes since their number is expected to be
     * very large.
     * </p>
     *
     * @param source the <code>Object</code> which is the source of the sound level change event being fired to
     * this <code>SoundLevelListener</code>
     * @param level the sound level to notify this <code>SoundLevelListener</code> about
     */
    public void soundLevelChanged(Object source, int level);
}
