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
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
 *
 * Portions Copyright 2011 Jens Elkner.
 */

/**
 * Spaces plugin.
 * 
 * Inserts a dummy space between line number and the text so that on copy-paste
 * the white space is preserved.
 * 
 * Internally listens on scroll events and autofills the spaces only for the visible
 * elements.
 * 
 * IMPORTANT: This plugin is strictly dependent on ascending order of lines
 * and on their attribute "name". It performs a binary search which boosts performance
 * of this plugin for really long files.
 * 
 * @author Krystof Tulinger
 */
(function (w, $) {
    var spaces = function () {
        var inner = {
            defaults: {
                interval: 750,
                selector: "a.l, a.hl",
                $parent: null,
                callback: function () {
                    if (!$(this).hasClass("selected")) {
                        $(this).addClass("selected");
                        $(this).text($(this).text() + " ");
                    }
                }
            },
            options: {},
            $collection: $(),
            initialized: false,
            lock: false,
            binarySearch: function (array, key, compare) {
                var lo = 0,
                        hi = array.length - 1,
                        mid,
                        element,
                        cmp;
                while (lo <= hi) {
                    mid = ((lo + hi) >> 1);
                    cmp = compare(array[mid], key)
                    if (cmp === 0) {
                        return mid;
                    } else if (cmp < 0) {
                        lo = mid + 1;
                    } else {
                        hi = mid - 1;
                    }
                }
                return -1;
            },
            handleScrollEvent: function () {
                inner.lock = false;
                
                var myOffset = inner.$collection.first().offset() ? inner.$collection.first().offset().top : 0
                var myHeight = inner.$collection.first().height() || 0
                var parentOffset = inner.options.$parent.offset() ? inner.options.$parent.offset().top : 0
                var parentHeight = inner.options.$parent.height() || 0
                
                var expectations = {
                    // the first element in viewport
                    start: Math.floor(
                            Math.abs(
                                Math.min(myOffset - parentOffset, 0)
                                ) / myHeight
                           ),
                    // the last element in viewport
                    end: Math.ceil(
                            (Math.abs(
                                Math.min(myOffset - parentOffset, 0)
                                ) + parentHeight
                            ) / myHeight
                         ),
                    
                };

                var indices = {
                    start: 0,
                    end: inner.$collection.length
                };

                var cmp = function (a, key) {
                    return $(a).attr("name") - key; // comparing the "name" attribute with the desired value
                };


                indices.start = inner.binarySearch(inner.$collection, expectations.start, cmp);
                indices.end = inner.binarySearch(inner.$collection, expectations.end, cmp);

                /** cutoffs */
                indices.start = Math.max(0, indices.start);
                indices.start = Math.min(inner.$collection.length - 1, indices.start);

                if (indices.end === -1)
                    indices.end = inner.$collection.length - 1;
                indices.end = Math.min(inner.$collection.length - 1, indices.end);

                /** calling callback for every element in the viewport */
                for (var i = indices.start; i <= indices.end; i++) {
                    inner.options.callback.apply(inner.$collection[i])
                }
            },
            init: function () {

                if (inner.initialized) {
                    return;
                }

                inner.$collection = inner.options.$parent.find(inner.options.selector);

                if (inner.$collection.length <= 0) {
                    return;
                }

                var scrollHandler = function (e) {
                    if (inner.lock) {
                        return;
                    }
                    inner.lock = true;
                    setTimeout(inner.handleScrollEvent, inner.options.interval);
                };
                inner.options.$parent
                        .scroll(scrollHandler)
                        .resize(scrollHandler)
                        .scroll() // fire the event if user has not scrolled
                inner.initialized = true;
            }
        };

        this.init = function (options) {
            inner.options = $.extend({}, inner.defaults, {$parent: $("#content")}, options)
            inner.init();
            return this;
        }
    };

    $.spaces = new ($.extend(spaces, $.spaces ? $.spaces : {}));
})(window, window.jQuery);

(function(window, $) {
   
    var hash = function () {
        var inner = {
            self: this,
            initialized: false,
            highlighted: [],
            defaults: {
              highlightedClass: 'target',
              linkSelectorTemplate: '{parent} a[name={n}]',
              clickSelector: '{parent} a.l, {parent} a.hl',
              parent: 'div#src',
              autoScroll: true,
              autoScrollDuration: 500,
              tooltip: true
            },
            options: {},
            $tooltip: null,
            bindClickHandler: function() {
                $(inner.format(inner.options.clickSelector, {parent: inner.options.parent})).click (function (e){
                    if(e.shiftKey) {
                        // shift pressed
                        var val = inner.toInt($(this).attr("name"))
                        if(!val){
                            return false
                        }

                        var l = inner.getLinesParts(window.location.hash)

                        if(l.length == 2) {
                            window.location.hash = "#" + Math.min(l[0], val) + "-" + Math.max(val, l[1])
                        } else if ( l.length == 1){
                            window.location.hash = "#" + Math.min(l[0], val) + "-" + Math.max(l[0], val) 
                        }
                        return false
                    }
                    return true
                })                    
            },
            
            getHashParts: function (hash) {
                if(!hash || hash == "")
                    return hash;
                return (hash = hash.split("#")).length > 1 ? hash[1] : "";
            },

            getLinesParts: function ( hashPart ) {
              hashPart = inner.getHashParts(hashPart)
              if (!hashPart || hashPart == "")
                  return hashPart;
              var s = hashPart.split("-")
              if(s.length > 1 && inner.toInt(s[0]) && inner.toInt(s[1]))
                  return [ inner.toInt(s[0]), inner.toInt(s[1]) ]
              if(s.length > 0 && inner.toInt(s[0]))
                  return [ inner.toInt(s[0]) ]
              return []
            },

            lines: function (urlPart) {
                p = inner.getLinesParts(urlPart)
                if(p.length == 2) {
                    var l = [];
                    for ( var i = Math.min(p[0],p[1]); i <= Math.max(p[0], p[1]); i ++ )
                        l.push(i);
                    return l;
                } else if (p.length == 1){
                    return [ p[0] ]
                }
                return [];
            },
        

            reload: function(e){
                for ( var i = 0; i < inner.highlighted.length; i ++ ) {
                    // remove color
                    inner.highlighted[i].removeClass(inner.options.highlightedClass)
                }
                inner.highlighted = []

                var lines = inner.lines(window.location.hash);

                if(lines.length < 1) {
                    // not a case of line highlighting
                    return
                }

                for ( var i = 0; i < lines.length; i ++ ) {
                    // color
                    var slc = inner.format(inner.options.linkSelectorTemplate, { "parent": inner.options.parent,
                                                                                  "n": lines[i] } );
                    var el = $(slc).addClass(inner.options.highlightedClass)
                    inner.highlighted.push(el)
                }                   
            },
            format: function(format) {
                var args = Array.prototype.slice.call(arguments, 1);
                args = args.length > 0 ? typeof args[0] === "object" ? args[0] : args : args;
                return format.replace(/{([a-zA-Z0-9_-]+)}/g, function(match, number) {
                  return typeof args[number] != 'undefined'
                    ? args[number] 
                    : match
                  ;
                });
            },
            toInt: function (string) {
                return parseInt(string)
            },
            scroll: function (){
                if(!inner.options.autoScroll)
                    return
   
                var lines = inner.getLinesParts(window.location.hash);
                if (lines.length > 0) {
                    var line = lines[0]; // first line
                    var $line = $(inner.format(inner.options.linkSelectorTemplate, {
                        parent: inner.options.parent,
                        n: line
                    }));
                    if ($line.length > 0) {
                        // if there is such element identified with the line number
                        // we can scroll to it
                        $("#content").animate({
                            scrollTop: $(inner.format(inner.options.linkSelectorTemplate, {
                                parent: inner.options.parent,
                                n: line
                            })).offset().top - $(inner.options.parent).offset().top
                        }, inner.options.autoScrollDuration);
                    }
                }
            },
            tooltip: function() {
                if(!inner.options.tooltip)
                    return
                
                inner.$tooltip = inner.$tooltip ? 
                                    inner.$tooltip :
                                    $("<div>Did you know? You can select a range of lines<br /> by clicking on the other while holding shift key.</div>")
                                    .appendTo($("body"))
                                    .hide()
                                    .addClass("tooltip")
                                    .addClass("diff_navigation_style")
                
                
                $(inner.format(inner.options.clickSelector, {parent: inner.options.parent}))
                .click(function(e) {
                    if(!inner.options.tooltip)
                        return
                   // show box
                   var $el = $(this)
                   setTimeout(function () {
                    inner.$tooltip
                            .show()
                            .stop()
                            .fadeIn()
                            .fadeOut( 5000 )
                            .offset({ 
                                top: $el.offset().top + 20, 
                                left: $el.offset().left + $el.width() + 5 
                            });
                   }, 300);
                   inner.options.tooltip = false;
                })
            }
        } // inner
        
        this.init = function (options) {
            if ( inner.initialized ) {
                return this;
            }

            inner.options = $.extend(inner.defaults, options, {})
            
            $(window).on("hashchange", inner.reload)
            
            inner.reload()
            
            inner.tooltip()
            
            inner.bindClickHandler()
            
            inner.scroll()

            inner.initialized = true
            
            return this;
        }
    }
    $.hash = new ($.extend(hash, $.hash ? $.hash : {}));
}) (window, window.jQuery);

