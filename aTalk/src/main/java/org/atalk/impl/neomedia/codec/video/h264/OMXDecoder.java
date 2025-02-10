/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.codec.video.h264;

import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.service.neomedia.codec.Constants;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.ResourceUnavailableException;
import javax.media.format.RGBFormat;
import javax.media.format.VideoFormat;

/**
 * Implements an H.264 decoder using OpenMAX.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class OMXDecoder extends AbstractCodec2 {
    /**
     * The list of <code>Format</code>s of video data supported as input by <code>OMXDecoder</code> instances.
     */
    private static final Format[] SUPPORTED_INPUT_FORMATS = {new VideoFormat(Constants.H264)};

    /**
     * The list of <code>Format</code>s of video data supported as output by <code>OMXDecoder</code> instances.
     */
    private static final Format[] SUPPORTED_OUTPUT_FORMATS = {
            new RGBFormat(
                    null,
                    Format.NOT_SPECIFIED,
                    Format.intArray,
                    Format.NOT_SPECIFIED,
                    32,
                    0x000000FF, 0x0000FF00, 0x00FF0000)
    };

    static {
        System.loadLibrary("jnopenmax");
    }

    private long ptr;

    /**
     * Initializes a new <code>OMXDecoder</code> instance.
     */
    public OMXDecoder() {
        super("H.264 OpenMAX Decoder", VideoFormat.class, SUPPORTED_OUTPUT_FORMATS);
        inputFormats = SUPPORTED_INPUT_FORMATS;
    }

    private static native void close(long ptr);

    @Override
    protected void doClose() {
        if (ptr != 0) {
            close(ptr);
            ptr = 0;
        }
    }

    private static native long open(Object reserved)
            throws ResourceUnavailableException;

    /**
     * Opens this <code>Codec</code> and acquires the resources that it needs to
     * operate. All required input and/or output formats are assumed to have
     * been set on this <code>Codec</code> before <code>doOpen</code> is called.
     *
     * @throws ResourceUnavailableException if any of the resources that this
     * <code>Codec</code> needs to operate cannot be acquired
     */
    @Override
    protected void doOpen()
            throws ResourceUnavailableException {
        ptr = open(null);
        if (ptr == 0)
            throw new ResourceUnavailableException("open");
    }

    @Override
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer) {
        return BUFFER_PROCESSED_OK;
    }
}
