/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kk.taurus.playerbase.player;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.PriorityTaskManager;
import com.google.android.exoplayer2.util.Util;

/**
 * The default {@link LoadControl} implementation.
 */
public class CustomLoadControl implements LoadControl {

    /**
     * The default minimum duration of media that the player will attempt to ensure is buffered at all
     * times, in milliseconds.
     * 常量定义了播放器触发加载AV数据的时机，即当前缓冲区AV数据 duration time 小于15秒。
     * 当缓冲AV数据小于15秒时，加载器重新执行加载逻辑。
     */
    public static final int DEFAULT_MIN_BUFFER_MS = 1000 * 180; //小于3分钟时开始缓冲

    /**
     * The default maximum duration of media that the player will attempt to buffer, in milliseconds.
     * 常量定义了播放器进行加载AV数据的上限，即当前缓冲区AV数据 duration time 小于50秒。
     * 直到缓冲AV数据大于50秒，停止加载，进入等待状态。
     */
    public static final int DEFAULT_MAX_BUFFER_MS = 1000 * 720; //最多缓冲10分钟

    /**
     * The default duration of media that must be buffered for playback to start or resume following a
     * user action such as a seek, in milliseconds.
     * 常量定义了播放器播放AV数据的条件，即缓冲区必须满足AV数据 duration time 不小于2.5秒。
     */
    public static final int DEFAULT_BUFFER_FOR_PLAYBACK_MS = 2500;

    /**
     * The default duration of media that must be buffered for playback to resume after a rebuffer, in
     * milliseconds. A rebuffer is defined to be caused by buffer depletion rather than a user action.
     * 常量定义了播放器从 REBUFFER状态(REBUFFER是由于运行时缓冲区耗尽触发导致) 恢复为可播放状态后可播放AV数据的条件，
     * 即缓冲区必须满足AV数据 duration time 不小于5秒。
     */
    public static final int DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5000;

    /**
     * 加载组件持续加载，直到缓冲AV数据大于50秒，停止加载，进入等待状态。
     * 当缓冲AV数据小于15秒时，加载器重新执行加载逻辑。
     * 播放器根据当前缓冲区AV数据 duration time 控制是否播放。
     * */

    /**
     * The default target buffer size in bytes. The value ({@link C#LENGTH_UNSET}) means that the load
     * control will calculate the target buffer size based on the selected tracks.
     */
    public static final int DEFAULT_TARGET_BUFFER_BYTES = C.LENGTH_UNSET;

    /**
     * The default prioritization of buffer time constraints over size constraints.
     */
    public static final boolean DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS = true;

    /**
     * The default back buffer duration in milliseconds.
     */
    public static final int DEFAULT_BACK_BUFFER_DURATION_MS = 0;

    /**
     * The default for whether the back buffer is retained from the previous keyframe.
     */
    public static final boolean DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME = false;

    /**
     * Builder for {@link DefaultLoadControl}.
     */
    public static final class Builder {

        private DefaultAllocator allocator;
        private int minBufferMs;
        private int maxBufferMs;
        private int bufferForPlaybackMs;
        private int bufferForPlaybackAfterRebufferMs;
        private int targetBufferBytes;
        private boolean prioritizeTimeOverSizeThresholds;
        private PriorityTaskManager priorityTaskManager;
        private int backBufferDurationMs;
        private boolean retainBackBufferFromKeyframe;

        /**
         * Constructs a new instance.
         */
        public Builder() {
            allocator = null;
            minBufferMs = DEFAULT_MIN_BUFFER_MS;
            maxBufferMs = DEFAULT_MAX_BUFFER_MS;
            bufferForPlaybackMs = DEFAULT_BUFFER_FOR_PLAYBACK_MS;
            bufferForPlaybackAfterRebufferMs = DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS;
            targetBufferBytes = DEFAULT_TARGET_BUFFER_BYTES;
            prioritizeTimeOverSizeThresholds = DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS;
            priorityTaskManager = null;
            backBufferDurationMs = DEFAULT_BACK_BUFFER_DURATION_MS;
            retainBackBufferFromKeyframe = DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME;
        }

