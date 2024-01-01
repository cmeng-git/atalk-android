/*
 * Copyright @ 2015 Atlassian Pty Ltd
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
package org.atalk.util.dsi;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.util.concurrent.ExecutorUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Implements {@link ActiveSpeakerDetector} with inspiration from the paper &quot;Dominant Speaker
 * Identification for Multipoint Videoconferencing&quot; by Ilana Volfin and Israel Cohen.
 *
 * @author Lyubomir Marinov
 */
public class DominantSpeakerIdentification extends AbstractActiveSpeakerDetector
{
    /**
     * The threshold of the relevant speech activities in the immediate time-interval in
     * &quot;global decision&quot;/&quot;Dominant speaker selection&quot; phase of the algorithm.
     */
    private static final double C1 = 3;

    /**
     * The threshold of the relevant speech activities in the medium time-interval in &quot;global
     * decision&quot;/&quot;Dominant speaker selection&quot; phase of the algorithm.
     */
    private static final double C2 = 2;

    /**
     * The threshold of the relevant speech activities in the long time-interval in &quot;global
     * decision&quot;/&quot;Dominant speaker selection&quot; phase of the algorithm.
     */
    private static final double C3 = 0;

    /**
     * The interval in milliseconds of the activation of the identification of the dominant speaker
     * in a multipoint conference.
     */
    private static final long DECISION_INTERVAL = 300;

    /**
     * The interval of time in milliseconds of idle execution of <code>DecisionMaker</code> after which
     * the latter should cease to exist. The interval does not have to be very long because the
     * background threads running the <code>DecisionMaker</code>s are pooled anyway.
     */
    private static final long DECISION_MAKER_IDLE_TIMEOUT = 15 * 1000;

    /**
     * The name of the <code>DominantSpeakerIdentification</code> property <code>dominantSpeaker</code>
     * which specifies the dominant speaker identified by synchronization source identifier (SSRC).
     */
    public static final String DOMINANT_SPEAKER_PROPERTY_NAME
            = DominantSpeakerIdentification.class.getName() + ".dominantSpeaker";

    /**
     * The interval of time without a call to {@link Speaker#levelChanged(int)} after which
     * <code>DominantSpeakerIdentification</code> assumes that there will be no report of a
     * <code>Speaker</code>'s level within a certain time-frame. The default value of <code>40</code> is
     * chosen in order to allow non-aggressive fading of the last received or measured level and to
     * be greater than the most common RTP packet durations in milliseconds i.e. <code>20</code> and
     * <code>30</code>.
     */
    private static final long LEVEL_IDLE_TIMEOUT = 40;

    /**
     * The (total) number of long time-intervals used for speech activity score evaluation at a
     * specific time-frame.
     */
    private static final int LONG_COUNT = 1;

    /**
     * The threshold in terms of active medium-length blocks which is used during the speech
     * activity evaluation step for the long time-interval.
     */
    private static final int LONG_THRESHOLD = 4;

    /**
     * The maximum value of audio level supported by <code>DominantSpeakerIdentification</code>.
     */
    private static final int MAX_LEVEL = 127;

    /**
     * The minimum value of audio level supported by <code>DominantSpeakerIdentification</code>.
     */
    private static final int MIN_LEVEL = 0;

    /**
     * The number of (audio) levels received or measured for a <code>Speaker</code> to be monitored in
     * order to determine that the minimum level for the <code>Speaker</code> has increased.
     */
    private static final int MIN_LEVEL_WINDOW_LENGTH = 15 /* seconds */ * 1000 /* milliseconds */
            / 20 /* milliseconds per level */;

    /**
     * The minimum value of speech activity score supported by
     * <code>DominantSpeakerIdentification</code>. The value must be positive because (1) we are going
     * to use it as the argument of a logarithmic function and the latter is undefined for negative
     * arguments and (2) we will be dividing by the speech activity score.
     */
    private static final double MIN_SPEECH_ACTIVITY_SCORE = 0.0000000001D;

    /**
     * The threshold in terms of active sub-bands in a frame which is used during the speech
     * activity evaluation step for the medium length time-interval.
     */
    private static final int MEDIUM_THRESHOLD = 7;

    /**
     * The (total) number of sub-bands in the frequency range evaluated for immediate speech
     * activity. The implementation of the class <code>DominantSpeakerIdentification</code> does not
     * really operate on the representation of the signal in the frequency domain, it works with
     * audio levels derived from RFC 6465 &quot;A Real-time Transport Protocol (RTP) Header
     * Extension for Mixer-to-Client Audio Level Indication&quot;.
     */
    private static final int N1 = 13;

    /**
     * The length/size of a sub-band in the frequency range evaluated for immediate speech activity.
     * In the context of the implementation of the class <code>DominantSpeakerIdentification</code>, it
     * specifies the length/size of a sub-unit of the audio level range defined by RFC 6465.
     */
    private static final int N1_SUBUNIT_LENGTH = (MAX_LEVEL - MIN_LEVEL + N1 - 1) / N1;

