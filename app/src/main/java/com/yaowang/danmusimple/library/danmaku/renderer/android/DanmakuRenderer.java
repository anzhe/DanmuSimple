/*
 * Copyright (C) 2013 Chen Hui <calmer91@gmail.com>
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

package com.yaowang.danmusimple.library.danmaku.renderer.android;

import com.yaowang.danmusimple.library.controller.DanmakuFilters;
import com.yaowang.danmusimple.library.danmaku.model.BaseDanmaku;
import com.yaowang.danmusimple.library.danmaku.model.DanmakuTimer;
import com.yaowang.danmusimple.library.danmaku.model.IDanmakuIterator;
import com.yaowang.danmusimple.library.danmaku.model.IDanmakus;
import com.yaowang.danmusimple.library.danmaku.model.IDisplayer;
import com.yaowang.danmusimple.library.danmaku.renderer.IRenderer;
import com.yaowang.danmusimple.library.danmaku.renderer.Renderer;
import com.yaowang.danmusimple.library.danmaku.renderer.android.DanmakusRetainer;


public class DanmakuRenderer extends Renderer {

    private final DanmakuTimer mStartTimer = new DanmakuTimer();
    private final RenderingState mRenderingState = new RenderingState();
    private com.yaowang.danmusimple.library.danmaku.renderer.android.DanmakusRetainer.Verifier mVerifier = new com.yaowang.danmusimple.library.danmaku.renderer.android.DanmakusRetainer.Verifier() {
        @Override
        public boolean skipLayout(BaseDanmaku danmaku, float fixedTop, int lines, boolean willHit) {
            if (DanmakuFilters.getDefault().filterSecondary(danmaku, lines, 0, mStartTimer, willHit)) {
                danmaku.setVisibility(false);
                return true;
            }
            return false;
        }
    };

    @Override
    public void clear() {
        com.yaowang.danmusimple.library.danmaku.renderer.android.DanmakusRetainer.clear();
        DanmakuFilters.getDefault().clear();
    }

    @Override
    public void release() {
        com.yaowang.danmusimple.library.danmaku.renderer.android.DanmakusRetainer.release();
        DanmakuFilters.getDefault().clear();
    }

    @Override
    public RenderingState draw(IDisplayer disp, IDanmakus danmakus, long startRenderTime) {
        int lastTotalDanmakuCount = mRenderingState.totalDanmakuCount;
        mRenderingState.reset();
        IDanmakuIterator itr = danmakus.iterator();
        int orderInScreen = 0;
        mStartTimer.update(System.currentTimeMillis());
        int sizeInScreen = danmakus.size();
        BaseDanmaku drawItem = null;
        while (itr.hasNext()) {

            drawItem = itr.next();

            if (drawItem.isLate()) {
                break;
            }

            if (!drawItem.hasPassedFilter()) {
                DanmakuFilters.getDefault().filter(drawItem, orderInScreen, sizeInScreen, mStartTimer, false);
            }

            if (drawItem.time < startRenderTime
                    || (drawItem.priority == 0 && drawItem.isFiltered())
                    || drawItem.isFilteredBy(DanmakuFilters.FILTER_TYPE_OVERLAPPING)
                    || drawItem.isFilteredBy(DanmakuFilters.FILTER_TYPE_MAXIMUM_LINES)) {
                continue;
            }

            if (drawItem.getType() == BaseDanmaku.TYPE_SCROLL_RL){
                // 同屏弹幕密度只对滚动弹幕有效
                orderInScreen++;
            }

            // measure
            if (!drawItem.isMeasured()) {
                drawItem.measure(disp);
            }

            // layout
            DanmakusRetainer.fix(drawItem, disp, mVerifier);

            // draw
            if (!drawItem.isOutside() && drawItem.isShown()) {
                if (drawItem.lines == null && drawItem.getBottom() > disp.getHeight()) {
                    continue;    // skip bottom outside danmaku
                }
                int renderingType = drawItem.draw(disp);
                if(renderingType == IRenderer.CACHE_RENDERING) {
                    mRenderingState.cacheHitCount++;
                } else if(renderingType == IRenderer.TEXT_RENDERING) {
                    mRenderingState.cacheMissCount++;
                }
                mRenderingState.addCount(drawItem.getType(), 1);
                mRenderingState.addTotalCount(1);
            }

        }
        
        mRenderingState.nothingRendered = (mRenderingState.totalDanmakuCount == 0);
        mRenderingState.endTime = drawItem != null ? drawItem.time : RenderingState.UNKNOWN_TIME;
        if (mRenderingState.nothingRendered) {
            mRenderingState.beginTime = RenderingState.UNKNOWN_TIME;
        }
        mRenderingState.incrementCount = mRenderingState.totalDanmakuCount - lastTotalDanmakuCount;
        mRenderingState.consumingTime = mStartTimer.update(System.currentTimeMillis());
        return mRenderingState;
    }
    
}