        /**
         * Sets the {@link DefaultAllocator} used by the loader.
         *
         * @param allocator The {@link DefaultAllocator}.
         * @return This builder, for convenience.
         */
        public Builder setAllocator(DefaultAllocator allocator) {
            this.allocator = allocator;
            return this;
        }

        /**
         * Sets the buffer duration parameters.
         *
         * @param minBufferMs                      The minimum duration of media that the player will attempt to ensure is
         *                                         buffered at all times, in milliseconds.
         * @param maxBufferMs                      The maximum duration of media that the player will attempt to buffer, in
         *                                         milliseconds.
         * @param bufferForPlaybackMs              The duration of media that must be buffered for playback to start
         *                                         or resume following a user action such as a seek, in milliseconds.
         * @param bufferForPlaybackAfterRebufferMs The default duration of media that must be buffered
         *                                         for playback to resume after a rebuffer, in milliseconds. A rebuffer is defined to be
         *                                         caused by buffer depletion rather than a user action.
         * @return This builder, for convenience.
         */
        public Builder setBufferDurationsMs(
                int minBufferMs,
                int maxBufferMs,
                int bufferForPlaybackMs,
                int bufferForPlaybackAfterRebufferMs) {
            this.minBufferMs = minBufferMs;
            this.maxBufferMs = maxBufferMs;
            this.bufferForPlaybackMs = bufferForPlaybackMs;
            this.bufferForPlaybackAfterRebufferMs = bufferForPlaybackAfterRebufferMs;
            return this;
        }

        /**
         * Sets the target buffer size in bytes. If set to {@link C#LENGTH_UNSET}, the target buffer
         * size will be calculated based on the selected tracks.
         *
         * @param targetBufferBytes The target buffer size in bytes.
         * @return This builder, for convenience.
         */
        public Builder setTargetBufferBytes(int targetBufferBytes) {
            this.targetBufferBytes = targetBufferBytes;
            return this;
        }

        /**
         * Sets whether the load control prioritizes buffer time constraints over buffer size
         * constraints.
         *
         * @param prioritizeTimeOverSizeThresholds Whether the load control prioritizes buffer time
         *                                         constraints over buffer size constraints.
         * @return This builder, for convenience.
         */
        public Builder setPrioritizeTimeOverSizeThresholds(boolean prioritizeTimeOverSizeThresholds) {
            this.prioritizeTimeOverSizeThresholds = prioritizeTimeOverSizeThresholds;
            return this;
        }

        /**
         * Sets the {@link PriorityTaskManager} to use.
         *
         * @param priorityTaskManager The {@link PriorityTaskManager} to use.
         * @return This builder, for convenience.
         */
        public Builder setPriorityTaskManager(PriorityTaskManager priorityTaskManager) {
            this.priorityTaskManager = priorityTaskManager;
            return this;
        }

        /**
         * Sets the back buffer duration, and whether the back buffer is retained from the previous
         * keyframe.
         *
         * @param backBufferDurationMs         The back buffer duration in milliseconds.
         * @param retainBackBufferFromKeyframe Whether the back buffer is retained from the previous
         *                                     keyframe.
         * @return This builder, for convenience.
         */
        public Builder setBackBuffer(int backBufferDurationMs, boolean retainBackBufferFromKeyframe) {
            this.backBufferDurationMs = backBufferDurationMs;
            this.retainBackBufferFromKeyframe = retainBackBufferFromKeyframe;
            return this;
        }