/**
 * General on-demand script downloader
 */
(function (window, document, $) {
    var script = function () {
        var self = this
        this.scriptsDownloaded = {};
        this.defaults = {
            contextPath: window.contextPath,
        }

        this.options = $.extend(this.defaults, {});

        this.loadScript = function (url) {
            if (!/^[a-z]{3,5}:\/\//.test(url)) { // dummy test for remote prefix
                url = this.options.contextPath + '/' + url
            }
            if (url in this.scriptsDownloaded) {
                return this.scriptsDownloaded[url]
            }
            return this.scriptsDownloaded[url] = $.ajax({
                url: url,
                dataType: 'script',
                cache: true,
                timeout: 10000
            }).fail(function () {
                console.debug('Failed to download "' + url + '" module')
            });
        }
    };
    $.script = new ($.extend(script, $.script ? $.script : {}));
})(window, document, jQuery);

/**
 * General window plugin
 *
 * This plugin allows you to create a new window inside the browser. The main
 * interface is create function.
 *
 * Usage:
 * $myWindow = $.window.create({
 *  // default options (later available via this.options)
 *  project: 'abcd', // not existing in window options and will be filled
 *  draggable: false, // override the window defaults
 *  // callbacks for events
 *  init: function ($window) {
 *      // called when creating the new window
 *      // you can modify the new window object - it's jquery object
 *  },
 *  load: function ($window) {
 *      // called when the page is successfully loaded
 *      // you can attach some handlers and fill some options with DOM values
 *  },
 *  update: function(data) {
 *      // called when update is called on your window bypassing the data param
 *      // you can modify the window content or other DOM content
 *  }
 * }, {
 *      // context object - can contain other helper variables and functions
 *      // it's available in the callbacks as the 'this' variable
 *      // the window itself is available as this.$window
 *      modified: false,
 *      modify: function () {
 *          this.modified = true;
 *      },
 * })
 *
 * The new $myWindow object is jQuery object - you can call jQuery functions on it.
 * It doesn't really make sense to call all of the jQuery functions however
 * some of them might be useful: toggle, hide, show and so on.
 *
 * The window object also provides some useful functions like
 * $myWindow.error(message) to display an error in this.$errors element or
 * $myWindow.update(data) to trigger your update callback with given data or
 * $myWindow.move(position) to move the window to the given position
 *    if no position is given it may be determined from the mouse position
 *
 * @author Kryštof Tulinger
 */
