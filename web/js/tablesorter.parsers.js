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
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
        var parts = s.match(/^([\d\.]+) ?(\w*)$/);
        var num = parts[1];
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
