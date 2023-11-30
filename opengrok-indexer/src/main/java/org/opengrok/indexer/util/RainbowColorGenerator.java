/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

/*
 * Portions Copyright (c) 2019, Krystof Tulinger <k.tulinger@seznam.cz>.
 */

package org.opengrok.indexer.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.opengrok.indexer.util.ColorUtil.fromHex;

public class RainbowColorGenerator {

    private static final int COLORS_BETWEEN_ANCHORS = 4;
    private static final Color[] STOP_COLORS = new Color[]{
            fromHex("eaffe2"),
            fromHex("d9e4f9"),
            fromHex("d1d1d1"),
            fromHex("fffbcf"),
            fromHex("ffbfc3"),
    };

    private RainbowColorGenerator() {
    }

    /**
     * Get linear sequence for all stop colors.
     *
     * @return the list of colors
     * @see #STOP_COLORS
     * @see #COLORS_BETWEEN_ANCHORS
     */
    public static List<Color> getOrderedColors() {
        return generateLinearColorSequence(Arrays.asList(STOP_COLORS), COLORS_BETWEEN_ANCHORS);
    }

    /**
     * Generate linear color sequence between given stop colors as {@code anchorColors}
     * and with {@code colorsBetweenAnchors} number of intermediary steps between them.
     *
     * @param anchorColors         the stop colors
     * @param colorsBetweenAnchors number of steps between each pair of stop colors
     * @return the list of colors
     */
    public static List<Color> generateLinearColorSequence(List<? extends Color> anchorColors, int colorsBetweenAnchors) {
        assert colorsBetweenAnchors >= 0;
        if (anchorColors.size() == 1) {
            return Collections.singletonList(anchorColors.get(0));
        }

        int segmentCount = anchorColors.size() - 1;
        List<Color> result = new ArrayList<>(anchorColors.size() + segmentCount * colorsBetweenAnchors);
        result.add(anchorColors.get(0));

        for (int i = 0; i < segmentCount; i++) {
            Color color1 = anchorColors.get(i);
            Color color2 = anchorColors.get(i + 1);

            List<Color> linearColors = generateLinearColorSequence(color1, color2, colorsBetweenAnchors);

            // skip first element from sequence to avoid duplication from connected segments
            result.addAll(linearColors.subList(1, linearColors.size()));
        }

        return result;
    }

    /**
     * Generate linear color sequence between two given colors.
     *
     * @param color1               the first color (to be included in the sequence)
     * @param color2               the last color (to be included in the sequence)
     * @param colorsBetweenAnchors number of colors between the two colors
     * @return the list of colors
     */
    public static List<Color> generateLinearColorSequence(Color color1, Color color2, int colorsBetweenAnchors) {
        assert colorsBetweenAnchors >= 0;

        List<Color> result = new ArrayList<>(colorsBetweenAnchors + 2);
        result.add(color1);

        for (int i = 1; i <= colorsBetweenAnchors; i++) {
            float ratio = (float) i / (colorsBetweenAnchors + 1);

            result.add(new Color(
                    ratio(color1.red, color2.red, ratio),
                    ratio(color1.green, color2.green, ratio),
                    ratio(color1.blue, color2.blue, ratio)
            ));
        }

        result.add(color2);
        return result;
    }

    private static int ratio(int val1, int val2, float ratio) {
        int value = (int) (val1 + (val2 - val1) * ratio);
        return Math.max(Math.min(value, 255), 0);
    }
}