(function (browserWindow, document, $, $script) {
    var window = function () {
        var inner = function (options, context) {
            var self = this;
            // private
            this.context = context;
            this.callbacks = {
                init: [],
                load: [],
                update: [],
            };
            this.$window = undefined;
            this.$errors = undefined;
            this.clientX = 0;
            this.clientY = 0;
            this.pendingUpdates = []

            /**
             * Default values for the window options.
             */
            this.defaults = {
                title: 'Window',
                appendDraggable: '#content',
                draggable: true,
                draggableScript: 'js/jquery-ui-1.12.0-draggable.min.js', // relative to context
                contextPath: browserWindow.contextPath,
                parent: undefined,
                load: undefined,
                init: undefined,
                handlers: undefined
            }

            this.options = $.extend({}, this.defaults, options);

            this.addCallback = function (name, callback, context) {
                context = context || this.getSelfContext;
                if (!this.callbacks || !$.isArray(this.callbacks[name])) {
                    this.callbacks[name] = []
                }
                this.callbacks[name].push({
                    'callback': callback,
                    'context': context
                })
            }

            this.fire = function (name, args) {
                if (!this.callbacks || !$.isArray(this.callbacks[name])) {
                    return;
                }

                for (var i = 0; i < this.callbacks[name].length; i++) {
                    this.$window = (this.callbacks[name][i].callback.apply(
                            this.callbacks[name][i].context.call(this),
                            args || [this.$window])) || this.$window
                }
            }

            this.getContext = function () {
                return $.extend(this.context, {options: this.options});
            }

            this.getSelfContext = function () {
                return this;
            }



            // private
            this.cropPosition = function($w, position) {
                var w = {
                    height: $w.outerHeight(true),
                    width: $w.outerWidth(true)
                }
                var bw = {
                    height: $(browserWindow).outerHeight(true),
                    width: $(browserWindow).outerWidth(true),
                    yOffset: browserWindow.pageYOffset,
                    xOffset: browserWindow.pageXOffset
                }
                position.top -= Math.max(0, position.top + w.height - bw.yOffset - bw.height + 20)
                position.left -= Math.max(0, position.left + w.width - bw.xOffset - bw.width + 20)
                return position
            }

            this.determinePosition = function () {
                var position = {
                    top: this.clientY,
                    left: this.clientX
                }
                return this.cropPosition(this.$window, position)
            }

            this.makeMeDraggable = function () {
                if (!$script || typeof $script.loadScript !== 'function') {
                    console.log("The window plugin requires $.script plugin when draggable option is 'true'")
                    return;
                }

                $script.loadScript(this.options.draggableScript).done(function () {
                    self.$window.draggable({
                        appendTo: self.options.draggableAppendTo || $('body'),
                        helper: 'clone',
                        start: function () {
                            $(this).hide();
                        },
                        stop: function (e, ui) {
                            $(this).show().offset(ui.offset).css('position', 'fixed');
                        },
                        create: function (e, ui) {
                            $(this).css('position', 'fixed');
                        }
                    });
                });
            }

            this.addCallback('init', function ($window) {
                var $top, $close, $controls

                $top = $("<div>").addClass('clearfix')
                        .append($("<div>").addClass("pull-left").append($("<b>").text(this.options.title || "Window")))
                        .append($close = $("<a href=\"#\" class=\"pull-right minimize\">x</a>"))

                $controls = $('<div>')
                        .addClass('clearfix')
                        .append(self.$errors = $('<div>').css('text-align', 'center'))

                $window = $("<div>")
                        .addClass('window')
                        .addClass('diff_navigation_style')
                        .css('z-index', 15000)
                        .hide()
                        .append($top)
                        .append($("<hr>"))
                        .append($controls)

                $close.click(function () {
                    $window.hide()
                    return false;
                });

                /**
                 * Display custom error message in the window
                 * @param {string} msg message
                 * @returns self
                 */
                $window.error = function (msg) {
                    var $span = $("<p class='error'>" + msg + "</p>")
                            .animate({opacity: "0.2"}, 3000)
                    $span.hide('slow', function () {
                        $span.remove();
                    });
                    self.$errors.html($span)
                    return this;
                }

                /**
                 * Move the window to the position. If no position is given
                 * it may be determined from the mouse position.
                 *
                 * @param {object} position object with top and left attributes
                 * @returns self
                 */
                $window.move = function (position) {
                    position = position || self.determinePosition()
                    return this.css(position);
                };

                /**
                 * Display or hide the window.
                 *
                 * We override this method from jquery to manually
                 * trigger the hide() and show() methods
                 * which may be used in the descendants.
                 *
                 * @returns self
                 */
                $window.toggle = function() {
                    var action = this.is(':visible') ? this.hide : this.show;
                    return action.apply(this, arguments);
                }

                /**
                 * Toggle and move the window to the current mouse position
                 *
                 * @returns self
                 */
                $window.toggleAndMove = function () {
                    return $window.toggle().move();
                }

                /**
                 * Update the window with given data.
                 *
                 * @param {mixed} data
                 * @returns {undefined}
                 */
                $window.update = function (data) {
                    if (this.loaded) {
                        self.fire('update', [data])
                    } else {
                        self.pendingUpdates.push({data: data})
                    }
                    return this;
                }

                // insert window into context
                this.context = $.extend(this.context, {$window: $window});

                // set us as initialized
                $window.initialized = true;
                // set us as not loaded
                $window.loaded = false;

                return $window;
            });

            this.addCallback('load', function ($window) {
                var that = this
                $(document).mousemove(function (e) {
                    that.clientX = e.pageX;
                    that.clientY = e.pageY;
                })
                $(document).keyup(function (e) {
                    var key = e.keyCode
                    switch (key) {
                        case 27: // esc
                            that.$window.hide();
                            break;
                        default:
                    }
                    return true;
                });
            })

            if (this.options.draggable) {
                this.addCallback('load', this.makeMeDraggable)
            }

            this.addCallback('load', function ($window) {
                this.$window.appendTo(this.options.parent ? $(this.options.parent) : $("body"));
            })

            if (this.options.init && typeof this.options.init === 'function') {
                this.addCallback('init', this.options.init, this.getContext)
            }

            if (self.options.load && typeof self.options.load === 'function') {
                this.addCallback('load', this.options.load, this.getContext)
            }

            if (self.options.update && typeof self.options.update === 'function') {
                this.addCallback('update', this.options.update, this.getContext)
            }

            $(function () {
                self.fire('load');
                self.$window.loaded = true;

                for (var i = 0; i < self.pendingUpdates.length; i++) {
                    self.fire('update', [self.pendingUpdates[i].data])
                }
                self.pendingUpdates = []
            })

            this.fire('init')

            return this.$window;
        };

        /**
         * Create a window.
         *
         * @param {hash} options containing default options and callbacks
         * @param {hash} context other helper variables and functions
         * @returns new window object
         */
        this.create = function (options, context) {
            return new inner(options, context)
        }
    };
    $.window = new ($.extend(window, $.window ? $.window : {}));
})(window, document, jQuery, jQuery.script);

/**
 * Intelligence window plugin.
 * 
 * Reworked to use Jquery in 2016
 */