    /**
     * The number of frames (i.e. {@link Speaker#immediates} evaluated for medium speech activity.
     */
    private static final int N2 = 5;

    /**
     * The number of medium-length blocks constituting a long time-interval.
     */
    private static final int N3 = 10;

    /**
     * The interval of time without a call to {@link Speaker#levelChanged(int)} after which
     * <code>DominantSpeakerIdentification</code> assumes that a non-dominant <code>Speaker</code> is to be
     * automatically removed from {@link #speakers}.
     */
    private static final long SPEAKER_IDLE_TIMEOUT = 60 * 60 * 1000;

    /**
     * The pool of <code>Thread</code>s which run <code>DominantSpeakerIdentification</code>s.
     */
    private static final ExecutorService threadPool
            = ExecutorUtils.newCachedThreadPool(true, "DominantSpeakerIdentification");

    /**
     * Computes the binomial coefficient indexed by <code>n</code> and <code>r</code> i.e. the number of
     * ways of picking <code>r</code> unordered outcomes from <code>n</code> possibilities.
     *
     * @param n the number of possibilities to pick from
     * @param r the number unordered outcomes to pick from <code>n</code>
     * @return the binomial coefficient indexed by <code>n</code> and <code>r</code> i.e. the number of
     * ways of picking <code>r</code> unordered outcomes from <code>n</code> possibilities
     */
    private static long binomialCoefficient(int n, int r)
    {
        int m = n - r; // r = Math.max(r, n - r);

        if (r < m)
            r = m;

        long t = 1;

        for (int i = n, j = 1; i > r; i--, j++)
            t = t * i / j;

        return t;
    }

    private static boolean computeBigs(byte[] littles, byte[] bigs, int threshold)
    {
        int bigLength = bigs.length;
        int littleLengthPerBig = littles.length / bigLength;
        boolean changed = false;

        for (int b = 0, l = 0; b < bigLength; b++) {
            byte sum = 0;

            for (int lEnd = l + littleLengthPerBig; l < lEnd; l++) {
                if (littles[l] > threshold)
                    sum++;
            }
            if (bigs[b] != sum) {
                bigs[b] = sum;
                changed = true;
            }
        }
        return changed;
    }

    private static double computeSpeechActivityScore(int vL, int nR, double p, double lambda)
    {
        double speechActivityScore = Math.log(binomialCoefficient(nR, vL)) + vL * Math.log(p)
                + (nR - vL) * Math.log(1 - p) - Math.log(lambda) + lambda * vL;

        if (speechActivityScore < MIN_SPEECH_ACTIVITY_SCORE)
            speechActivityScore = MIN_SPEECH_ACTIVITY_SCORE;
        return speechActivityScore;
    }

    /**
     * The background thread which repeatedly makes the (global) decision about speaker switches.
     */
    private DecisionMaker decisionMaker;

    /**
     * The synchronization source identifier/SSRC of the dominant speaker in this multipoint
     * conference.
     */
    private Long dominantSSRC;

    /**
     * The last/latest time at which this <code>DominantSpeakerIdentification</code> made a (global)
     * decision about speaker switches. The (global) decision about switcher switches should be
     * made every {@link #DECISION_INTERVAL} milliseconds.
     */
    private long lastDecisionTime;

    /**
     * The time in milliseconds of the most recent (audio) level report or measurement (regardless
     * of the <code>Speaker</code>).
     */
    private long lastLevelChangedTime;

    /**
     * The last/latest time at which this <code>DominantSpeakerIdentification</code> notified the
     * <code>Speaker</code>s who have not received or measured audio levels for a certain time (i.e.
     * {@link #LEVEL_IDLE_TIMEOUT}) that they will very likely not have a level within a certain
     * time-frame of the algorithm.
     */
    private long lastLevelIdleTime;

    /**
     * The <code>PropertyChangeNotifier</code> which facilitates the implementations of adding and
     * removing <code>PropertyChangeListener</code>s to and from this instance and firing
     * <code>PropertyChangeEvent</code>s to the added <code>PropertyChangeListener</code>s.
     */
    private final PropertyChangeNotifier propertyChangeNotifier = new PropertyChangeNotifier();

    /**
     * The relative speech activities for the immediate, medium and long time-intervals,
     * respectively, which were last calculated for a <code>Speaker</code>. Simply reduces the
     * number of allocations and the penalizing effects of the garbage collector.
     */
    private final double[] relativeSpeechActivities = new double[3];

    /**
     * The <code>Speaker</code>s in the multipoint conference associated with this
     * <code>ActiveSpeakerDetector</code>.
     */
    private final Map<Long, Speaker> speakers = new HashMap<>();

    /**
     * Initializes a new <code>DominantSpeakerIdentification</tT> instance.
     */
    public DominantSpeakerIdentification()
    {
    }

