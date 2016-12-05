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

/*
 * This file contains JavaScript code used by diff.jsp.
 */

/**
 * Diff window plugin.
 *
 * @author Kryštof Tulinger
 */
(function (window, $window) {
    if (!$window || typeof $window.create !== 'function') {
        console.log('The diffWindow plugin requires $.window plugin')
        return;
    }

    var diffWindow = function () {
        this.init = function (options, context) {
            return $.diffWindow = $window.create(options = $.extend({
                title: 'Diff jumper',
                draggable: false,
                init: function ($window) {
                    var $prev, $next
                    var that = this

                    // set initial position by the toggle button
                    that.options.$toggleButton.each(function () {
                        that.$window.css({
                            top: $(this).offset().top,
                            left: $(this).offset().left + $(this).outerWidth(),
                            opacity: 0
                        }).hide();
                    });
                    // bind animation features
                    that.options.$toggleButton.click(function (e) {
                        that.initChanges();
                        var flag = $(this).data("animation-in-progress");
                        if (flag == "showing") {
                            that.$window.stop().animate({
                                top: that.options.$content.scrollTop() +
                                        $(this).offset().top,
                                left: $(this).offset().left + $(this).outerWidth(),
                                opacity: 0
                            }, that.options.animationDuration, function () {
                                that.$window.hide()
                                $(this).data("animation-in-progress", null);
                            });
                            $(this).data("animation-in-progress", "hiding");
                        } else {
                            that.$window.stop().show().animate({
                                top: that.options.$content.scrollTop() +
                                        that.options.$parent.offset().top + 10,
                                left: $(window).outerWidth() - that.$window.outerWidth() - 25,
                                opacity: 1
                            }, that.options.animationDuration, function () {
                                $(this).data("animation-in-progress", null);
                            })
                            $(this).data("animation-in-progress", "showing");
                        }
                        return false
                    });

                    var $controls = $("<div class=\"pull-right\">")
                            .append($prev = $("<a href='#' class='prev' title='Jump to previous chunk (shortcut b)'><< Previous</a>"))
                            .append("<span class=\"pull-rigt\"> | </span>")
                            .append($next = $("<a href='#' class='next' title='Jump to next chunk (shortcut n)'>Next >></a>"))
                            .append($("<div class=\"clearfix\">"))

                    $next.click(function (e) {
                        that.nextHandler.apply(that, [e]);
                    });
                    $prev.click(function (e) {
                        that.prevHandler.apply(that, [e]);
                    });

                    $window.find('.minimize').unbind('click').click(function (e) {
                        that.$window.stop().animate({
                            top: that.options.$content.scrollTop() +
                                    that.options.$toggleButton.offset().top,
                            left: that.options.$toggleButton.offset().left +
                                    that.options.$toggleButton.outerWidth(),
                            opacity: 0
                        }, that.options.animationDuration, function () {
                            that.$window.hide()
                            that.options.$toggleButton.data("animation-in-progress", null);
                        });
                        that.options.$toggleButton.data("animation-in-progress", "hiding");
                    });

                    return $window
                            .attr('id', 'diff_win')
                            .addClass('diff-window')
                            .addClass('diff_navigation_style')
                            .css({
                                top: '150px',
                                right: '20px',
                                'min-width': '300px'})
                            .append($controls)
                            .append(this.$summary)
                            .append(this.$progress)
                },
                load: function ($window) {
                    var that = this
                    $(document).keypress(function (e) {
                        var key = e.keyCode || e.which
                        switch (key) {
                            case 110: // n
                                that.nextHandler(e)
                                break;
                            case 98: // b
                                that.prevHandler(e)
                                break;
                            default:
                        }
                    });
                },
                update: function (data) {
                    var index = this.currentIndex < 0 ? 1 : (this.currentIndex + 1);
                    this.$summary.text(index + "/" + this.$changes.length + " chunks")
                }
            }, options || {
                /*
                 * {
                 *  $parent: // jQuery object for common anchestor of all diff features
                 *  $content: // jQuery object which is anchestor and scrollable - fixing animation
                 *  chunkSelector: // String describing chunk selection
                 *  addSelector: // String describing added lines
                 *  delSelector: // String describin deleted lines
                 *  $toggleButton: // jQuery object of button to toggle the jumper window
                 *  animationDuration: // duration of toggling the jumper window
                 * }
                 */
                $parent: $("#difftable"),
                $content: $("#content"), // first scrollable div
                chunkSelector: ".chunk",
                addSelector: ".a",
                delSelector: ".d",
                $toggleButton: $("#toggle-jumper"),
                animationDuration: 500
            }), $.extend({
                currentIndex: -1,
                $changes: $(),
                $progress: $('<div>').css('text-align', 'center'),
                $summary: $('<div>'),
                initChanges: function () {
                    if (this.$changes.length)
                        return;
                    // is diff in table (udiff/sdiff) or just html text (new/old diff)?
                    var isTable = this.options.$parent.find("table").length > 0
                    // get all changes
                    this.$changes = isTable ? this.options.$parent.find(this.options.chunkSelector) :
                            this.options.$parent.find(this.options.addSelector + "," + this.options.delSelector)
                    this.$window.update();
                },
                progress: function (str) {
                    var $span = $("<p>" + str + "</p>")
                            .animate({opacity: "0.2"}, 1000)
                    $span.hide('fast', function () {
                        $span.remove();
                    });
                    this.$progress.html($span)
                },
                scrollTop: function ($el) {
                    if (this.options.scrollTop) {
                        this.options.scrollTop($el)
                    } else {
                        $("#content").stop().animate({
                            scrollTop: $el.offset().top - this.options.$parent.offset().top
                        }, 500);
                    }
                    return this;
                },
                prevHandler: function (e) {
                    e.preventDefault()
                    this.initChanges();
                    var $current = $(this.$changes[this.currentIndex - 1])

                    if (!$current.length) {
                        this.$window.error("No previous chunk!")
                        return false
                    }

                    this.currentIndex--;
                    this.progress("Going to chunk " + (this.currentIndex + 1) + "/" + this.$changes.length)
                    this.scrollTop($current);
                    this.$window.update();
                    return false
                },
                nextHandler: function (e) {
                    e.preventDefault()
                    this.initChanges();
                    var $current = $(this.$changes[this.currentIndex + 1])
                    if (!$current.length) {
                        this.$window.error("No next chunk!")
                        return false
                    }
                    this.currentIndex++;
                    this.progress("Going to chunk " + (this.currentIndex + 1) + "/" + this.$changes.length)
                    this.scrollTop($current);
                    this.$window.update()
                    return false
                },
            }, context || {}));
        }
    };
    $.diffWindow = new ($.extend(diffWindow, $.diffWindow ? $.diffWindow : {}));
}(window, $.window));

// Code to be called when the DOM for diff.jsp is ready.
$(document).ready(function () {
    $.diffWindow.init();
});