(function (browserWindow, document, $, $window) {
    if (!$window || typeof $window.create !== 'function') {
        console.log("The intelligenceWindow plugin requires $.window plugin")
        return;
    }

    var intelliWindow = function () {
        this.initialised = false;
        this.init = function (options, context) {
            return $.intelliWindow = $window.create($.extend({
                title: 'Intelligence window',
                selector: 'a.intelliWindow-symbol',
                google_url: 'https://www.google.com/search?q=',
                project: undefined,
                init: function ($window) {
                    var $highlight, $unhighlight, $unhighlightAll, $prev, $next

                    var $firstList = $("<ul>")
                            .append($("<li>").append(
                                    $highlight = $("<a href=\"#\" title=\"Highlight\">" +
                                            "<span>Highlight</span> <b class=\"symbol-name\"></b></a>")))
                            .append($("<li>").append(
                                    $unhighlight = $("<a href=\"#\" title=\"Unhighlight\">" +
                                            "<span>Unhighlight</span> <b class=\"symbol-name\"></b></a>")))
                            .append($("<li>").append(
                                    $unhighlightAll = $("<a href=\"#\" title=\"Unhighlight all\">" +
                                            "<span>Unhighlight all</span></a>")))

                    this.bindOnClick($highlight, this.highlight)
                    this.bindOnClick($unhighlight, this.unhighlight)
                    this.bindOnClick($unhighlightAll, this.unhighlightAll);

                    var $secondList = $("<ul>")
                            .append($("<li>").append(
                                    $("<a class=\"search-defs\" href=\"#\" target=\"_blank\">" +
                                            "<span>Search for definitions of</span> <b class=\"symbol-name\"></b></a>")))
                            .append($("<li>").append(
                                    $("<a class=\"search-refs\" href=\"#\" target=\"_blank\">" +
                                            "<span>Search for references of</span> <b class=\"symbol-name\"></b></a>")))
                            .append($("<li>").append(
                                    $("<a class=\"search-full\" href=\"#\" target=\"_blank\">" +
                                            "<span>Do a full search with</span> <b class=\"symbol-name\"></b></a>")))
                            .append($("<li>").append(
                                    $("<a class=\"search-files\" href=\"#\" target=\"_blank\">" +
                                            "<span>Search for file names that contain</span> <b class=\"symbol-name\"></b></a>")))

                    var $thirdList = $("<ul>")
                            .append($("<li>").append(
                                    $("<a class=\"search-google\" href=\"#\" target=\"_blank\">" +
                                            "<span>Google</span> <b class=\"symbol-name\"></b></a>")))

                    var $controls = $("<div class=\"pull-right\">")
                            .append($next = $("<a href=\"#\" title=\"next\" class=\"pull-right\">Next >></a>"))
                            .append("<span class=\"pull-right\"> | </span>")
                            .append($prev = $("<a href=\"#\" title=\"prev\" class=\"pull-right\"><< Prev </a>"))
                            .append($("<div class=\"clearfix\">"))
                            .append(this.$errors = $("<span class=\"clearfix\">"))

                    this.bindOnClick($next, this.scrollToNextElement, 1);
                    this.bindOnClick($prev, this.scrollToNextElement, -1);

                    return $window
                            .attr('id', 'intelli_win')
                            .addClass('intelli-window')
                            .append($controls)
                            .append($("<h2>").addClass('symbol-name'))
                            .append($("<span>").addClass('symbol-description'))
                            .append($("<hr>"))
                            .append($("<h5>").text("In current file"))
                            .append($firstList)
                            .append($("<h5>").text("In project \"" + this.project + "\""))
                            .append($secondList)
                            .append($("<h5>").text("On Google"))
                            .append($thirdList);
                },
                load: function ($window) {
                    var that = this;
                    $(document).keypress(function (e) {
                        var key = e.which;
                        switch (key) {
                            case 49: // 1
                                if (that.symbol) {
                                    that.$window.toggleAndMove();
                                }
                                break;
                            case 50: // 2
                                if (that.symbol) {
                                    that.unhighlight(that.symbol).length === 0 && that.highlight(that.symbol);
                                }
                                break;
                            case 51: // 3
                                that.unhighlightAll();
                                break;
                            case 110: // n
                                that.scrollToNextElement(1);
                                break;
                            case 98: // b
                                that.scrollToNextElement(-1);
                                break;
                            default:
                        }
                        return true;
                    });
                    this.getSymbols().mouseover(function () {
                        that.changeSymbol($(this));
                    });
                    this.project = this.options.project || $("input[name='project']").val();
                    this.contextPath = browserWindow.contextPath;
                },
            }, options || {}), $.extend({
                symbol: undefined,
                project: undefined,
                $symbols: undefined,
                $current: undefined,
                $last_highlighted_current: $(),
                $search_defs: undefined,
                $search_refs: undefined,
                $search_full: undefined,
                $search_files: undefined,
                $search_google: undefined,
                changeSymbol: function ($el) {
                    this.$current = $el
                    this.$last_highlighted_current = $el.hasClass("symbol-highlighted") ? $el : this.$last_highlighted_current
                    this.symbol = $el.text()
                    this.place = $el.data("definition-place")
                    this.$window.find('.hidden-on-start').show()
                    this.$window.find(".symbol-name").text(this.symbol);
                    this.$window.find(".symbol-description").text(this.getSymbolDescription(this.place))
                    this.modifyLinks();
                },
                modifyLinks: function () {
                    this.$search_defs = this.$search_defs || this.$window.find('.search-defs')
                    this.$search_refs = this.$search_refs || this.$window.find('.search-refs')
                    this.$search_full = this.$search_full || this.$window.find('.search-full')
                    this.$search_files = this.$search_files || this.$window.find('.search-files')
                    this.$search_google = this.$search_google || this.$window.find('.search-google')

                    this.$search_defs.attr('href', this.getSearchLink('defs'));
                    this.$search_refs.attr('href', this.getSearchLink('refs'));
                    this.$search_full.attr('href', this.getSearchLink('q'));
                    this.$search_files.attr('href', this.getSearchLink('path'));
                    this.$search_google.attr('href', this.options.google_url + this.symbol)
                },
                getSearchLink: function (query) {
                    return this.options.contextPath + '/search?' + query + '=' + this.symbol + '&project=' + this.project;
                },
                getSymbolDescription: function (place) {
                    switch (place) {
                        case "def":
                            return "A declaration or definition.";
                        case "defined-in-file":
                            return "A symbol declared or defined in this file.";
                        case "undefined-in-file":
                            return "A symbol declared or defined elsewhere.";
                        default:
                            // should not happen
                            return "Something I have no idea about.";
                    }
                },
                getSymbols: function () {
                    return (this.$symbols = this.$symbols || $(this.options.selector));
                },
                highlight: function (symbol) {
                    if (this.$current.text() === symbol) {
                        this.$last_highlighted_current = this.$current;
                    }
                    return this.getSymbols().filter(function () {
                        return $(this).text() === symbol;
                    }).addClass('symbol-highlighted')
                },
                unhighlight: function (symbol) {
                    if (this.$last_highlighted_current &&
                            this.$last_highlighted_current.text() === symbol &&
                            this.$last_highlighted_current.hasClass('symbol-highlighted')) {
                        var i = this.getSymbols().index(this.$last_highlighted_current)
                        this.$last_highlighted_jump = this.getSymbols().slice(0, i).filter('.symbol-highlighted').last();
                    }
                    return this.getSymbols().filter(".symbol-highlighted").filter(function () {
                        return $(this).text() === symbol;
                    }).removeClass('symbol-highlighted')

                },
                unhighlightAll: function () {
                    this.$last_highlighted_current = undefined
                    return this.getSymbols().filter(".symbol-highlighted").removeClass("symbol-highlighted")
                },
                scrollTop: function ($el) {
                    if (this.options.scrollTop) {
                        this.options.scrollTop($el)
                    } else {
                        $("#content").stop().animate({
                            scrollTop: $el.offset().top - $("#src").offset().top
                        }, 500);
                    }
                },
                scrollToNextElement: function (direction) {
                    var UP = -1;
                    var DOWN = 1;
                    var $highlighted = this.getSymbols().filter(".symbol-highlighted");
                    var $el = $highlighted.length && this.$last_highlighted_current
                            ? this.$last_highlighted_current
                            : this.$current;
                    var indexOfCurrent = this.getSymbols().index($el);

                    switch (direction) {
                        case DOWN:
                            $el = this.getSymbols().slice(indexOfCurrent + 1);
                            if ($highlighted.length) {
                                $el = $el.filter('.symbol-highlighted')
                            }
                            if (!$el.length) {
                                this.$window.error("This is the last occurence!")
                                return;
                            }
                            $el = $el.first();
                            break;
                        case UP:
                            $el = this.getSymbols().slice(0, indexOfCurrent);
                            if ($highlighted.length) {
                                $el = $el.filter('.symbol-highlighted')
                            }
                            if (!$el.length) {
                                this.$window.error("This is the first occurence!")
                                return;
                            }
                            $el = $el.last();
                            break;
                        default:
                            this.$window.error("Unknown direction")
                            return;
                    }

                    this.scrollTop($el)
                    this.changeSymbol($el)
                },
                bindOnClick: function ($el, callback, param) {
                    var that = this
                    $el.click(function (e) {
                        e.preventDefault()
                        callback.call(that, param || that.symbol)
                        return false;
                    })
                },
            }, context || {}));
        }
    }
    $.intelliWindow = new ($.extend(intelliWindow, $.intelliWindow ? $.intelliWindow : {}));
})(window, document, jQuery, jQuery.window);

