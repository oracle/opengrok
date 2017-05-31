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
 * Copyright (c) 2016, 2017 Oracle and/or its affiliates. All rights reserved.
 */
$.tablesorter.addParser({
    id: 'dates',
    is: function (s) {
        // return false so this parser is not auto detected
        return false;
    },
    format: function (s) {
        var date = s.match(/^(\d{2})\-(\w{3})\-(\d{4})$/);
        if (!date)
            return new Date().getTime();
        var d = date[1];
        var m = date[2];
        var y = date[3];
        return new Date(m + ' ' + d + ' ' + y).getTime();
    },
    type: 'numeric'
});

$.tablesorter.addParser({
    id: 'groksizes',
    is: function (s) {
        // return false so this parser is not auto detected
        return false;
    },
    format: function (s) {
        /*
         * This correctly handles thousand separator
         * in a big number (either ',' or ' ' or none)
         *
         * In our case there is a little gap between 1000 and 1023 which
         * is still smaller than the next order unit. This should accept all
         * values like:
         * 1,000 or 1 000 or 1,023
         *
         * However it is more generic just in case. It should not have trouble
         * with:
         * 1,000,123,133.235
         * 1 000.4564
         * and with even misspelled numbers:
         * 1,00,345,0.123 (wrong number of digits between the separator)
         * 13,456 13 45.1234 (mixed separators)
         * 1000,123 (wrong number of digits between the separator)
         * 1,123534435,134547435.165165165 (again)
         */
        var parts = s.match(/^(\d{1,3}(?:[, ]?\d{1,3})*(?:\.\d+)?|\.\d+) ?(\w*)$/);

        if (parts === null || parts.length < 3) {
            return 0;
        }

        var num = parts[1].replace(/[, ]/g, "")
        var unit = parts[2];

        // convert to bytes
        if (unit == "KiB") {
            return (num * 1024);
        } else if (unit == "MiB") {
            return (num * 1024 * 1024);
        } else if (unit == "GiB") {
            return (num * 1024 * 1024 * 1024);
        } else {
            return (num);
        }
    },
    type: 'numeric'
});