    /**
     * Adds a <code>PropertyChangeListener</code> to the list of listeners interested in and notified
     * about changes in the values of the properties of this <code>DominantSpeakerIdentification</code>.
     *
     * @param listener a <code>PropertyChangeListener</code> to be notified about changes in the values of the
     * properties of this <code>DominantSpeakerIdentification</code>
     */
    public void addPropertyChangeListener(PropertyChangeListener listener)
    {
        propertyChangeNotifier.addPropertyChangeListener(listener);
    }

    /**
     * Notifies this <code>DominantSpeakerIdentification</code> instance that a specific
     * <code>DecisionMaker</code> has permanently stopped executing (in its background/daemon
     * <code>Thread</code>). If the specified <code>decisionMaker</code> is the one utilized by this
     * <code>DominantSpeakerIdentification</code> instance, the latter will update its state to reflect
     * that the former has exited.
     *
     * @param decisionMaker the <code>DecisionMaker</code> which has exited
     */
    synchronized void decisionMakerExited(DecisionMaker decisionMaker)
    {
        if (this.decisionMaker == decisionMaker)
            this.decisionMaker = null;
    }

    /**
     * Retrieves a JSON representation of this instance for the purposes of the REST API of Videobridge.
     *
     * By the way, the method name reflects the fact that the method handles an HTTP GET request.
     *
     * @return a <code>JSONObject</code> which represents this instance of the purposes of the REST API of Videobridge
     */
    public JSONObject doGetJSON()
    {
        JSONObject jsonObject;
        if (TimberLog.isTraceEnable) {
            synchronized (this) {
                jsonObject = new JSONObject();

                // dominantSpeaker
                long dominantSpeaker = getDominantSpeaker();
                try {
                    jsonObject.put("dominantSpeaker", (dominantSpeaker == -1) ? null : dominantSpeaker);
                    // speakers
                    Collection<Speaker> speakersCollection = this.speakers.values();
                    JSONArray speakersArray = new JSONArray();

                    for (Speaker speaker : speakersCollection) {
                        // ssrc
                        JSONObject speakerJSONObject = new JSONObject();
                        speakerJSONObject.put("ssrc", Long.valueOf(speaker.ssrc));

                        // levels
                        speakerJSONObject.put("levels", speaker.getLevels());
                        speakersArray.put(speakerJSONObject);
                    }

                    jsonObject.put("speakers", speakersArray);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        else {
            // Retrieving a JSON representation of a DominantSpeakerIdentification has been
            // implemented for the purposes of debugging only.
            jsonObject = null;
        }
        return jsonObject;
    }

    /**
     * Fires a new <code>PropertyChangeEvent</code> to the <code>PropertyChangeListener</code>s registered
     * with this <code>DominantSpeakerIdentification</code> in order to notify about a change in the
     * value of a specific property which had its old value modified to a specific new value.
     *
     * @param property the name of the property of this <code>DominantSpeakerIdentification</code> which had its
     * value changed
     * @param oldValue the value of the property with the specified name before the change
     * @param newValue the value of the property with the specified name after the change
     */
    protected void firePropertyChange(String property, Long oldValue, Long newValue)
    {
        firePropertyChange(property, (Object) oldValue, (Object) newValue);

        if (DOMINANT_SPEAKER_PROPERTY_NAME.equals(property)) {
            long ssrc = (newValue == null) ? -1 : newValue;

            fireActiveSpeakerChanged(ssrc);
        }
    }

    /**
     * Fires a new <code>PropertyChangeEvent</code> to the <code>PropertyChangeListener</code>s registered
     * with this <code>DominantSpeakerIdentification</code> in order to notify about a change in the
     * value of a specific property which had its old value modified to a specific new value.
     *
     * @param property the name of the property of this <code>DominantSpeakerIdentification</code> which had its
     * value changed
     * @param oldValue the value of the property with the specified name before the change
     * @param newValue the value of the property with the specified name after the change
     */
    protected void firePropertyChange(String property, Object oldValue, Object newValue)
    {
        propertyChangeNotifier.firePropertyChange(property, oldValue, newValue);
    }

    /**
     * Gets the synchronization source identifier (SSRC) of the dominant speaker in this multipoint conference.
     *
     * @return the synchronization source identifier (SSRC) of the dominant speaker in this multipoint conference
     */
    public long getDominantSpeaker()
    {
        Long dominantSSRC = this.dominantSSRC;

        return (dominantSSRC == null) ? -1 : dominantSSRC;
    }

    /**
     * Gets the <code>Speaker</code> in this multipoint conference identified by a specific SSRC. If no
     * such <code>Speaker</code> exists, a new <code>Speaker</code> is initialized with the specified
     * <code>ssrc</code>, added to this multipoint conference and returned.
     *
     * @param ssrc the SSRC identifying the <code>Speaker</code> to return
     * @return the <code>Speaker</code> in this multipoint conference identified by the specified <code>ssrc</code>
     */
    private synchronized Speaker getOrCreateSpeaker(long ssrc)
    {
        Long key = ssrc;
        Speaker speaker = speakers.get(key);

        if (speaker == null) {
            speaker = new Speaker(ssrc);
            speakers.put(key, speaker);

            // Since we've created a new Speaker in the multipoint conference, we'll very likely
            // need to make a decision whether there have been speaker switch events soon.
            maybeStartDecisionMaker();
        }
        return speaker;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void levelChanged(long ssrc, int level)
    {
        Speaker speaker;
        long now = System.currentTimeMillis();

        synchronized (this) {
            speaker = getOrCreateSpeaker(ssrc);

            // Note that this ActiveSpeakerDetector is still in use. When it is
            // not in use long enough, its DecisionMaker i.e. background thread
            // will prepare itself and, consequently, this
            // DominantSpeakerIdentification for garbage collection.
            if (lastLevelChangedTime < now) {
                lastLevelChangedTime = now;

                // A report or measurement of an audio level indicates that this
                // DominantSpeakerIdentification is in use and, consequently,
                // that it'll very likely need to make a decision whether there
                // have been speaker switch events soon.
                maybeStartDecisionMaker();
            }
        }
        if (speaker != null)
            speaker.levelChanged(level, now);
    }

    /**
     * Makes the decision whether there has been a speaker switch event. If there has been such an
     * event, notifies the registered listeners that a new speaker is dominating the multipoint conference.
     */
    private void makeDecision()
    {
        // If we have to fire events to any registered listeners eventually, we
        // will want to do it outside the synchronized block.
        Long oldDominantSpeakerValue = null, newDominantSpeakerValue = null;

        synchronized (this) {
            int speakerCount = speakers.size();
            Long newDominantSSRC;

            if (speakerCount == 0) {
                // If there are no Speakers in a multipoint conference, then
                // there are no speaker switch events to detect.
                newDominantSSRC = null;
            }
            else if (speakerCount == 1) {
                // If there is a single Speaker in a multipoint conference, then
                // his/her speech surely dominates.
                newDominantSSRC = speakers.keySet().iterator().next();
            }
            else {
                Speaker dominantSpeaker = (dominantSSRC == null) ? null : speakers.get(dominantSSRC);

                // If there is no dominant speaker, nominate one at random and then
                // let the other speakers compete with the nominated one.
                if (dominantSpeaker == null) {
                    Map.Entry<Long, Speaker> s = speakers.entrySet().iterator().next();
                    dominantSpeaker = s.getValue();
                    newDominantSSRC = s.getKey();
                }
                else {
                    newDominantSSRC = null;
                }
                dominantSpeaker.evaluateSpeechActivityScores();

                double[] relativeSpeechActivities = this.relativeSpeechActivities;
                // If multiple speakers cause speaker switches, they compete among themselves by
                // their relative speech activities in the middle time-interval.
                double newDominantC2 = C2;

                for (Map.Entry<Long, Speaker> s : speakers.entrySet()) {
                    Speaker speaker = s.getValue();

                    // The dominant speaker does not compete with itself. In other words, there
                    // is no use detecting a speaker switch from the dominant speaker to the
                    // dominant speaker. Technically, the relative speech activities are all
                    // zeroes for the dominant speaker.
                    if (speaker == dominantSpeaker)
                        continue;

                    speaker.evaluateSpeechActivityScores();

                    // Compute the relative speech activities for the immediate,
                    // medium and long time-intervals.
                    for (int interval = 0; interval < relativeSpeechActivities.length; ++interval) {
                        relativeSpeechActivities[interval]
                                = Math.log(speaker.getSpeechActivityScore(interval)
                                / dominantSpeaker.getSpeechActivityScore(interval));
                    }

                    double c1 = relativeSpeechActivities[0];
                    double c2 = relativeSpeechActivities[1];
                    double c3 = relativeSpeechActivities[2];

                    if ((c1 > C1) && (c2 > C2) && (c3 > C3) && (c2 > newDominantC2)) {
                        // If multiple speakers cause speaker switches, they compete among
                        // themselves by their relative speech  in the middle time-interval.
                        newDominantC2 = c2;
                        newDominantSSRC = s.getKey();
                    }
                }
            }
            if ((newDominantSSRC != null) && !newDominantSSRC.equals(dominantSSRC)) {
                oldDominantSpeakerValue = dominantSSRC;
                dominantSSRC = newDominantSSRC;
                newDominantSpeakerValue = dominantSSRC;

            } // synchronized (this)
        }

        // Now that we are outside the synchronized block, fire events, if any,
        // to any registered listeners.
        if ((newDominantSpeakerValue != null) &&
                !newDominantSpeakerValue.equals(oldDominantSpeakerValue)) {
            firePropertyChange(DOMINANT_SPEAKER_PROPERTY_NAME,
                    oldDominantSpeakerValue, newDominantSpeakerValue);
        }
    }

    /**
     * Starts a background thread which is to repeatedly make the (global) decision about speaker
     * switches if such a background thread has not been started yet and if the current state of
     * this <code>DominantSpeakerIdentification</code> justifies the start of such a background thread
     * (e.g. there is at least one <code>Speaker</code> in this multipoint conference).
     */
    private synchronized void maybeStartDecisionMaker()
    {
        if ((this.decisionMaker == null) && !speakers.isEmpty()) {
            DecisionMaker decisionMaker = new DecisionMaker(this);
            boolean scheduled = false;

            this.decisionMaker = decisionMaker;
            try {
                threadPool.execute(decisionMaker);
                scheduled = true;
            } finally {
                if (!scheduled && (this.decisionMaker == decisionMaker))
                    this.decisionMaker = null;
            }
        }
    }

    /**
     * Removes a <code>PropertyChangeListener</code> from the list of listeners interested in and
     * notified about changes in the values of the properties of this
     * <code>DominantSpeakerIdentification</code>.
     *
     * @param listener a <code>PropertyChangeListener</code> to no longer be notified about changes in the values
     * of the properties of this <code>DominantSpeakerIdentification</code>
     */
    public void removePropertyChangeListener(PropertyChangeListener listener)
    {
        propertyChangeNotifier.removePropertyChangeListener(listener);
    }

    /**
     * Runs in the background/daemon <code>Thread</code> of {@link #decisionMaker} and makes the
     * decision whether there has been a speaker switch event.
     *
     * @return a negative integer if the <code>DecisionMaker</code> is to exit or a non-negative
     * integer to specify the time in milliseconds until the next execution of the <code>DecisionMaker</code>
     */
    private long runInDecisionMaker()
    {
        long now = System.currentTimeMillis();
        long levelIdleTimeout = LEVEL_IDLE_TIMEOUT - (now - lastLevelIdleTime);
        long sleep = 0;

        if (levelIdleTimeout <= 0) {
            if (lastLevelIdleTime != 0)
                timeoutIdleLevels(now);
            lastLevelIdleTime = now;
        }
        else {
            sleep = levelIdleTimeout;
        }

        long decisionTimeout = DECISION_INTERVAL - (now - lastDecisionTime);

        if (decisionTimeout <= 0) {
            // The identification of the dominant active speaker may be a
            // time-consuming ordeal so the time of the last decision is the
            // time of the beginning of a decision iteration.
            lastDecisionTime = now;
            makeDecision();
            // The identification of the dominant active speaker may be a
            // time-consuming ordeal so the timeout to the next decision
            // iteration should be computed after the end of the decision iteration.
            decisionTimeout = DECISION_INTERVAL - (System.currentTimeMillis() - now);

        }
        if ((decisionTimeout > 0) && (sleep > decisionTimeout))
            sleep = decisionTimeout;

        return sleep;
    }

    /**
     * Runs in the background/daemon <code>Thread</code> of a specific <code>DecisionMaker</code> and makes
     * the decision whether there has been a speaker switch event.
     *
     * @param decisionMaker the <code>DecisionMaker</code> invoking the method
     * @return a negative integer if the <code>decisionMaker</code> is to exit or a non-negative
     * integer to specify the time in milliseconds until the next execution of the <code>decisionMaker</code>
     */
    long runInDecisionMaker(DecisionMaker decisionMaker)
    {
        synchronized (this) {
            // Most obviously, DecisionMakers no longer employed by this
            // DominantSpeakerIdentification should cease to exist as soon as possible.
            if (this.decisionMaker != decisionMaker)
                return -1;

            // If the decisionMaker has been unnecessarily executing long enough, kill it in
            // order to have a more deterministic behavior with respect to disposal.
            if (0 < lastDecisionTime) {
                long idle = lastDecisionTime - lastLevelChangedTime;

                if (idle >= DECISION_MAKER_IDLE_TIMEOUT)
                    return -1;
            }
        }
        return runInDecisionMaker();
    }

    /**
     * Notifies the <code>Speaker</code>s in this multipoint conference who have not received or
     * measured audio levels for a certain time (i.e. {@link #LEVEL_IDLE_TIMEOUT}) that they will
     * very likely not have a level within a certain time-frame of the
     * <code>DominantSpeakerIdentification</code> algorithm. Additionally, removes the non-dominant
     * <code>Speaker</code>s who have not received or measured audio levels for far too long (i.e.
     * {@link #SPEAKER_IDLE_TIMEOUT}).
     *
     * @param now the time at which the timing out is being detected
     */
    private synchronized void timeoutIdleLevels(long now)
    {
        Iterator<Map.Entry<Long, Speaker>> i = speakers.entrySet().iterator();

        while (i.hasNext()) {
            Speaker speaker = i.next().getValue();
            long idle = now - speaker.getLastLevelChangedTime();

            // Remove a non-dominant Speaker if he/she has been idle for far too long.
            if ((SPEAKER_IDLE_TIMEOUT < idle)
                    && ((dominantSSRC == null) || (speaker.ssrc != dominantSSRC))) {
                i.remove();
            }
            else if (LEVEL_IDLE_TIMEOUT < idle) {
                speaker.levelTimedOut();
            }
        }
    }

    /**
     * Represents the background thread which repeatedly makes the (global) decision about speaker
     * switches. Weakly references an associated <code>DominantSpeakerIdentification</code> instance in
     * order to eventually detect that the multipoint conference has actually expired and that the
     * background <code>Thread</code> should perish.
     *
     * @author Lyubomir Marinov
     */
    private static class DecisionMaker implements Runnable
    {
        /**
         * The <code>DominantSpeakerIdentification</code> instance which is repeatedly run into this
         * background thread in order to make the (global) decision about speaker switches. It is a
         * <code>WeakReference</code> in order to eventually detect that the multipoint conference has
         * actually expired and that this background <code>Thread</code> should perish.
         */
        private final WeakReference<DominantSpeakerIdentification> algorithm;

        /**
         * Initializes a new <code>DecisionMaker</code> instance which is to repeatedly run a specific
         * <code>DominantSpeakerIdentification</code> into a background thread in order to make the
         * (global) decision about speaker switches.
         *
         * @param algorithm the <code>DominantSpeakerIdentification</code> to be repeatedly run by the new
         * instance in order to make the (global) decision about speaker switches
         */
        public DecisionMaker(DominantSpeakerIdentification algorithm)
        {
            this.algorithm = new WeakReference<>(algorithm);
        }

        /**
         * Repeatedly runs {@link #algorithm} i.e. makes the (global) decision about speaker
         * switches until the multipoint conference expires.
         */
        @Override
        public void run()
        {
            try {
                do {
                    DominantSpeakerIdentification algorithm = this.algorithm.get();
                    if (algorithm == null) {
                        break;
                    }
                    else {
                        long sleep = algorithm.runInDecisionMaker(this);

                        // A negative sleep value is explicitly supported i.e.
                        // expected and is contracted to mean that this DecisionMaker is
                        // instructed by the algorithm to commit suicide.
                        if (sleep < 0) {
                            break;
                        }
                        else if (sleep > 0) {
                            // Before sleeping, make the currentThread release its reference to
                            // the associated DominantSpeakerIdentification instance.
                            algorithm = null;
                            try {
                                Thread.sleep(sleep);
                            } catch (InterruptedException ie) {
                                // Continue with the next iteration.
                            }
                        }
                    }
                } while (true);
            } finally {
                // Notify the algorithm that this background thread will no
                // longer run it in order to make the (global) decision about
                // speaker switches. Subsequently, the algorithm may decide to
                // spawn another background thread to run the same task.
                DominantSpeakerIdentification algorithm = this.algorithm.get();

                if (algorithm != null)
                    algorithm.decisionMakerExited(this);
            }
        }
    }

    /**
     * Facilitates this <code>DominantSpeakerIdentification</code> in the implementations of adding and
     * removing <code>PropertyChangeListener</code> s and firing <code>PropertyChangeEvent</code>s to the
     * added <code>PropertyChangeListener</code>s.
     *
     * @author Lyubomir Marinov
     */
    private class PropertyChangeNotifier extends org.atalk.util.event.PropertyChangeNotifier
    {
        /**
         * {@inheritDoc}
         *
         * Makes the super implementations (which is protected) public.
         */
        @Override
        public void firePropertyChange(String property, Object oldValue, Object newValue)
        {
            super.firePropertyChange(property, oldValue, newValue);
        }

        /**
         * {@inheritDoc}
         *
         * Always returns this <code>DominantSpeakerIdentification</code>.
         */
        @Override
        protected Object getPropertyChangeSource(String property, Object oldValue, Object newValue)
        {
            return DominantSpeakerIdentification.this;
        }
    }

    /**
     * Represents a speaker in a multipoint conference identified by synchronization source identifier/SSRC.
     *
     * @author Lyubomir Marinov
     */
    private static class Speaker
    {
        private final byte[] immediates = new byte[LONG_COUNT * N3 * N2];

        /**
         * The speech activity score of this <code>Speaker</code> for the immediate time-interval.
         */
        private double immediateSpeechActivityScore = MIN_SPEECH_ACTIVITY_SCORE;

        /**
         * The time in milliseconds of the most recent invocation of {@link #levelChanged(int)}
         * i.e. the last time at which an actual (audio) level was reported or measured for this
         * <code>Speaker</code>. If no level is reported or measured for this <code>Speaker</code> long
         * enough i.e. {@link #LEVEL_IDLE_TIMEOUT}, the associated
         * <code>DominantSpeakerIdentification</code> will presume that this <code>Speaker</code> was muted
         * for the duration of a certain frame.
         */
        private long lastLevelChangedTime = System.currentTimeMillis();

        /**
         * The (history of) audio levels received or measured for this <code>Speaker</code>.
         */
        private final byte[] levels;

        private final byte[] longs = new byte[LONG_COUNT];

        /**
         * The speech activity score of this <code>Speaker</code> for the long time-interval.
         */
        private double longSpeechActivityScore = MIN_SPEECH_ACTIVITY_SCORE;

        private final byte[] mediums = new byte[LONG_COUNT * N3];

        /**
         * The speech activity score of this <code>Speaker</code> for the medium time-interval.
         */
        private double mediumSpeechActivityScore = MIN_SPEECH_ACTIVITY_SCORE;

        /**
         * The minimum (audio) level received or measured for this <code>Speaker</code>. Since
         * <code>MIN_LEVEL</code> is specified for samples generated by a muted audio source, a value
         * equal to <code>MIN_LEVEL</code> indicates that the minimum level for this <code>Speaker</code>
         * has not been determined yet.
         */
        private byte minLevel = MIN_LEVEL;

        /**
         * The (current) estimate of the minimum (audio) level received or measured for this
         * <code>Speaker</code>. Used to increase the value of {@link #minLevel}
         */
        private byte nextMinLevel = MIN_LEVEL;

        /**
         * The number of subsequent (audio) levels received or measured for this <code>Speaker</code>
         * which have been monitored thus far in order to estimate an up-to-date minimum (audio)
         * level received or measured for this <code>Speaker</code>.
         */
        private int nextMinLevelWindowLength;

        /**
         * The synchronization source identifier/SSRC of this <code>Speaker</code> which is unique
         * within a multipoint conference.
         */
        public final long ssrc;

        /**
         * Initializes a new <code>Speaker</code> instance identified by a specific synchronization
         * source identifier/SSRC.
         *
         * @param ssrc the synchronization source identifier/SSRC of the new instance
         */
        public Speaker(long ssrc)
        {
            this.ssrc = ssrc;
            levels = new byte[immediates.length];
        }

        private boolean computeImmediates()
        {
            // The minimum audio level received or measured for this Speaker is
            // the level of "silence" for this Speaker. Since the various
            // Speakers may differ in their levels of "silence", put all
            // Speakers on equal footing by replacing the individual levels of
            // "silence" with the uniform level of absolute silence.
            byte[] immediates = this.immediates;
            byte[] levels = this.levels;
            byte minLevel = (byte) (this.minLevel + N1_SUBUNIT_LENGTH);
            boolean changed = false;

            for (int i = 0; i < immediates.length; ++i) {
                byte level = levels[i];

                if (level < minLevel)
                    level = MIN_LEVEL;

                byte immediate = (byte) (level / N1_SUBUNIT_LENGTH);

                if (immediates[i] != immediate) {
                    immediates[i] = immediate;
                    changed = true;
                }
            }
            return changed;
        }

        private boolean computeLongs()
        {
            return computeBigs(mediums, longs, LONG_THRESHOLD);
        }

        private boolean computeMediums()
        {
            return computeBigs(immediates, mediums, MEDIUM_THRESHOLD);
        }

        /**
         * Computes/evaluates the speech activity score of this <code>Speaker</code> for the immediate time-interval.
         */
        private void evaluateImmediateSpeechActivityScore()
        {
            immediateSpeechActivityScore
                    = computeSpeechActivityScore(immediates[0], N1, 0.5, 0.78);
        }

        /**
         * Computes/evaluates the speech activity score of this <code>Speaker</code> for the long time-interval.
         */
        private void evaluateLongSpeechActivityScore()
        {
            longSpeechActivityScore = computeSpeechActivityScore(longs[0], N3, 0.5, 47);
        }

        /**
         * Computes/evaluates the speech activity score of this <code>Speaker</code> for the medium time-interval.
         */
        private void evaluateMediumSpeechActivityScore()
        {
            mediumSpeechActivityScore = computeSpeechActivityScore(mediums[0], N2, 0.5, 24);
        }

        /**
         * Evaluates the speech activity scores of this <code>Speaker</code> for the immediate, medium,
         * and long time-intervals. Invoked when it is time to decide whether there has been a
         * speaker switch event.
         */
        synchronized void evaluateSpeechActivityScores()
        {
            if (computeImmediates()) {
                evaluateImmediateSpeechActivityScore();
                if (computeMediums()) {
                    evaluateMediumSpeechActivityScore();
                    if (computeLongs())
                        evaluateLongSpeechActivityScore();
                }
            }
        }

        /**
         * Gets the time in milliseconds at which an actual (audio) level was reported or measured
         * for this <code>Speaker</code> last.
         *
         * @return the time in milliseconds at which an actual (audio) level was reported or
         * measured for this <code>Speaker</code> last
         */
        public synchronized long getLastLevelChangedTime()
        {
            return lastLevelChangedTime;
        }

        /**
         * Gets the (history of) audio levels received or measured for this <code>Speaker</code>.
         *
         * @return a <code>byte</code> array which represents the (history of) audio levels received or
         * measured for this <code>Speaker</code>
         */
        byte[] getLevels()
        {
            // The levels of Speaker are internally maintained starting with the
            // last audio level received or measured for this Speaker and ending
            // with the first audio level received or measured for this Speaker.
            // Unfortunately, the method is expected to return levels in reverse order.
            byte[] src = this.levels;
            byte[] dst = new byte[src.length];

            for (int s = src.length - 1, d = 0; d < dst.length; --s, ++d) {
                dst[d] = src[s];
            }
            return dst;
        }

        /**
         * Gets the speech activity score of this <code>Speaker</code> for a specific time-interval.
         *
         * @param interval <code>0</code> for the immediate time-interval, <code>1</code> for the medium
         * time-interval, or <code>2</code> for the long time-interval
         * @return the speech activity score of this <code>Speaker</code> for the time-interval
         * specified by <code>index</code>
         */
        double getSpeechActivityScore(int interval)
        {
            switch (interval) {
                case 0:
                    return immediateSpeechActivityScore;
                case 1:
                    return mediumSpeechActivityScore;
                case 2:
                    return longSpeechActivityScore;
                default:
                    throw new IllegalArgumentException("interval " + interval);
            }
        }

        /**
         * Notifies this <code>Speaker</code> that a new audio level has been received or measured.
         *
         * @param level the audio level which has been received or measured for this <code>Speaker</code>
         */
        @SuppressWarnings("unused")
        public void levelChanged(int level)
        {
            levelChanged(level, System.currentTimeMillis());
        }

        /**
         * Notifies this <code>Speaker</code> that a new audio level has been received or measured at a specific time.
         *
         * @param level the audio level which has been received or measured for this <code>Speaker</code>
         * @param time the (local <code>System</code>) time in milliseconds at which the specified
         * <code>level</code> has been received or measured
         */
        public synchronized void levelChanged(int level, long time)
        {
            // It sounds relatively reasonable that late audio levels should better be discarded.
            if (lastLevelChangedTime <= time) {
                lastLevelChangedTime = time;

                // Ensure that the specified level is within the supported range.
                byte b;
                if (level < MIN_LEVEL)
                    b = MIN_LEVEL;
                else if (level > MAX_LEVEL)
                    b = MAX_LEVEL;
                else
                    b = (byte) level;

                // Push the specified level into the history of audio levels
                // received or measured for this Speaker.
                System.arraycopy(levels, 0, levels, 1, levels.length - 1);
                levels[0] = b;

                // Determine the minimum level received or measured for this Speaker.
                updateMinLevel(b);
            }
        }

        /**
         * Notifies this <code>Speaker</code> that no new audio level has been received or measured for
         * a certain time which very likely means that this <code>Speaker</code> will not have a level
         * within a certain time-frame of a <code>DominantSpeakerIdentification</code> algorithm.
         */
        public synchronized void levelTimedOut()
        {
            levelChanged(MIN_LEVEL, lastLevelChangedTime);
        }

        /**
         * Updates the minimum (audio) level received or measured for this <code>Speaker</code> in
         * light of the receipt of a specific level.
         *
         * @param level the audio level received or measured for this <code>Speaker</code>
         */
        private void updateMinLevel(byte level)
        {
            if (level != MIN_LEVEL) {
                if ((minLevel == MIN_LEVEL) || (minLevel > level)) {
                    minLevel = level;
                    nextMinLevel = MIN_LEVEL;
                    nextMinLevelWindowLength = 0;
                }
                else {
                    // The specified (audio) level is greater than the minimum
                    // level received or measure for this Speaker. However, the
                    // minimum level may be out-of-date by now. Estimate an
                    // up-to-date minimum level and, eventually, make it the
                    // minimum level received or measured for this Speaker.
                    if (nextMinLevel == MIN_LEVEL) {
                        nextMinLevel = level;
                        nextMinLevelWindowLength = 1;
                    }
                    else {
                        if (nextMinLevel > level) {
                            nextMinLevel = level;
                        }
                        nextMinLevelWindowLength++;
                        if (nextMinLevelWindowLength >= MIN_LEVEL_WINDOW_LENGTH) {
                            // The arithmetic mean will increase the minimum
                            // level faster than the geometric mean. Since the
                            // goal is to track a minimum, it sounds reasonable
                            // to go with a slow increase.
                            double newMinLevel = Math.sqrt(minLevel * (double) nextMinLevel);

                            // Ensure that the new minimum level is within the supported range.
                            if (newMinLevel < MIN_LEVEL)
                                newMinLevel = MIN_LEVEL;
                            else if (newMinLevel > MAX_LEVEL)
                                newMinLevel = MAX_LEVEL;

                            minLevel = (byte) newMinLevel;
                            nextMinLevel = MIN_LEVEL;
                            nextMinLevelWindowLength = 0;
                        }
                    }
                }
            }
        }
    }
}