/**
 * Messages window plugin.
 *
 * @author Kryštof Tulinger
 */
(function (browserWindow, document, $, $window) {
    if (!$window || typeof $window.create !== 'function') {
        console.log("The messagesWindow plugin requires $.window plugin")
        return;
    }
    
    var messagesWindow = function () {
        this.init = function (options, context) {
            return $.messagesWindow = $window.create(options = $.extend({
                title: 'Messages Window',
                draggable: false,
                init: function ($window) {
                    return $window
                            .attr('id', 'messages_win')
                            .addClass('messages-window')
                            .addClass('diff_navigation_style')
                            .css({top: '150px', right: '20px'})
                            .append(this.$messages = $("<div>"))
                },
                load: function ($window) {
                    $window.mouseenter(function () {
                        $window.show()
                    }).mouseleave(function () {
                        $window.hide()
                    })

                    // simulate show/toggle and move
                    $.each(['show', 'toggle'], function () {
                        var old = $window[this]
                        $window[this] = function () {
                            return old.call($window).move();
                        }
                    })
                },
                update: function (data) {
                    this.$messages.empty()
                    for (var i = 0; i < data.length; i++) {
                        var tag = data[i]
                        if (!tag || tag.messages.length === 0) {
                            continue;
                        }
                        this.$messages.append($("<h5>")
                                .addClass('message-group-caption')
                                .text(tag.tag.charAt(0).toUpperCase() + tag.tag.slice(1)))
                        var $ul = $("<ul>").addClass('message-group limited');
                        for (var j = 0; j < tag.messages.length; j++) {
                            if (!tag.messages[j]) {
                                continue;
                            }
                            $ul.append(
                                    $('<li>')
                                    .addClass('message-group-item')
                                    .addClass(tag.messages[j].class)
                                    .attr('title', 'Expires on ' + tag.messages[j].expiration)
                                    .html(tag.messages[j].created + ': ' + tag.messages[j].text)
                                    )
                        }
                        this.$messages.append($ul);
                    }
                }
            }, options || {}), $.extend({
                $messages: $(),
            }, context || {}));
        }
    };
    $.messagesWindow = new ($.extend(messagesWindow, $.messagesWindow ? $.messagesWindow : {}));
})(window, document, jQuery, jQuery.window);

/**
 * Scopes window plugin.
 *
 * @author Kryštof Tulinger
 */
(function (browserWindow, document, $, $window) {
    if (!$window || typeof $window.create !== 'function') {
        console.log("The scopesWindow plugin requires $.window plugin")
        return;
    }

    var scopesWindow = function () {
        this.init = function (options, context) {
            return $.scopesWindow = $window.create($.extend({
                title: 'Scopes Window',
                draggable: false,
                init: function ($window) {
                    return $window
                            .attr('id', 'scopes_win')
                            .addClass('scopes-window')
                            .addClass('diff_navigation_style')
                            .css({top: '150px', right: '20px'})
                            .append(this.$scopes = $("<div>"))
                },
                load: function ($window) {
                    $window.hide().css('top', parseFloat($("#content").css('top').replace('px', '')) + 10 + 'px')

                    // override the hide and show to throw an event and run
                    // scope_on_scroll() for update
                    $.each(['hide', 'show'], function () {
                        var event = this
                        var old = $window[event];
                        $window[event] = function () {
                            var $toReturn = old.call($window).trigger(event);
                            if (!scope_on_scroll || typeof scope_on_scroll !== 'function') {
                                console.debug("[scopesWindow]: The scope_on_scroll() is not a function at this point.");
                                return $toReturn;
                            }
                            scope_on_scroll();
                            return $toReturn;
                        }
                    });
                    
                    $('.scopes-toggle').click(function () {
                        $window.toggle();
                        return false;
                    })
                },
                update: function (data) {
                    if(!this.$window.is(':visible') && !this.$window.data('shown-once')) {
                        this.$window.show().data('shown-once', true);
                    }
                    this.$scopes.empty()
                    this.$scopes.html(this.buildLink(data.id, data.link))
                    this.$window.trigger('update')
                }
            }, options || {}), $.extend({
                $scopes: $(),
                buildLink: function (href, name) {
                    return $('<a>').attr('href', '#' + href).attr('title', name).html(name)
                }
            }, context || {}));
        }
    }
    $.scopesWindow = new ($.extend(scopesWindow, $.scopesWindow ? $.scopesWindow : {}));
})(window, document, jQuery, jQuery.window);

/**
 * Navigate window plugin.
 *
 * @author Kryštof Tulinger
 */
(function (browserWindow, document, $, $window) {
    if (!$window || typeof $window.create !== 'function') {
        console.log("The navigateWindow plugin requires $.window plugin")
        return;
    }

    var navigateWindow = function () {
        this.init = function (options, context) {
            return $.navigateWindow = $window.create($.extend({
                title: 'Navigate Window',
                draggable: false,
                init: function ($window) {
                    return $window
                            .attr('id', 'navigate_win')
                            .addClass('navigate-window')
                            .addClass('diff_navigation_style')
                            .css({top: '150px', right: '20px'})
                            .append(this.$content)
                },
                load: function ($window) {
                    var that = this
                    $window.css('top', this.getTopOffset() + 10 + 'px')
                    if ($.scopesWindow && $.scopesWindow.initialized) {
                        $.scopesWindow.on('show', function () {
                            setTimeout(function () {
                                that.updatePosition($window)
                            }, 100);
                        }).on('hide', function () {
                            that.updatePosition($window);
                        }).on('update', function () {
                            that.updatePosition($window);
                        })

                        if ($.scopesWindow.is(':visible')) {
                            setTimeout(function () {
                                that.updatePosition($window)
                            }, 100);
                        }
                    }

                    // override and show to throw an event and update position
                    $.each(['show'], function () {
                        var event = this
                        var old = $window[event];
                        $window[event] = function () {
                            return that.updatePosition(old.call($window).trigger(event))
                        }
                    });

                    $(browserWindow).resize(function () {
                        that.updatePosition($window)
                    })
                    that.updatePosition($window)
                },
                update: function (data) {
                    var $ul;
                    this.$content.empty()
                    for (var i = 0; i < data.length; i++)
                    {
                        this.$content.append($('<h4>').text(data[i][0]))
                        if (data[i][2].length === 0)
                            continue;
                        this.$content.append($ul = $('<ul>'))
                        for (var j = 0; j < data[i][2].length; j ++)
                            $ul.append($('<li>').append(this.buildLink(data[i][2][j][1], data[i][2][j][0], data[i][1])));
                    }

                }
            }, options || {
            }), $.extend({
                $content: $('<div>'),
                buildLink: function (href, name, c) {
                    return $('<a>').attr('href', '#' + href).attr('title', this.escapeHtml(name)).addClass(c).html(this.escapeHtml(name)).click(lnshow)
                },
                getTopOffset: function () {
                    return parseFloat($("#content").css('top'))
                },
                updatePosition: function ($w) {
                    if (!$w.is(':visible')) {
                        /**
                         * If the window is not visible then this
                         * function is expensive as
                         * <a href="http://api.jquery.com/outerheight/">documented</a>
                         * under additional notes.
                         */
                        return $w;
                    }

                    var a = {}
                    a.top = this.getTopOffset() + 10;
                    if ($.scopesWindow &&
                            $.scopesWindow.initialized &&
                            $.scopesWindow.is(':visible')) {
                        a.top = $.scopesWindow.offset().top + $.scopesWindow.outerHeight() + 20;
                    }
                    a.height = Math.min(parseFloat($w.css('max-height')) || 480, $(browserWindow).outerHeight() - a.top - ($w.outerHeight(true) - $w.height()) - 20);

                    if (a.height == $w.height() && a.top == this.getTopOffset())
                        return $w;

                    if (this.$content.children().length === 0)
                        // the window is empty
                        delete a.height

                    return $w.stop().animate(a)
                },
                escapeHtml: function (html) {
                    return html.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                }
            }, context || {}));
        }
    }
    $.navigateWindow = new ($.extend(navigateWindow, $.navigateWindow ? $.navigateWindow : {}));
})(window, document, jQuery, jQuery.window);