        /**
         * Creates a {@link DefaultLoadControl}.
         */
        public CustomLoadControl createDefaultLoadControl() {
            if (allocator == null) {
                allocator = new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE);
            }
            return new CustomLoadControl(
                    allocator,
                    minBufferMs,
                    maxBufferMs,
                    bufferForPlaybackMs,
                    bufferForPlaybackAfterRebufferMs,
                    targetBufferBytes,
                    prioritizeTimeOverSizeThresholds,
                    priorityTaskManager,
                    backBufferDurationMs,
                    retainBackBufferFromKeyframe);
        }
    }

    private final DefaultAllocator allocator;

    private final long minBufferUs;
    private final long maxBufferUs;
    private final long bufferForPlaybackUs;
    private final long bufferForPlaybackAfterRebufferUs;
    private final int targetBufferBytesOverwrite;
    private final boolean prioritizeTimeOverSizeThresholds;
    private final PriorityTaskManager priorityTaskManager;
    private final long backBufferDurationUs;
    private final boolean retainBackBufferFromKeyframe;

    private int targetBufferSize;
    private boolean isBuffering;

    /**
     * Constructs a new instance, using the {@code DEFAULT_*} constants defined in this class.
     */
    @SuppressWarnings("deprecation")
    public CustomLoadControl() {
        this(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE));
    }

    /**
     * @deprecated Use {@link Builder} instead.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public CustomLoadControl(DefaultAllocator allocator) {
        this(
                allocator,
                DEFAULT_MIN_BUFFER_MS,
                DEFAULT_MAX_BUFFER_MS,
                DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                DEFAULT_TARGET_BUFFER_BYTES,
                DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS);
    }

    /**
     * @deprecated Use {@link Builder} instead.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public CustomLoadControl(
            DefaultAllocator allocator,
            int minBufferMs,
            int maxBufferMs,
            int bufferForPlaybackMs,
            int bufferForPlaybackAfterRebufferMs,
            int targetBufferBytes,
            boolean prioritizeTimeOverSizeThresholds) {
        this(
                allocator,
                minBufferMs,
                maxBufferMs,
                bufferForPlaybackMs,
                bufferForPlaybackAfterRebufferMs,
                targetBufferBytes,
                prioritizeTimeOverSizeThresholds,
                /* priorityTaskManager= */ null);
    }

    /**
     * @deprecated Use {@link Builder} instead.
     */
    @Deprecated
    public CustomLoadControl(
            DefaultAllocator allocator,
            int minBufferMs,
            int maxBufferMs,
            int bufferForPlaybackMs,
            int bufferForPlaybackAfterRebufferMs,
            int targetBufferBytes,
            boolean prioritizeTimeOverSizeThresholds,
            PriorityTaskManager priorityTaskManager) {
        this(
                allocator,
                minBufferMs,
                maxBufferMs,
                bufferForPlaybackMs,
                bufferForPlaybackAfterRebufferMs,
                targetBufferBytes,
                prioritizeTimeOverSizeThresholds,
                priorityTaskManager,
                DEFAULT_BACK_BUFFER_DURATION_MS,
                DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME);
    }

    protected CustomLoadControl(
            DefaultAllocator allocator,
            int minBufferMs,
            int maxBufferMs,
            int bufferForPlaybackMs,
            int bufferForPlaybackAfterRebufferMs,
            int targetBufferBytes,
            boolean prioritizeTimeOverSizeThresholds,
            PriorityTaskManager priorityTaskManager,
            int backBufferDurationMs,
            boolean retainBackBufferFromKeyframe) {
        assertGreaterOrEqual(bufferForPlaybackMs, 0, "bufferForPlaybackMs", "0");
        assertGreaterOrEqual(
                bufferForPlaybackAfterRebufferMs, 0, "bufferForPlaybackAfterRebufferMs", "0");
        assertGreaterOrEqual(minBufferMs, bufferForPlaybackMs, "minBufferMs", "bufferForPlaybackMs");
        assertGreaterOrEqual(
                minBufferMs,
                bufferForPlaybackAfterRebufferMs,
                "minBufferMs",
                "bufferForPlaybackAfterRebufferMs");
        assertGreaterOrEqual(maxBufferMs, minBufferMs, "maxBufferMs", "minBufferMs");
        assertGreaterOrEqual(backBufferDurationMs, 0, "backBufferDurationMs", "0");

        this.allocator = allocator;
        this.minBufferUs = C.msToUs(minBufferMs);
        this.maxBufferUs = C.msToUs(maxBufferMs);
        this.bufferForPlaybackUs = C.msToUs(bufferForPlaybackMs);
        this.bufferForPlaybackAfterRebufferUs = C.msToUs(bufferForPlaybackAfterRebufferMs);
        this.targetBufferBytesOverwrite = targetBufferBytes;
        this.prioritizeTimeOverSizeThresholds = prioritizeTimeOverSizeThresholds;
        this.priorityTaskManager = priorityTaskManager;
        this.backBufferDurationUs = C.msToUs(backBufferDurationMs);
        this.retainBackBufferFromKeyframe = retainBackBufferFromKeyframe;
    }

    @Override
    public void onPrepared() {
        reset(false);
    }

    @Override
    public void onTracksSelected(Renderer[] renderers, TrackGroupArray trackGroups,
                                 TrackSelectionArray trackSelections) {
        targetBufferSize =
                targetBufferBytesOverwrite == C.LENGTH_UNSET
                        ? calculateTargetBufferSize(renderers, trackSelections)
                        : targetBufferBytesOverwrite;
        allocator.setTargetBufferSize(targetBufferSize);
    }

    @Override
    public void onStopped() {
        reset(true);
    }

    @Override
    public void onReleased() {
        reset(true);
    }

    @Override
    public Allocator getAllocator() {
        return allocator;
    }

    @Override
    public long getBackBufferDurationUs() {
        return backBufferDurationUs;
    }

    @Override
    public boolean retainBackBufferFromKeyframe() {
        return retainBackBufferFromKeyframe;
    }

    @Override
    public boolean shouldContinueLoading(long bufferedDurationUs, float playbackSpeed) {
        boolean targetBufferSizeReached = allocator.getTotalBytesAllocated() >= targetBufferSize;
        boolean wasBuffering = isBuffering;
        long minBufferUs = this.minBufferUs;
        if (playbackSpeed > 1) {
            // The playback speed is faster than real time, so scale up the minimum required media
            // duration to keep enough media buffered for a playout duration of minBufferUs.
            long mediaDurationMinBufferUs =
                    Util.getMediaDurationForPlayoutDuration(minBufferUs, playbackSpeed);
            minBufferUs = Math.min(mediaDurationMinBufferUs, maxBufferUs);
        }
        if (bufferedDurationUs < minBufferUs) {
            isBuffering = prioritizeTimeOverSizeThresholds || !targetBufferSizeReached;
        } else if (bufferedDurationUs > maxBufferUs || targetBufferSizeReached) {
            isBuffering = false;
        } // Else don't change the buffering state
        if (priorityTaskManager != null && isBuffering != wasBuffering) {
            if (isBuffering) {
                priorityTaskManager.add(C.PRIORITY_PLAYBACK);
            } else {
                priorityTaskManager.remove(C.PRIORITY_PLAYBACK);
            }
        }
        return isBuffering;
    }

    @Override
    public boolean shouldStartPlayback(
            long bufferedDurationUs, float playbackSpeed, boolean rebuffering) {
        bufferedDurationUs = Util.getPlayoutDurationForMediaDuration(bufferedDurationUs, playbackSpeed);
        long minBufferDurationUs = rebuffering ? bufferForPlaybackAfterRebufferUs : bufferForPlaybackUs;
        return minBufferDurationUs <= 0
                || bufferedDurationUs >= minBufferDurationUs
                || (!prioritizeTimeOverSizeThresholds
                && allocator.getTotalBytesAllocated() >= targetBufferSize);
    }

    /**
     * Calculate target buffer size in bytes based on the selected tracks. The player will try not to
     * exceed this target buffer. Only used when {@code targetBufferBytes} is {@link C#LENGTH_UNSET}.
     *
     * @param renderers           The renderers for which the track were selected.
     * @param trackSelectionArray The selected tracks.
     * @return The target buffer size in bytes.
     */
    protected int calculateTargetBufferSize(
            Renderer[] renderers, TrackSelectionArray trackSelectionArray) {
        int targetBufferSize = 0;
        for (int i = 0; i < renderers.length; i++) {
            if (trackSelectionArray.get(i) != null) {
                targetBufferSize += Util.getDefaultBufferSize(renderers[i].getTrackType());
            }
        }
        return targetBufferSize;
    }

    private void reset(boolean resetAllocator) {
        targetBufferSize = 0;
        if (priorityTaskManager != null && isBuffering) {
            priorityTaskManager.remove(C.PRIORITY_PLAYBACK);
        }
        isBuffering = false;
        if (resetAllocator) {
            allocator.reset();
        }
    }

    private static void assertGreaterOrEqual(int value1, int value2, String name1, String name2) {
        Assertions.checkArgument(value1 >= value2, name1 + " cannot be less than " + name2);
    }
}
