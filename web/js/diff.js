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

(function(window, $){
    /*
     * DiffJumper module
     *
     * called for example like
     * $("#difftable").diffTable(options)
     * where options are
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
    var diff = function($parent, options) {
        var inner = {
            initialized: false,
            currentIndex: -1,
            $changes: $(),
            options: {},
            defaults: {
                $parent: $("#difftable"),
                $content: $("#content"), //first scrollable div
                chunkSelector: ".chunk",
                addSelector: ".a",
                delSelector: ".d",
                $toggleButton: $("#toggle-jumper"),
                animationDuration: 500
            },
            /*
             * Whole panel looks like this in code
             * Other css rules are described in style.css
             * <div class="diff_navigation">
             *  <div class="header">
             *      <span class="prev summary">4/30 chunks</span>
             *      <a href="#" class="minimize">_</a>
             *  </div>
             *  <div class="controls">
             *      <a href="#" class="prev">&lt;&lt; Previous</a>
             *      <a href="#" class="next">Next &gt;&gt;</a>
             *  </div>
             *  <div class="progress">
             *  </div>
             *  <div class="errors">
             *  </div>
             * </div>
             */
            $panel: null,
            $summary: null,
            $errors: null,
            $progress: null,
            scrollTop: function (top) {
                $('*').scrollTop(top);
            },
            prevHandler: function (e) {
                e.preventDefault()

                inner.initChanges();

                var $current = $(inner.$changes[inner.currentIndex - 1])

                if(!$current.length) {
                    inner.$errors.error ( "No previous chunk!" )
                    return false
                }

                inner.currentIndex --;

                inner.$progress.progress("Going to chunk " + (inner.currentIndex+1) + "/" + inner.$changes.length )

                inner.scrollTop($current.offset().top - inner.options.$parent.offset().top);

                inner.$summary.trigger("diff.summary.refresh");

                return false
            },
            nextHandler: function(e) {
                e.preventDefault()

                inner.initChanges();

                var $current = $(inner.$changes[inner.currentIndex + 1])

                if(!$current.length) {
                    inner.$errors.error ( "No next chunk!" )
                    return false
                }

                inner.currentIndex ++;

                inner.$progress.progress("Going to chunk " + (inner.currentIndex+1) + "/" + inner.$changes.length )

                inner.scrollTop($current.offset().top - inner.options.$parent.offset().top);

                inner.$summary.trigger("diff.summary.refresh");

                return false
            },
            createPanel: function(){
                inner.$panel = $("<div></div>")
                            .appendTo($("body"))
                            .addClass("diff_navigation_style")
                            .addClass("diff_navigation")
                            .hide()

                inner.createHeader()
                inner.createButtons()
                inner.createProgress()
                inner.createErrors()
            },
            createHeader: function() {
                var $cancel = $("<a href='#' class='minimize'>_</a>")
                        .click(function(e){
                            inner.$panel.stop().animate({
                                top: inner.options.$content.scrollTop() +
                                     inner.options.$toggleButton.offset().top,
                                left: inner.options.$toggleButton.offset().left +
                                      inner.options.$toggleButton.width(),
                                opacity: 0
                            }, inner.options.animationDuration, function() {
                                inner.$panel.hide()
                                inner.options.$toggleButton.data("animation-in-progress", null );
                            });
                            inner.options.$toggleButton.data("animation-in-progress", "hiding" );
                        });
                inner.$summary = $("<span class='prev summary'></span>")
                        .text(inner.$changes.length + " chunks")
                        .on("diff.summary.refresh", function (e) {
                                var index = inner.currentIndex < 0 ? 1 : ( inner.currentIndex + 1 );
                                $(this).text ( index + "/" + inner.$changes.length + " chunks" )
                             });

                var $controls = $("<div class=\"header\"></div>")
                $controls.append(inner.$summary)
                $controls.append($cancel)
                inner.$panel.append($controls)
            },
            createButtons: function() {
                var $prev = $("<a href='#' class='prev' title='Jump to previous chunk (shortcut b)'><< Previous</a>")
                        .click(inner.prevHandler)
                var $next = $("<a href='#' class='next' title='Jump to next chunk (shortcut n)'>Next >></a>")
                        .click(inner.nextHandler)

                var $controls = $("<div class=\"controls\"></div>")
                $controls.append($prev)
                $controls.append($next)
                inner.$panel.append($controls)
            },
            createErrors: function(){
                var $errors = $("<div class=\"errors\"></div>")
                $errors.error = function(str) {
                    var $span = $("<p class='error'>" + str + "</p>")
                            .animate({opacity: "0.2"}, 3000)
                    $span.hide('slow', function(){ $span.remove(); });

                    $errors.html($span)
                }
                inner.$panel.append($errors)
                inner.$errors = $errors
            },
            createProgress: function(){
                var $progress = $("<div class=\"progress\"></div>")
                $progress.progress = function(str) {
                    var $span = $("<p>" + str + "</p>")
                            .animate({opacity: "0.2"}, 1000)
                    $span.hide('fast', function(){ $span.remove(); });
                    $progress.html($span)
                    inner.$errors.html("")
                }
                inner.$panel.append($progress)
                inner.$progress = $progress
            },
            initChanges: function(){
                if(inner.$changes.length)
                    return
                // is diff in table (udiff/sdiff) or just html text (new/old diff)?
                var isTable = inner.options.$parent.find("table").length > 0
                // get all changes
                inner.$changes = isTable ? inner.options.$parent.find(inner.options.chunkSelector) :
                           inner.options.$parent.find(inner.options.addSelector + "," + inner.options.delSelector)

                inner.$summary.trigger("diff.summary.refresh");
            },
            init: function(){
                inner.createPanel();
                // set initial position by the toggle button
                inner.options.$toggleButton.each(function(){
                    inner.$panel.css({
                            top: $(this).offset().top,
                            left: $(this).offset().left + $(this).width(),
                            opacity: 0
                        }).hide();
                });
                // bind animation features
                inner.options.$toggleButton.click(function(e){
                   inner.initChanges();
                   var flag = $(this).data("animation-in-progress");
                   if(flag == "showing") {
                        inner.$panel.stop().animate({
                            top: inner.options.$content.scrollTop() +
                                 $(this).offset().top,
                            left: $(this).offset().left + $(this).width(),
                            opacity: 0
                        }, inner.options.animationDuration, function() {
                            inner.$panel.hide()
                            $(this).data("animation-in-progress", null );
                        });
                        $(this).data("animation-in-progress", "hiding" );
                   } else {
                        inner.$panel.stop().show().animate({
                            top: inner.options.$content.scrollTop() +
                                 inner.options.$parent.offset().top,
                            left: $(window).width() - inner.$panel.width() - 25,
                            opacity: 1
                        }, inner.options.animationDuration, function(){
                            $(this).data("animation-in-progress", null );
                        })
                        $(this).data("animation-in-progress", "showing" );
                   }
                   return false
                });
            }
        }

        this.init = (function($parent, options){
            if (inner.initialized)
                return
            inner.options = $.extend({}, inner.defaults, options)

            inner.init ()

            // bind n and b to special events
            $(document).keypress(function(e){
               var key = e.keyCode || e.which
               switch(key) {
                   case 110: // n
                     inner.nextHandler(e)
                   break;
                   case 98: // b
                     inner.prevHandler(e)
                   break;
                   default:
               }
            });

            inner.initialized = true

            return this
        })($parent, options)
    }

    $.fn.diffTable = function(options){
        return this.each(function(){
           new diff($(this), options);
        });
    }

}(window, window.jQuery));

// Code to be called when the DOM for diff.jsp is ready.
$(document).ready(function () {
    $("#difftable").diffTable();
});