function init_scopes() {
    $.scopesWindow.init();
    $("#content").scroll(scope_on_scroll);
}

function init_results_autohide() {
    $("#sbox input[type='submit']").click(function (e) {
        $("#footer").not(".main_page").hide(); // footer
        $("#results > .message-group").hide(); // messages
        $("#results > p.suggestions").hide(); // suggestions
        $("#results > p.pagetitle").hide(); // description
        $("#results > p.slider").hide(); // pagination
        $("#results > h3").hide(); // error
        $("#results > table, #results > ul").hide(); // results + empty
        $("#results > table + p, #results > ul + p").hide(); // results + empty timing
    })
}

function init_searchable_option_list() {
    var searchableOptionListOptions = {
        maxHeight: '300px',
        showSelectionBelowList: false,
        showSelectAll: false,
        maxShow: 30,
        resultsContainer: $("#ltbl"),
        texts: {
            searchplaceholder: 'Click here to select project(s)'
        },
        events: {
            onInitialized: function () {
                if ($.messagesWindow.initialized) {
                    this.$selectionContainer.find("[data-messages]").mouseenter(function () {
                        var data = $(this).data('messages') || []
                        $.messagesWindow.update(data)
                        $.messagesWindow.show()
                    }).mouseleave(function (e) {
                        $.messagesWindow.hide()
                    })
                }
            },
            // override the default onScroll positioning event if necessary
            onScroll: function () {

                var posY = this.$input.offset().top - this.config.scrollTarget.scrollTop() + this.$input.outerHeight() + 1,
                        selectionContainerWidth = this.$innerContainer.outerWidth(false) - parseInt(this.$selectionContainer.css('border-left-width'), 10) - parseInt(this.$selectionContainer.css('border-right-width'), 10);

                if (this.$innerContainer.css('display') !== 'block') {
                    // container has a certain width
                    // make selection container a bit wider
                    selectionContainerWidth = Math.ceil(selectionContainerWidth * 1.2);
                } else {
                    // no border radius on top
                    this.$selectionContainer
                            .css('border-top-right-radius', 'initial');

                    if (this.$actionButtons) {
                        this.$actionButtons
                                .css('border-top-right-radius', 'initial');
                    }
                }

                this.$selectionContainer
                        .css('top', Math.floor(posY))
                        .css('left', Math.floor(this.$container.offset().left))
                        .css('width', selectionContainerWidth);
            },
            onRendered: function () {
                /**
                 * Has to be here because otherwise the offset()
                 * takes the original long &lt;select&gt; box and the max-height
                 * does not work then.
                 */
                $('#type').searchableOptionList({
                    texts: {
                        searchplaceholder: 'Click here to restrict the file type'
                    },
                    maxHeight: $('#type').offset().top + 'px',
                    /**
                     * Defined in menu.jsp just next to the original &lt;select&gt;
                     */
                    resultsContainer: $("#type-select-container"),
                });
            }
        }
    };

    $('#project').searchableOptionList(searchableOptionListOptions);
}

function init_history_input() {
    $('input[data-revision-path]').click(function () {
        var $this = $(this)
        $("a.more").each(function () {
            $(this).attr('href', setParameter($(this).attr('href'), 'r1', $this.data('revision-1')))
            $(this).attr('href', setParameter($(this).attr('href'), 'r2', $this.data('revision-2')))
        })

        var $revisions = $('input[data-revision-path]'),
                index = -1;

        // change the correct revision on every element
        // (every element keeps a track which revision is selected)
        $revisions.filter('[data-diff-revision=\'r1\']')
                .data('revision-2', $this.data('revision-2'))
        $revisions.filter('[data-diff-revision=\'r2\']')
                .data('revision-1', $this.data('revision-1'))

        // set the correct revision for the form submission
        $("#input_" + $this.data('diff-revision')).val($this.data('revision-path'))

        // enable all input
        $revisions.prop('disabled', false),
                // uncheck all input in my column
                $revisions.filter('[data-diff-revision=\'' + $this.data('diff-revision') + '\']').prop('checked', false)
        // set me as checked
        $this.prop('checked', true)

        // disable from top to r2
        index = Math.max($revisions.index($('input[data-revision-path][data-diff-revision=\'r2\']:checked')), 0)
        $revisions.slice(0, index).filter('[data-diff-revision=\'r1\']').prop('disabled', true)

        // disable from bottom to r1
        index = Math.max($revisions.index($('input[data-revision-path][data-diff-revision=\'r1\']:checked')), index)
        $revisions.slice(index + 1).filter('[data-diff-revision=\'r2\']').prop('disabled', true)
    })
}

function init_tablesorter() {
    $("#dirlist").tablesorter({
        sortList: [[0, 0]],
        cancelSelection: true,
        headers: {
            1: {
                sorter: 'text'
            },
            3: {
                sorter: 'dates'
            },
            4: {
                sorter: 'groksizes'
            }
        }
    });
}

