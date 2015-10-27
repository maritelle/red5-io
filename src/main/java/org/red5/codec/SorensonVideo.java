/*
 * RED5 Open Source Flash Server - https://github.com/Red5/
 * 
 * Copyright 2006-2015 by respective authors (see below). All rights reserved.
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

package org.red5.codec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.IoConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Red5 video codec for the sorenson video format.
 *
 * VERY simple implementation, just stores last keyframe.
 *
 * @author The Red5 Project
 * @author Joachim Bauch (jojo@struktur.de)
 * @author Paul Gregoire (mondain@gmail.com) 
 */
public class SorensonVideo implements IVideoStreamCodec, IoConstants {

	private Logger log = LoggerFactory.getLogger(SorensonVideo.class);

    /**
     * Sorenson video codec constant
     */
	static final String CODEC_NAME = "SorensonVideo";

    /**
     * Block of data
     */
	private byte[] blockData;
    /**
     * Number of data blocks
     */
	private int dataCount;
    /**
     * Data block size
     */
	private int blockSize;

	/**
	 * Timestamp of keyframe
	 */
	private int keyframeTimestamp;

	/**
	 * Storage for frames buffered since last key frame
	 */
	private final List<FrameData> interframes = new ArrayList<FrameData>(50);

	/**
	 * Number of frames buffered since last key frame
	 */
	private final AtomicInteger numInterframes = new AtomicInteger(0);

	/** Constructs a new SorensonVideo. */
	public SorensonVideo() {
		this.reset();
	}

	/** {@inheritDoc} */
    public String getName() {
		return CODEC_NAME;
	}

	/** {@inheritDoc} */
    public boolean canDropFrames() {
		return true;
	}

	/** {@inheritDoc} */
    public void reset() {
		this.blockData = null;
		this.blockSize = 0;
		this.dataCount = 0;
		this.keyframeTimestamp = 0;
	}

	/** {@inheritDoc} */
    public boolean canHandleData(IoBuffer data) {
		if (data.limit() == 0) {
			// Empty buffer
			return false;
		}

		byte first = data.get();
		boolean result = ((first & 0x0f) == VideoCodec.H263.getId());
		data.rewind();
		return result;
	}

	/** {@inheritDoc} */
    public boolean addData(IoBuffer data, int timestamp) {
		if (data.limit() == 0) {
			// Empty buffer
			return true;
		}

		if (!this.canHandleData(data)) {
			return false;
		}

		byte first = data.get();
		data.rewind();

		int frameType = (first & MASK_VIDEO_FRAMETYPE) >> 4;
		if (frameType != FLAG_FRAMETYPE_KEYFRAME) {
			// Not a keyframe
			try {
				int lastInterframe = numInterframes.getAndIncrement();
				if (frameType != FLAG_FRAMETYPE_DISPOSABLE) {
					log.trace("Buffering interframe #{}", lastInterframe);
					if (interframes.size() < lastInterframe + 1) {
						interframes.add(new FrameData());
					}
					interframes.get(lastInterframe).setData(data, timestamp);
				} else {
					numInterframes.set(lastInterframe);
				}
			} catch (Throwable e) {
				log.error("Failed to buffer interframe", e);
			}
			data.rewind();
			return true;
		}

		numInterframes.set(0);
		keyframeTimestamp = 0;

		// Store last keyframe
		this.dataCount = data.limit();
		if (this.blockSize < this.dataCount) {
			this.blockSize = this.dataCount;
			this.blockData = new byte[this.blockSize];
		}

		data.get(this.blockData, 0, this.dataCount);
		data.rewind();
		return true;
	}

	/** {@inheritDoc} */
    public IoBuffer getKeyframe() {
		if (this.dataCount == 0) {
			return null;
		}

		IoBuffer result = IoBuffer.allocate(this.dataCount);
		result.put(this.blockData, 0, this.dataCount);
		result.rewind();
		return result;
	}

	/** {@inheritDoc} */
	public int getKeyframeTimestamp() {
		return keyframeTimestamp;
	}
    
	public IoBuffer getDecoderConfiguration() {
		return null;
	}

	/** {@inheritDoc} */
	public int getNumInterframes() {
		return numInterframes.get();
	}

	/** {@inheritDoc} */
	public FrameData getInterframe(int index) {
		return interframes.get(index);
	}
}