function init_markdown_converter() {
    var converter = null;
    $('[data-markdown]').each(function () {
        var $that = $(this);
        $.script.loadScript('js/xss-0.2.16.min.js').done(function () {
            $.script.loadScript('js/showdown-1.4.2.min.js').done(function () {
                $that.find('.markdown-content[data-markdown-download]').each(function () {
                    var $that = $(this)
                    if (converter === null) {
                        converter = new showdown.Converter();
                    }

                    $.ajax({
                        url: $(this).data('markdown-download'),
                        dataType: 'text',
                        timeout: 5000,
                        mimeType: 'text/plain',
                    }).done(function (payload) {
                        $that.html(filterXSS(converter.makeHtml(payload)))
                                .show()
                        $that.addClass('markdown')
                                .find('[data-markdown-original]')
                                .hide()
                    })
                });
            });
        });
    });
}

window.onload = function () {
    for (var i in document.pageReady) {
        document.pageReady[i]();
    }
};

$(document).ready(function () {
    for (var i in this.domReady) {
        document.domReady[i]();
    }

    /**
     * Initialize scope scroll event to display scope information correctly when
     * the element comes into the viewport.
     */
    $('#src').each(function () {
        init_scopes();
    })

    /**
     * Initialize table sorter on every directory listing.
     */
    init_tablesorter()

    /**
     * Initialize intelligence window plugin. Presence of #contextpath indicates
     * that we use the code view.
     */
    $("#contextpath").each(function () {
        $.intelliWindow.init();
        return false;
    })

    /**
     * Initialize the messages plugin to display
     * message onhover on every affected element.
     */
    $.messagesWindow.init();

    /**
     * Attaches a onhover listener to display messages for affected elements.
     */
    if ($.messagesWindow.initialized) {
        $("[data-messages]").mouseenter(function () {
            var data = $(this).data('messages') || []
            $.messagesWindow.update(data)
            $.messagesWindow.show()
        }).mouseleave(function (e) {
            $.messagesWindow.hide()
        })
    }

    /**
     * Initialize spaces plugin which automatically inserts a single space between
     * the line number and the following text. It strongly relies on the fact
     * that the line numbers are stored in 'name' attribute on each line link.
     */
    $.spaces.init()

    /**
     * Initialize the window hash management. Mainly this allows users to select
     * multiple lines of code and use that url to send it to somebody else.
     */
    $.hash.init({parent: "pre"})

    /**
     * After hitting the search button, the results or projects are temporarily hidden
     * until the new page is loaded. That helps to distinguish if search is being in process.
     *
     */
    init_results_autohide()

    /**
     * Initialize the new project picker
     */
    init_searchable_option_list()

    /**
     * Initialize the history input picker.
     * Checkboxes are automatically covered with a click event and automatically
     * colored as disabled or checked.
     *
     * Also works for paging where it stores the actual selected revision range in the
     * pagination links.
     */
    init_history_input()

    /**
     * Initialize the markdown converter.
     *
     * WARNING: The converter is not XSS safe. If you're not sure about what
     * could occur in the readmes then rather comment out this.
     */
    init_markdown_converter();

    /**
     * Display last modified date in search results on hover over the filename
     */
    $('a.result-annotate').tooltip()
});

/**
 * Resize the element with the ID 'content' so that it fills the whole browser
 * window (i.e. the space between the header and the bottom of the window) and
 * thus get rid off the scrollbar in the page header.
 */
function resizeContent() {
    if (document.adjustContent != 0) {
        $('#content').css('top', $('body').outerHeight(true)).css('bottom', 0);
    }
}

/**
 * Get a parameter value from the URL.
 *
 * @param p the name of the parameter
 * @return the decoded value of parameter p
 */
function getParameter(p) {
    // First split up the parameter list. That is, transform from
    //       ?a=b&c=d
    // to
    //       [ ["a", "b"], ["c","d"] ]
    if (getParameter.params === undefined) {
        getParameter.params = window.location.search.substr(1).split("&").map(
                function (x) { return x.split("="); });
    }
    var params = getParameter.params;
    // Then look for the parameter.
    for (var i in params) {
        if (params[i][0] === p && params[i].length > 1) {
            return decodeURIComponent(params[i][1]);
        }
    }
    return undefined;
}

/**
 * Set parameter in the given url.
 * @param string url
 * @param string p parameter name
 * @param string v parameter value
 * @returns string the modified url
 */
function setParameter(url, p, v) {
    var base = url.substr(0, url.indexOf('?'))
    var params = url.substr(base.length + 1).split("&").map(
            function (x) {
                return x.split("=");
            });
    var found = false;
    for (var i in params) {
        if (params[i][0] === p && params[i].length > 1) {
            params[i][1] = encodeURIComponent(v);
            found = true
        }
    }
    if (!found) {
        params.push([p, encodeURIComponent(v)])
    }

    return base + '?' + params.map(function (x) {
        return x[0] + '=' + x[1];
    }).join('&');
}

function domReadyMast() {
    if (!window.location.hash) {
        var h = getParameter("h");
        if (h && h !== "") {
            window.location.hash = h;
        } else {
            $("#content")
                    .attr("tabindex", 1)
                    .focus()
                    .css('outline', 'none')
        }
    }
    if (document.annotate) {
        $('a.r').tooltip({
            content: function () {
                var element = $(this);
                var title = element.attr("title") || ""
                var parts = title.split(/<br\/>(?=[a-zA-Z0-9]+:)/g);
                if (parts.length <= 0)
                    return "";
                var $el = $("<dl>");
                for (var i = 0; i < parts.length; i++) {
                    var definitions = parts[i].split(":");
                    if (definitions.length < 2)
                        continue;
                    $("<dt>").text(definitions.shift().trim()).appendTo($el);
                    var $dd = $("<dd>");
                    $.each(definitions.join(":").split("<br/>"), function (i, el) {
                        $dd.append(el.trim());
                        $dd.append($("<br/>"));
                    });
                    $dd.appendTo($el);
                }
                return $el;
            },
        })

        $("#toggle-annotate-by-javascript").css('display', 'inline');
        $("#toggle-annotate").hide()
    }

    // When we move to a version of XHTML that supports the onscroll
    // attribute in the div element, we should add an onscroll attribute
    // in the generated XHTML in mast.jsp. For now, set it with jQuery.
    $("#content").scroll(scope_on_scroll);
}

function pageReadyMast() {
    document.adjustContent = 0;
    if ($('#whole_header').length > 0 && $('#content').length > 0) {
        document.adjustContent = 1;
        resizeContent();
    }
    $(window).resize(function () {
        resizeContent();
    });
}

function domReadyMenu() {
    var sbox = document.getElementById('sbox');
/*
    $("#project").autocomplete(projects, {
        minChars: 0,
        multiple: true,
        multipleSeparator: ",",
        //mustMatch: true,
        matchContains: "word",
        max: 200,
        cacheLength:20,
        //autoFill: false,
        formatItem: function(row, i, max) {
                return (row != null) ? i + "/" + max + ": " + row[0] : "";
            },
        formatMatch: function(row, i, max) {
                return (row != null) ? row[0] : "";
            },
        formatResult: function(row) {
                return (row != null) ? row[0] : "";
            },
        width: "300px"
    });
*/
    // TODO  Bug 11749
    // var p = document.getElementById('project');
    // p.setAttribute("autocomplete", "off");
}

function domReadyHistory() {
    // start state should ALWAYS be: first row: r1 hidden, r2 checked ;
    // second row: r1 clicked, (r2 hidden)(optionally)
    // I cannot say what will happen if they are not like that, togglediffs
    // will go mad !
    togglerevs();
    toggleProjectInfo();
}

function get_annotations() {
    var link = window.location.pathname + "?a=true";
    if (document.rev && document.rev()) {
        link += "&r=" + encodeURIComponent(document.rev());
    }
    if (window.location.hash) {
        // If a line is highlighted when "annotate" is clicked, we want to
        // preserve the highlighting, but we don't want the page to scroll
        // to the highlighted line. So put the line number in a URL parameter
        // instead of in the hash.
        link += "&h=";
        link += window.location.hash.substring(1, window.location.hash.length);
    }
    window.location = link;
}

function toggle_annotations() {
    $(document.body).toggleClass("blame-hidden");
}

/** list.jsp */

/**
 * Initialize defaults for list.jsp
 */
function pageReadyList() {
    document.highlight_count = 0;
    $.navigateWindow.init()
    if (typeof get_sym_list === 'function') {
        $.navigateWindow.update(get_sym_list())
    }
    $('#navigate').click(function () {
        $.navigateWindow.toggle()
        return false;
    })
}

/**
 * Toggle the display of line numbers.
 */
function lntoggle() {
    $(document.body).toggleClass("lines-hidden");
    $('.fold-space, .fold-icon, .unfold-icon').toggle()
}

function lnshow() {
    $(document.body).removeClass("lines-hidden");
    $('.fold-space, .fold-icon, .unfold-icon').show()
}

/* ------ Highlighting ------ */
/**
 * An expensive Highlighter:
 * Note: It will replace link's href contents as well, be careful.
 */
/* Not used.
function HighlightKeywordsFullText(keywords) {
    var el = $("body");
    $(keywords).each(
        function() {
            var pattern = new RegExp("("+this+")", ["gi"]);
            var rs = "<span style='background-color:#FFFF00;font-weight:bold;'"
                + ">$1</span>";
            el.html(el.html().replace(pattern, rs));
        }
    );
    // HighlightKeywordsFullText(["nfstcpsock"]);
}
*/

/**
 *  Highlight keywords by changing the style of matching tags.
 */
function HighlightKeyword(keyword) {
    var high_colors = [ "#ffff66", "#ffcccc", "#ccccff", "#99ff99", "#cc66ff" ];
    var pattern = "a:contains('" + keyword + "')";
    $(pattern).css({
        'text-decoration' : 'underline',
        'background-color' : high_colors[document.highlight_count
            % high_colors.length],
        'font-weight' : 'bold'
    });
    document.highlight_count++;
}
//Test: HighlightKeyword('timeval');

/**
 * Highlight the text given as value of the element with the ID "input_highlight" .
 * @see HighlightKeyword
 */
function add_highlight() {
    var tbox = document.getElementById('input_highlight');
    HighlightKeyword(tbox.value);
}

function toggle_filelist() {
    var $a = $('div.filelist')
    var $b = $('div.filelist-hidden')
    $a.toggle().toggleClass('filelist').toggleClass('filelist-hidden')
    $b.toggle().toggleClass('filelist').toggleClass('filelist-hidden')
}

function toggle_revtags() {
    var $a = $('tr.revtags, span.revtags')
    var $b = $('tr.revtags-hidden, span.revtags-hidden')
    $a.toggle().toggleClass('revtags').toggleClass('revtags-hidden')
    $b.toggle().toggleClass('revtags').toggleClass('revtags-hidden')
}

/**
 *  Function to toggle message length presentation
 */
function toggleCommon(closestType) {
  $(".rev-toggle-a").click(function() {
    var toggleState = $(this).closest(closestType).attr("data-toggle-state");
    var thisCell = $(this).closest("td");

    if (toggleState === "less") {
      $(this).closest(closestType).attr("data-toggle-state", "more");
      thisCell.find(".rev-message-summary").addClass("rev-message-hidden");
      thisCell.find(".rev-message-full").removeClass("rev-message-hidden");
      $(this).html("... show less");
    }
    else if (toggleState === "more") {
      $(this).closest(closestType).attr("data-toggle-state", "less");
      thisCell.find(".rev-message-full").addClass("rev-message-hidden");
      thisCell.find(".rev-message-summary").removeClass("rev-message-hidden");
      $(this).html("show more ...");
    }

    return false;
  });
}

/**
 *  Function to toggle revision message length for long revision messages
 */
function togglerevs() {
  $(".rev-toggle-a").click(toggleCommon("p"));
}
/**
 *  Function to toggle project info message length
 */
function toggleProjectInfo() {
  $(".rev-toggle-a").click(toggleCommon("span"));
}

function selectAllProjects() {
    if ($("#project").data(SearchableOptionList.prototype.DATA_KEY)) {
        $("#project").searchableOptionList().selectAll();
    } else {
        $("#project option").prop('selected', true)
    }
}

function invertAllProjects() {
    if ($("#project").data(SearchableOptionList.prototype.DATA_KEY)) {
        $("#project").searchableOptionList().invert();
    } else {
        $("#project option").each(function () {
            $(this).prop('selected', !$(this).prop('selected'));
        })
    }
}

function deselectAllProjects() {
    if ($("#project").data(SearchableOptionList.prototype.DATA_KEY)) {
        $("#project").searchableOptionList().deselectAll();
    } else {
        $("#project option").prop('selected', false)
    }
}

function clearSearchFrom() {
    $("#sbox input[type='text']").each(function () {
        $(this).val("");
    });
    $("#type :selected").prop("selected", false);
}

/**
 * Fold or unfold a function definition.
 */
function fold(id) {
    $('#' + id + '_fold_icon')
            .children()
            .first()
            .toggleClass('unfold-icon')
            .toggleClass('fold-icon')
    $('#' + id + '_fold').toggle('fold');
}

var scope_timeout = null;
/**
 * Function that is called when the #content div element is scrolled. Checks
 * if the top of the page is inside a function scope. If so, update the
 * scope element to show the name of the function and a link to its definition.
 */
function scope_on_scroll() {
    if($.scopesWindow && $.scopesWindow.initialized && !$.scopesWindow.is(':visible')) {
        return;
    }
    if (scope_timeout !== null) {
        clearTimeout(scope_timeout)
        scope_timeout = null
    }
    scope_timeout = setTimeout(function () {
        var cnt = document.getElementById("content");
        var y = cnt.getBoundingClientRect().top + 2;
        var c = document.elementFromPoint(15, y + 1);

        if ($(c).is('.l, .hl')) {
            var $par = $(c).closest('.scope-body, .scope-head')

            if (!$par.length) {
                return;
            }

            var $head = $par.hasClass('scope-body') ? $par.prev() : $par;
            var $sig = $head.children().first()
            if ($.scopesWindow.initialized) {
                $.scopesWindow.update({
                    'id': $head.attr('id'),
                    'link': $sig.html(),
                })
            }
        }
        scope_timeout = null;
    }, 150);
}
