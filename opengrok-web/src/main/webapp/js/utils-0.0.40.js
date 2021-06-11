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
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright 2011 Jens Elkner.
 * Portions Copyright (c) 2017, 2020, Chris Fraire <cfraire@me.com>.
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
    const spaces = function () {
        const inner = {
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
                let lo = 0;
                let hi = array.length - 1;
                while (lo <= hi) {
                    const mid = ((lo + hi) >> 1);
                    const cmp = compare(array[mid], key);
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

                const myOffset = inner.$collection.first().offset() ? inner.$collection.first().offset().top : 0;
                const myHeight = inner.$collection.first().height() || 0;
                const parentOffset = inner.options.$parent.offset() ? inner.options.$parent.offset().top : 0;
                const parentHeight = inner.options.$parent.height() || 0;

                const expectations = {
                    // the first element in viewport
                    start: Math.floor(Math.abs(Math.min(myOffset - parentOffset, 0)) / myHeight),
                    // the last element in viewport
                    end: Math.ceil((Math.abs(Math.min(myOffset - parentOffset, 0)) + parentHeight) / myHeight)
                };

                const indices = {
                    start: 0,
                    end: inner.$collection.length
                };

                const cmp = function (a, key) {
                    return $(a).attr("name") - key; // comparing the "name" attribute with the desired value
                };


                indices.start = inner.binarySearch(inner.$collection, expectations.start, cmp);
                indices.end = inner.binarySearch(inner.$collection, expectations.end, cmp);

                /** cutoffs */
                indices.start = Math.max(0, indices.start);
                indices.start = Math.min(inner.$collection.length - 1, indices.start);

                if (indices.end === -1) {
                    indices.end = inner.$collection.length - 1;
                }
                indices.end = Math.min(inner.$collection.length - 1, indices.end);

                /** calling callback for every element in the viewport */
                for (var i = indices.start; i <= indices.end; i++) {
                    inner.options.callback.apply(inner.$collection[i]);
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

                const scrollHandler = function (e) {
                    if (inner.lock) {
                        return;
                    }
                    inner.lock = true;
                    setTimeout(inner.handleScrollEvent, inner.options.interval);
                };
                // fire the event if user has not scrolled
                inner.options.$parent.scroll(scrollHandler).resize(scrollHandler).scroll();
                inner.initialized = true;
            }
        };

        this.init = function (options) {
            inner.options = $.extend({}, inner.defaults, {$parent: $("#content")}, options);
            inner.init();
            return this;
        };
    };

    $.spaces = new ($.extend(spaces, $.spaces ? $.spaces : {}))();
})(window, window.jQuery);

/**
 * Offseting the target anchors by the height of the fixed header.
 * Code taken from http://jsfiddle.net/ianclark001/rkocah23/.
 *
 * If this is not used, clicking on a anchor
 * with a hash target (href="#some-id") will
 * lead to incorrect positioning at the top of the page.
 */
(function (document, history, location) {
    const HISTORY_SUPPORT = !!(history && history.pushState);

    const anchorScrolls = {
        ANCHOR_REGEX: /^#[^ ]+$/,
        OFFSET_HEIGHT_PX: 90,

        /**
         * Establish events, and fix initial scroll position if a hash is provided.
         */
        init: function () {
            this.scrollToCurrent();
            $(window).on('hashchange', $.proxy(this, 'scrollToCurrent'));
            $('body').on('click', 'a', $.proxy(this, 'delegateAnchors'));
        },

        /**
         * Return the offset amount to deduct from the normal scroll position.
         * Modify as appropriate to allow for dynamic calculations
         */
        getFixedOffset: function () {
            return this.OFFSET_HEIGHT_PX;
        },

        /**
         * If the provided href is an anchor which resolves to an element on the
         * page, scroll to it.
         * @param  {String} href
         * @return {Boolean} - Was the href an anchor.
         */
        scrollIfAnchor: function (href, pushToHistory) {
            if (!this.ANCHOR_REGEX.test(href)) {
                return false;
            }

            let match = document.getElementById(href.slice(1));
            if (!match) {
                /**
                 * Match the elements with name="href", take the first match
                 */
                match = document.getElementsByName(href.slice(1));
                match = match.length > 0 ? match[0] : null;
            }

            if (match) {
                const anchorOffset = $(match.nextElementSibling).offset().top - this.getFixedOffset();
                $('html, body').animate({scrollTop: anchorOffset});

                location.hash = href;

                // Add the state to history as-per normal anchor links
                if (HISTORY_SUPPORT && pushToHistory) {
                    history.pushState({}, document.title, location.pathname + location.search + href);
                }
            }

            return !!match;
        },

        /**
         * Attempt to scroll to the current location's hash.
         */
        scrollToCurrent: function (e) {
            if (this.scrollIfAnchor(window.location.hash) && e) {
                e.preventDefault();
            }
        },

        /**
         * If the click event's target was an anchor, fix the scroll position.
         */
        delegateAnchors: function (e) {
            const elem = e.target;

            if (this.scrollIfAnchor(elem.getAttribute('href'), true)) {
                e.preventDefault();
            }
        }
    };


    $(document).ready($.proxy(anchorScrolls, 'init'));
})(window.document, window.history, window.location);

(function(window, $) {
    const hash = function () {
        const inner = {
            self: this,
            initialized: false,
            highlighted: [],
            defaults: {
              highlightedClass: 'target',
              linkSelectorTemplate: '{parent} a[name={n}]',
              clickSelector: '{parent} a.l, {parent} a.hl',
              parent: 'div#src',
              autoScroll: true,
              autoScrollDuration: 500
            },
            options: {},
            bindClickHandler: function() {
                $(inner.format(inner.options.clickSelector, {parent: inner.options.parent})).click(function (e) {
                    if(e.shiftKey) {
                        // shift pressed
                        const val = inner.toInt($(this).attr("name"));
                        if (!val) {
                            return false;
                        }

                        const l = inner.getLinesParts(window.location.hash);

                        if (l.length == 2) {
                            window.location.hash = "#" + Math.min(l[0], val) + "-" + Math.max(val, l[1]);
                        } else if (l.length == 1) {
                            window.location.hash = "#" + Math.min(l[0], val) + "-" + Math.max(l[0], val);
                        }
                        return false;
                    }
                    return true;
                });
            },
            
            getHashParts: function (hash) {
                if (!hash || hash === "") {
                    return hash;
                }
                return (hash = hash.split("#")).length > 1 ? hash[1] : "";
            },

            getLinesParts: function ( hashPart ) {
              hashPart = inner.getHashParts(hashPart);
              if (!hashPart || hashPart === "") {
                  return hashPart;
              }
              const s = hashPart.split("-");
              if (s.length > 1 && inner.toInt(s[0]) && inner.toInt(s[1])) {
                  return [inner.toInt(s[0]), inner.toInt(s[1])];
              }
              if (s.length > 0 && inner.toInt(s[0])) {
                  return [inner.toInt(s[0])];
              }
              return [];
            },

            lines: function (urlPart) {
                const p = inner.getLinesParts(urlPart);
                if (p.length == 2) {
                    let l = [];
                    for (let i = Math.min(p[0],p[1]); i <= Math.max(p[0], p[1]); i++) {
                        l.push(i);
                    }
                    return l;
                } else if (p.length == 1){
                    return [p[0]];
                }
                return [];
            },

            reload: function(e){
                for (let i = 0; i < inner.highlighted.length; i++) {
                    // remove color
                    inner.highlighted[i].removeClass(inner.options.highlightedClass);
                }
                inner.highlighted = [];

                const lines = inner.lines(window.location.hash);

                if (lines.length < 1) {
                    // not a case of line highlighting
                    return;
                }
                for (let j = 0; j < lines.length; j++) {
                    // color
                    const slc = inner.format(inner.options.linkSelectorTemplate, { "parent": inner.options.parent,
                                                                                  "n": lines[j] } );
                    const el = $(slc).addClass(inner.options.highlightedClass);
                    inner.highlighted.push(el);
                }
            },
            format: function(format) {
                let args = Array.prototype.slice.call(arguments, 1);
                args = args.length > 0 ? typeof args[0] === "object" ? args[0] : args : args;
                return format.replace(/{([a-zA-Z0-9_-]+)}/g, function(match, number) {
                  return typeof args[number] != 'undefined' ? args[number] : match;
                });
            },
            toInt: function (string) {
                return parseInt(string, 10);
            },
            scroll: function (){
                if (!inner.options.autoScroll) {
                    return;
                }
                const lines = inner.getLinesParts(window.location.hash);
                if (lines.length > 0) {
                    const line = lines[0]; // first line
                    const $line = $(inner.format(inner.options.linkSelectorTemplate, {
                        parent: inner.options.parent,
                        n: line
                    }));
                    if ($line.length > 0) {
                        // if there is such element identified with the line number
                        // we can scroll to it
                        $('html, body').animate({
                            scrollTop: $(inner.format(inner.options.linkSelectorTemplate, {
                                parent: inner.options.parent,
                                n: line
                            })).offset().top - $(inner.options.parent).offset().top
                        }, inner.options.autoScrollDuration);
                    }
                }
            }
        }; // inner
        
        this.init = function (options) {
            if (inner.initialized) {
                return this;
            }
            inner.options = $.extend(inner.defaults, options, {});

            $(window).on("hashchange", inner.reload);
            inner.reload();
            inner.bindClickHandler();
            inner.scroll();
            inner.initialized = true;
            return this;
        };
    };
    $.hash = new ($.extend(hash, $.hash ? $.hash : {}))();
}) (window, window.jQuery);

/**
 * General on-demand script downloader
 */
(function (window, document, $) {
    const script = function () {
        this.scriptsDownloaded = {};
        this.defaults = {
            contextPath: window.contextPath
        };

        this.options = $.extend(this.defaults, {});

        this.loadScript = function (url) {
            if (!/^[a-z]{3,5}:\/\//.test(url)) { // dummy test for remote prefix
                url = this.options.contextPath + '/' + url;
            }
            if (url in this.scriptsDownloaded) {
                return this.scriptsDownloaded[url];
            }
            this.scriptsDownloaded[url] = $.ajax({
                url: url,
                dataType: 'script',
                cache: true,
                timeout: 10000
            }).fail(function () {
                console.debug('Failed to download "' + url + '" module');
            });
            return this.scriptsDownloaded[url];
        };
    };
    $.script = new ($.extend(script, $.script ? $.script : {}))();
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
 *      // you must return the modified window object
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
 * For custom content in the window you can use body() function:
 * $myWindow.body().append($('<div>').addClass('important-div'))
 *
 * @author Kryštof Tulinger
 */
(function (browserWindow, document, $, $script) {
    const window = function () {
        const Inner = function (options, context) {
            const self = this;
            // private
            this.context = context;
            this.callbacks = {
                init: [],
                load: [],
                update: []
            };
            this.$window = undefined;
            this.$errors = undefined;
            this.clientX = 0;
            this.clientY = 0;
            this.pendingUpdates = [];

            /**
             * Default values for the window options.
             */
            this.defaults = {
                title: 'Window',
                appendDraggable: '#content',
                draggable: true,
                draggableScript: 'js/jquery-ui-1.12.1-draggable.min.js', // relative to context
                contextPath: browserWindow.contextPath,
                parent: undefined,
                load: undefined,
                init: undefined,
                handlers: undefined
            };

            this.options = $.extend({}, this.defaults, options);

            this.addCallback = function (name, callback, context) {
                context = context || this.getSelfContext;
                if (!this.callbacks || !$.isArray(this.callbacks[name])) {
                    this.callbacks[name] = [];
                }
                this.callbacks[name].push({
                    'callback': callback,
                    'context': context
                });
            };

            this.fire = function (name, args) {
                if (!this.callbacks || !$.isArray(this.callbacks[name])) {
                    return;
                }

                for (let i = 0; i < this.callbacks[name].length; i++) {
                    this.$window = (this.callbacks[name][i].callback.apply(
                            this.callbacks[name][i].context.call(this),
                            args || [this.$window])) || this.$window;
                }
            };

            this.getContext = function () {
                return $.extend(this.context, {options: this.options});
            };

            this.getSelfContext = function () {
                return this;
            };

            // private
            this.cropPosition = function($w, position) {
                const w = {
                    height: $w.outerHeight(true),
                    width: $w.outerWidth(true)
                };
                const bw = {
                    height: $(browserWindow).outerHeight(true),
                    width: $(browserWindow).outerWidth(true),
                    yOffset: 0,
                    xOffset: 0
                };
                position.top -= Math.max(0, position.top + w.height - bw.yOffset - bw.height + 20);
                position.left -= Math.max(0, position.left + w.width - bw.xOffset - bw.width + 20);
                return position;
            };

            this.determinePosition = function () {
                const position = {
                    top: this.clientY,
                    left: this.clientX
                };
                return this.cropPosition(this.$window, position);
            };

            this.makeMeDraggable = function () {
                if (!$script || typeof $script.loadScript !== 'function') {
                    console.log("The window plugin requires $.script plugin when draggable option is 'true'");
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
            };

            this.addCallback('init', function ($window) {
                let $close;
                const $top = $("<div>").addClass('clearfix').
                        append($("<div>").addClass("pull-left").append($("<b>").text(this.options.title || "Window"))).
                        append($("<div>").addClass('pull-right').append($close = $('<a href="#" class="minimize">x</a>')));

                const $header = $("<div>").addClass('window-header').append($top);

                const $body = $("<div>").addClass("window-body").append(self.$errors = $('<div>').css('text-align', 'center'));

                $window = $("<div>").
                        addClass('window').
                        addClass('diff_navigation_style').
                        css('z-index', 15000).
                        hide().
                        append($header).
                        append($body);

                $close.click(function () {
                    $window.hide();
                    return false;
                });

                /**
                 * Get the element for the window body.
                 * This should be used to place your desired content.
                 *
                 * The returned object has a method {@code window()}
                 * which returns the whole window.
                 *
                 * {@code $window} is equivalent to {@code $window.body().window()}
                 *
                 * @returns jQuery object of the window body
                 */
                $window.body = function () {
                    $body.window = function () {
                        return $window;
                    };
                    return $body;
                };

                /**
                 * Display custom error message in the window
                 * @param {string} msg message
                 * @returns self
                 */
                $window.error = function (msg) {
                    const $span = $("<p class='error'>" + msg + "</p>").
                            animate({opacity: "0.2"}, 3000);
                    $span.hide('slow', function () {
                        $span.remove();
                    });
                    self.$errors.html($span);
                    return this;
                };

                /**
                 * Move the window to the position. If no position is given
                 * it may be determined from the mouse position.
                 *
                 * @param {object} position object with top and left attributes
                 * @returns self
                 */
                $window.move = function (position) {
                    position = position || self.determinePosition();
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
                    const action = this.is(':visible') ? this.hide : this.show;
                    return action.apply(this, arguments);
                };

                /**
                 * Toggle and move the window to the current mouse position
                 *
                 * @returns self
                 */
                $window.toggleAndMove = function () {
                    return $window.toggle().move();
                };

                /**
                 * Update the window with given data.
                 *
                 * @param {mixed} data
                 * @returns {undefined}
                 */
                $window.update = function (data) {
                    if (this.loaded) {
                        self.fire('update', [data]);
                    } else {
                        self.pendingUpdates.push({data: data});
                    }
                    return this;
                };

                // insert window into context
                this.context = $.extend(this.context, {$window: $window});

                // set us as initialized
                $window.initialized = true;
                // set us as not loaded
                $window.loaded = false;

                return $window;
            });

            this.addCallback('load', function ($window) {
                const that = this;
                $(document).mousemove(function (e) {
                    that.clientX = e.clientX;
                    that.clientY = e.clientY;
                });
                $(document).keyup(function (e) {
                    var key = e.keyCode;
                    if (key === 27) { // esc
                        that.$window.hide();
                    }
                    return true;
                });
            });

            if (this.options.draggable) {
                this.addCallback('load', this.makeMeDraggable);
            }

            this.addCallback('load', function ($window) {
                this.$window.appendTo(this.options.parent ? $(this.options.parent) : $("body"));
            });

            if (this.options.init && typeof this.options.init === 'function') {
                this.addCallback('init', this.options.init, this.getContext);
            }

            if (self.options.load && typeof self.options.load === 'function') {
                this.addCallback('load', this.options.load, this.getContext);
            }

            if (self.options.update && typeof self.options.update === 'function') {
                this.addCallback('update', this.options.update, this.getContext);
            }

            $(function () {
                self.fire('load');
                self.$window.loaded = true;

                for (var i = 0; i < self.pendingUpdates.length; i++) {
                    self.fire('update', [self.pendingUpdates[i].data]);
                }
                self.pendingUpdates = [];
            });

            this.fire('init');

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
            return new Inner(options, context);
        };
    };
    $.window = new ($.extend(window, $.window ? $.window : {}))();
})(window, document, jQuery, jQuery.script);

/**
 * Intelligence window plugin.
 * 
 * Reworked to use Jquery in 2016
 */
(function (browserWindow, document, $, $window) {
    if (!$window || typeof $window.create !== 'function') {
        console.log("The intelligenceWindow plugin requires $.window plugin");
        return;
    }

    var intelliWindow = function () {
        this.initialised = false;
        this.init = function (options, context) {
            $.intelliWindow = $window.create($.extend({
                title: 'Intelligence window',
                selector: 'a.intelliWindow-symbol',
                google_url: 'https://www.google.com/search?q=',
                project: undefined,
                init: function ($window) {
                    let $highlight, $unhighlight, $unhighlightAll, $prev, $next;

                    let $firstList = $("<ul>").
                            append($("<li>").append(
                                    $highlight = $('<a href="#" title="Highlight">' +
                                            '<span>Highlight</span> <b class="symbol-name"></b></a>'))).
                            append($("<li>").append(
                                    $unhighlight = $('<a href="#" title="Unhighlight">' +
                                            '<span>Unhighlight</span> <b class="symbol-name"></b></a>'))).
                            append($("<li>").append(
                                    $unhighlightAll = $('<a href="#" title="Unhighlight all">' +
                                            '<span>Unhighlight all</span></a>')));

                    this.bindOnClick($highlight, this.highlight);
                    this.bindOnClick($unhighlight, this.unhighlight);
                    this.bindOnClick($unhighlightAll, this.unhighlightAll);

                    let $secondList = $("<ul>").
                            append($("<li>").append(
                                    $('<a class="search-defs" href="#" target="_blank">' +
                                            '<span>Search for definitions of</span> <b class="symbol-name"></b></a>'))).
                            append($("<li>").append(
                                    $('<a class="search-refs" href="#" target="_blank">' +
                                            '<span>Search for references of</span> <b class="symbol-name"></b></a>'))).
                            append($("<li>").append(
                                    $('<a class="search-full" href="#" target="_blank">' +
                                            '<span>Do a full search with</span> <b class="symbol-name"></b></a>'))).
                            append($("<li>").append(
                                    $('<a class="search-files" href="#" target="_blank">' +
                                            '<span>Search for file names that contain</span> <b class="symbol-name"></b></a>')));

                    let $thirdList = $("<ul>").
                            append($("<li>").append(
                                    $('<a class="search-google" href="#" target="_blank">' +
                                            '<span>Google</span> <b class="symbol-name"></b></a>')));

                    let $controls = $('<div class="pull-right">').
                            append($next = $('<a href="#" title="next" class="pull-right">Next >></a>')).
                            append('<span class="pull-right"> | </span>').
                            append($prev = $('<a href="#" title="prev" class="pull-right"><< Prev </a>')).
                            append($('<div class="clearfix">')).
                            append(this.$errors = $('<span class="clearfix">'));

                    this.bindOnClick($next, this.scrollToNextElement, 1);
                    this.bindOnClick($prev, this.scrollToNextElement, -1);

                    return $window.
                            attr('id', 'intelli_win').
                            addClass('intelli-window').
                            body().
                            append($controls).
                            append($("<h2>").addClass('symbol-name')).
                            append($("<span>").addClass('symbol-description')).
                            append($("<hr>")).
                            append($("<h5>").text("In current file")).
                            append($firstList).
                            append($("<h5>").text('In project "' + this.project + '"')).
                            append($secondList).
                            append($("<h5>").text("On Google")).
                            append($thirdList).
                            window();
                },
                load: function ($window) {
                    const that = this;
                    $(document).keypress(function (e) {
                        if (textInputHasFocus()) {
                            return true;
                        }
                        const key = e.which;
                        switch (key) {
                            case 49: // 1
                                if (that.symbol) {
                                    that.$window.toggleAndMove();
                                }
                                break;
                            case 50: // 2
                                if (that.symbol && that.unhighlight(that.symbol).length === 0) {
                                     that.highlight(that.symbol, 1);
                                }
                                break;
                            case 51: // 3
                                if (that.symbol && that.unhighlight(that.symbol).length === 0) {
                                     that.highlight(that.symbol, 2);
                                }
                                break;
                            case 52: // 4
                                if (that.symbol && that.unhighlight(that.symbol).length === 0) {
                                     that.highlight(that.symbol, 3);
                                }
                                break;
                            case 53: // 5
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
                }
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
                    this.$current = $el;
                    this.$last_highlighted_current = $el.hasClass("symbol-highlighted") ? $el : this.$last_highlighted_current;
                    this.symbol = $el.text();
                    this.place = $el.data("definition-place");
                    this.$window.find('.hidden-on-start').show();
                    this.$window.find(".symbol-name").text(this.symbol);
                    this.$window.find(".symbol-description").text(this.getSymbolDescription(this.place));
                    this.modifyLinks();
                },
                modifyLinks: function () {
                    this.$search_defs = this.$search_defs || this.$window.find('.search-defs');
                    this.$search_refs = this.$search_refs || this.$window.find('.search-refs');
                    this.$search_full = this.$search_full || this.$window.find('.search-full');
                    this.$search_files = this.$search_files || this.$window.find('.search-files');
                    this.$search_google = this.$search_google || this.$window.find('.search-google');

                    this.$search_defs.attr('href', this.getSearchLink('defs'));
                    this.$search_refs.attr('href', this.getSearchLink('refs'));
                    this.$search_full.attr('href', this.getSearchLink('full'));
                    this.$search_files.attr('href', this.getSearchLink('path'));
                    this.$search_google.attr('href', this.options.google_url + this.symbol);
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
                highlight: function (symbol, color) {
                    if (this.$current.text() === symbol) {
                        this.$last_highlighted_current = this.$current;
                    }
                    return this.getSymbols().filter(function () {
                        return $(this).text() === symbol;
                    }).addClass('symbol-highlighted').addClass('hightlight-color-' + (color || 1));
                },
                unhighlight: function (symbol) {
                    if (this.$last_highlighted_current &&
                            this.$last_highlighted_current.text() === symbol &&
                            this.$last_highlighted_current.hasClass('symbol-highlighted')) {
                        const i = this.getSymbols().index(this.$last_highlighted_current);
                        this.$last_highlighted_jump = this.getSymbols().slice(0, i).filter('.symbol-highlighted').last();
                    }
                    return this.getSymbols().filter(".symbol-highlighted").filter(function () {
                        return $(this).text() === symbol;
                    }).removeClass('symbol-highlighted').
                            removeClass("hightlight-color-1 hightlight-color-2 hightlight-color-3");
                },
                unhighlightAll: function () {
                    this.$last_highlighted_current = undefined;
                    return this.getSymbols().filter(".symbol-highlighted").
                            removeClass("symbol-highlighted").
                            removeClass("hightlight-color-1 hightlight-color-2 hightlight-color-3");
                },
                scrollTop: function ($el) {
                    if (this.options.scrollTop) {
                        this.options.scrollTop($el);
                    } else {
                        $('html, body').stop().animate({
                            scrollTop: $el.offset().top - $("#src").offset().top
                        }, 500);
                    }
                },
                scrollToNextElement: function (direction) {
                    const UP = -1;
                    const DOWN = 1;
                    const $highlighted = this.getSymbols().filter(".symbol-highlighted");
                    let $el = $highlighted.length && this.$last_highlighted_current ? this.$last_highlighted_current : this.$current;
                    const indexOfCurrent = this.getSymbols().index($el);

                    switch (direction) {
                        case DOWN:
                            $el = this.getSymbols().slice(indexOfCurrent + 1);
                            if ($highlighted.length) {
                                $el = $el.filter('.symbol-highlighted');
                            }
                            if (!$el.length) {
                                this.$window.error("This is the last occurence!");
                                return;
                            }
                            $el = $el.first();
                            break;
                        case UP:
                            $el = this.getSymbols().slice(0, indexOfCurrent);
                            if ($highlighted.length) {
                                $el = $el.filter('.symbol-highlighted');
                            }
                            if (!$el.length) {
                                this.$window.error("This is the first occurence!");
                                return;
                            }
                            $el = $el.last();
                            break;
                        default:
                            this.$window.error("Unknown direction");
                            return;
                    }

                    this.scrollTop($el);
                    this.changeSymbol($el);
                },
                bindOnClick: function ($el, callback, param) {
                    const that = this;
                    $el.click(function (e) {
                        e.preventDefault();
                        callback.call(that, param || that.symbol);
                        return false;
                    });
                }
            }, context || {}));
            return $.intelliWindow;
        };
    };
    $.intelliWindow = new ($.extend(intelliWindow, $.intelliWindow ? $.intelliWindow : {}))();
})(window, document, jQuery, jQuery.window);

/**
 * Messages window plugin.
 *
 * @author Kryštof Tulinger
 */
(function (browserWindow, document, $, $window) {
    if (!$window || typeof $window.create !== 'function') {
        console.log("The messagesWindow plugin requires $.window plugin");
        return;
    }
    
    const messagesWindow = function () {
        this.init = function (options, context) {
            $.messagesWindow = $window.create($.extend({
                title: 'Messages Window',
                draggable: false,
                init: function ($window) {
                    return $window.
                            attr('id', 'messages_win').
                            addClass('messages-window').
                            addClass('diff_navigation_style').
                            css({top: '150px', right: '20px'}).
                            body().
                            append(this.$messages = $("<div>")).
                            window();
                },
                load: function ($window) {
                    $window.mouseenter(function () {
                        $window.show();
                    }).mouseleave(function () {
                        $window.hide();
                    });

                    // simulate show/toggle and move
                    $.each(['show', 'toggle'], function () {
                        const old = $window[this];
                        $window[this] = function () {
                            return old.call($window).move();
                        };
                    });
                },
                update: function (data) {
                    this.$messages.empty();
                    for (let tag of data) {
                        if (!tag || tag.messages.length === 0) {
                            continue;
                        }
                        this.$messages.append($("<h5>").
                                addClass('message-group-caption').
                                text(tag.tag.charAt(0).toUpperCase() + tag.tag.slice(1)));
                        const $ul = $("<ul>").addClass('message-group limited');
                        for (let j = 0; j < tag.messages.length; j++) {
                            if (!tag.messages[j]) {
                                continue;
                            }
                            $ul.append(
                                $('<li>').
                                    addClass('message-group-item').
                                    addClass(tag.messages[j].messageLevel).
                                    attr('title', 'Expires on ' + tag.messages[j].expiration).
                                    html(tag.messages[j].created + ': ' + tag.messages[j].text)
                            );
                        }
                        this.$messages.append($ul);
                    }
                }
            }, options || {}), $.extend({
                $messages: $()
            }, context || {}));
            return $.messagesWindow;
        };
    };
    $.messagesWindow = new ($.extend(messagesWindow, $.messagesWindow ? $.messagesWindow : {}))();
})(window, document, jQuery, jQuery.window);

/**
 * Scopes window plugin.
 *
 * @author Kryštof Tulinger
 */
(function (browserWindow, document, $, $window) {
    if (!$window || typeof $window.create !== 'function') {
        console.log("The scopesWindow plugin requires $.window plugin");
        return;
    }

    const scopesWindow = function () {
        this.init = function (options, context) {
            $.scopesWindow = $window.create($.extend({
                title: 'Scopes Window',
                draggable: false,
                init: function ($window) {
                    return $window.
                            attr('id', 'scopes_win').
                            addClass('scopes-window').
                            addClass('diff_navigation_style').
                            css({top: '150px', right: '20px'}).
                            body().
                            append(this.$scopes = $("<div>")).
                            window();
                },
                load: function ($window) {
                    $window.hide().css('top', $("#content").offset().top + 10 + 'px');

                    // override the hide and show to throw an event and run
                    // scope_on_scroll() for update
                    $.each(['hide', 'show'], function () {
                        const event = this;
                        const old = $window[event];
                        $window[event] = function () {
                            var $toReturn = old.call($window).trigger(event);
                            if (!scope_on_scroll || typeof scope_on_scroll !== 'function') {
                                console.debug("[scopesWindow]: The scope_on_scroll() is not a function at this point.");
                                return $toReturn;
                            }
                            scope_on_scroll();
                            return $toReturn;
                        };
                    });
                    
                    $('.scopes-toggle').click(function () {
                        $window.toggle();
                        return false;
                    });
                },
                update: function (data) {
                    if(!this.$window.is(':visible') && !this.$window.data('shown-once')) {
                        this.$window.show().data('shown-once', true);
                    }
                    this.$scopes.empty();
                    this.$scopes.html(this.buildLink(data.id, data.link));
                    this.$window.trigger('update');
                }
            }, options || {}), $.extend({
                $scopes: $(),
                buildLink: function (href, name) {
                    return $('<a>').attr('href', '#' + href).attr('title', name).html(name);
                }
            }, context || {}));
            return $.scopesWindow;
        };
    };
    $.scopesWindow = new ($.extend(scopesWindow, $.scopesWindow ? $.scopesWindow : {}))();
})(window, document, jQuery, jQuery.window);

/**
 * Navigate window plugin.
 *
 * @author Kryštof Tulinger
 */
(function (browserWindow, document, $, $window) {
    if (!$window || typeof $window.create !== 'function') {
        console.log("The navigateWindow plugin requires $.window plugin");
        return;
    }

    const navigateWindow = function () {
        this.init = function (options, context) {
            $.navigateWindow = $window.create($.extend({
                title: 'Navigate Window',
                draggable: false,
                init: function ($window) {
                    return $window.
                            attr('id', 'navigate_win').
                            addClass('navigate-window').
                            addClass('diff_navigation_style').
                            addClass('diff_navigation_style').
                            css({top: '150px', right: '20px', height: this.defaultHeight + 'px'}).
                            body().
                            append(this.$content).
                            window();
                },
                load: function ($window) {
                    const that = this;
                    $window.css('top', this.getTopOffset() + 10 + 'px');

                    if ($.scopesWindow && $.scopesWindow.initialized) {
                        $.scopesWindow.on('show', function () {
                            setTimeout(function () {
                                that.updatePosition($window);
                            }, 100);
                        }).on('hide', function () {
                            that.updatePosition($window);
                        }).on('update', function () {
                            that.updatePosition($window);
                        });

                        if ($.scopesWindow.is(':visible')) {
                            setTimeout(function () {
                                that.updatePosition($window);
                            }, 100);
                        }
                    }

                    if ($('[data-navigate-window-enabled="true"]').length) {
                        $window.show();
                    }

                    // override and show to throw an event and update position
                    $.each(['show'], function () {
                        const event = this;
                        const old = $window[event];
                        $window[event] = function () {
                            return that.updatePosition(old.call($window).trigger(event));
                        };
                    });

                    $(browserWindow).resize(function () {
                        that.updatePosition($window);
                    });
                    that.updatePosition($window);
                },
                update: function (data) {
                    let $ul;
                    this.$content.empty();
                    for (let i = 0; i < data.length; i++) {
                        this.$content.append($('<h4>').text(data[i][0]));
                        if (data[i][2].length === 0) {
                            continue;
                        }
                        this.$content.append($ul = $('<ul>'));
                        for (let j = 0; j < data[i][2].length; j++) {
                            $ul.append($('<li>').append(this.buildLink(data[i][2][j][1], data[i][2][j][0], data[i][1])));
                        }
                    }
                    this.updatePosition(this.$window);
                }
            }, options || {
            }), $.extend({
                $content: $('<div>'),
                defaultHeight: 480,
                buildLink: function (href, name, c) {
                    return $('<a>').attr('href', '#' + href).attr('title', this.escapeHtml(name)).addClass(c).html(this.escapeHtml(name)).click(lnshow);
                },
                getTopOffset: function () {
                    return $("#content").offset().top;
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

                    const a = {};
                    a.top = this.getTopOffset() + 10;
                    if ($.scopesWindow &&
                            $.scopesWindow.initialized &&
                            $.scopesWindow.is(':visible')) {
                        a.top = $.scopesWindow.position().top + $.scopesWindow.outerHeight() + 20;
                    }
                    a.height = Math.min(parseFloat($w.css('max-height')) || this.defaultHeight, $(browserWindow).outerHeight() - a.top - ($w.outerHeight(true) - $w.height()) - 20);

                    if (a.height == $w.height() && a.top == this.getTopOffset()) {
                        return $w;
                    }

                    if (this.$content.children().length === 0) {
                        // the window is empty
                        delete a.height;
                    }

                    return $w.stop().animate(a);
                },
                escapeHtml: function (html) {
                    return html.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
                }
            }, context || {}));
            return $.navigateWindow;
        };
    };
    $.navigateWindow = new ($.extend(navigateWindow, $.navigateWindow ? $.navigateWindow : {}))();
})(window, document, jQuery, jQuery.window);

function init_scopes() {
    $.scopesWindow.init();
    $(window).scroll(scope_on_scroll);
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
    });
}

function init_searchable_option_list() {
    function init_sol_on_type_combobox() {
        const $type = $('#type');
        if ($type.length === 0) {
            return;
        }
        /**
         * Has to be here because otherwise the offset()
         * takes the original long &lt;select&gt; box and the max-height
         * does not work then.
         */
        $type.searchableOptionList({
            texts: {
                searchplaceholder: 'Click here to restrict the file type'
            },
            maxHeight: $type.offset().top + 'px',
            /**
             * Defined in menu.jsp just next to the original &lt;select&gt;
             */
            resultsContainer: $("#type-select-container")
        });
    }
    const searchableOptionListOptions = {
        maxHeight: '300px',
        showSelectionBelowList: false,
        showSelectAll: false,
        maxShow: 30,
        resultsContainer: $("#ltbl"),
        numSelectedItem: $("#nn"),
        quickDeleteForm: $("#sbox"),
        quickDeletePermit: checkIsOnSearchPage,
        texts: {
            searchplaceholder: 'Click here to select project(s)'
        },
        events: {
            onInitialized: function () {
                if ($.messagesWindow.initialized) {
                    this.$selectionContainer.find("[data-messages]").mouseenter(function () {
                        var data = $(this).data('messages') || [];
                        $.messagesWindow.update(data);
                        $.messagesWindow.show();
                    }).mouseleave(function (e) {
                        $.messagesWindow.hide();
                    });
                }
            },
            // override the default onScroll positioning event if necessary
            onScroll: function () {

                const posY = this.$input.offset().top - this.config.scrollTarget.scrollTop() + this.$input.outerHeight() + 1;
                let selectionContainerWidth = this.$innerContainer.outerWidth(false) - parseInt(this.$selectionContainer.css('border-left-width'), 10) - parseInt(this.$selectionContainer.css('border-right-width'), 10);

                if (this.$innerContainer.css('display') !== 'block') {
                    // container has a certain width
                    // make selection container a bit wider
                    selectionContainerWidth = Math.ceil(selectionContainerWidth * 1.2);
                } else {
                    // no border radius on top
                    this.$selectionContainer.css('border-top-right-radius', 'initial');

                    if (this.$actionButtons) {
                        this.$actionButtons.css('border-top-right-radius', 'initial');
                    }
                }

                this.$selectionContainer.
                        css('top', Math.floor(posY)).
                        css('left', Math.floor(this.$container.offset().left)).
                        css('width', selectionContainerWidth);
            },
            onRendered: init_sol_on_type_combobox
        }
    };

    const $project = $('#project');
    if ($project.length === 1) {
        $project.searchableOptionList(searchableOptionListOptions);
    } else {
        init_sol_on_type_combobox();
    }
}

function init_history_input() {
    $('input[data-revision-path]').click(function () {
        const $this = $(this);
        $("a.more").each(function () {
            $(this).attr('href', setParameter($(this).attr('href'), 'r1', $this.data('revision-1')));
            $(this).attr('href', setParameter($(this).attr('href'), 'r2', $this.data('revision-2')));
        });

        const $revisions = $('input[data-revision-path]');

        // change the correct revision on every element
        // (every element keeps a track which revision is selected)
        $revisions.filter("[data-diff-revision='r1']").data('revision-2', $this.data('revision-2'));
        $revisions.filter("[data-diff-revision='r2']").data('revision-1', $this.data('revision-1'));

        // set the correct revision for the form submission
        $("#input_" + $this.data('diff-revision')).val($this.data('revision-path'));

        // enable all input
        $revisions.prop('disabled', false);
        // uncheck all input in my column
        $revisions.filter("[data-diff-revision='" + $this.data('diff-revision') + "']").prop('checked', false);
        // set me as checked
        $this.prop('checked', true);

        // disable from top to r2
        let index = Math.max($revisions.index($("input[data-revision-path][data-diff-revision='r2']:checked")), 0);
        $revisions.slice(0, index).filter("[data-diff-revision='r1']").prop('disabled', true);

        // disable from bottom to r1
        index = Math.max($revisions.index($("input[data-revision-path][data-diff-revision='r1']:checked")), index);
        $revisions.slice(index + 1).filter("[data-diff-revision='r2']").prop('disabled', true);
    });
}

function init_tablesorter() {
    $("#dirlist").tablesorter({
        sortList: [[0, 0]],
        cancelSelection: true,
        sortReset : true,
        sortRestart : true,
        sortInitialOrder: "desc",
        headers: {
            1: {
                sorter: 'text',
                sortInitialOrder: "asc"
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
    let converter = null;
    $('[data-markdown]').each(function () {
        var $that = $(this);
        $.script.loadScript('webjars/xss/1.0.8/dist/xss.min.js').done(function () {
            $.script.loadScript('webjars/showdown/1.9.1/dist/showdown.min.js').done(function () {
                $that.find('.markdown-content[data-markdown-download]').each(function () {
                    var $dataMarkdownDownloadEl = $(this);
                    if (converter === null) {
                        converter = new showdown.Converter();
                        converter.setOption('tables', true);
                        converter.setOption('strikethrough', true);
                        converter.setOption('tasklists', true);
                        converter.setOption('simplifiedAutoLink', true);
                        converter.setOption('parseImgDimension', true);
                    }

                    $.ajax({
                        url: $(this).data('markdown-download'),
                        dataType: 'text',
                        timeout: 5000,
                        mimeType: 'text/plain'
                    }).done(function (payload) {
                        $dataMarkdownDownloadEl.html(filterXSS(converter.makeHtml(payload))).show();
                        $that.addClass('markdown').find('[data-markdown-original]').hide();
                    });
                });
            });
        });
    });
}

window.onload = function () {
    for (let i in document.pageReady) {
        document.pageReady[i]();
    }
};

$(document).ready(function () {
    for (let i in this.domReady) {
        document.domReady[i]();
    }

    /**
     * Initialize scope scroll event to display scope information correctly when
     * the element comes into the viewport.
     */
    $('#src').each(function () {
        init_scopes();
    });

    /**
     * Initialize table sorter on every directory listing.
     */
    init_tablesorter();

    /**
     * Initialize intelligence window plugin. Presence of #contextpath indicates
     * that we use the code view.
     */
    $("#contextpath").each(function () {
        $.intelliWindow.init();
        return false;
    });

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
            const data = $(this).data('messages') || [];
            $.messagesWindow.update(data);
            $.messagesWindow.show();
        }).mouseleave(function (e) {
            $.messagesWindow.hide();
        });
    }

    /**
     * Initialize spaces plugin which automatically inserts a single space between
     * the line number and the following text. It strongly relies on the fact
     * that the line numbers are stored in 'name' attribute on each line link.
     */
    $.spaces.init();

    /**
     * Initialize the window hash management. Mainly this allows users to select
     * multiple lines of code and use that url to send it to somebody else.
     */
    $.hash.init({parent: "pre"});

    /**
     * After hitting the search button, the results or projects are temporarily hidden
     * until the new page is loaded. That helps to distinguish if search is being in process.
     */
    init_results_autohide();

    /**
     * Initialize the new project picker
     */
    init_searchable_option_list();

    /**
     * Initialize the history input picker.
     * Checkboxes are automatically covered with a click event and automatically
     * colored as disabled or checked.
     *
     * Also works for paging where it stores the actual selected revision range in the
     * pagination links.
     */
    init_history_input();

    /**
     * Initialize the markdown converter.
     *
     * WARNING: The converter is not XSS safe. If you're not sure about what
     * could occur in the readmes then rather comment out this.
     */
    init_markdown_converter();

    restoreFocusAfterSearchSubmit();
});

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
    const params = getParameter.params;
    // Then look for the parameter.
    for (let i in params) {
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
    const base = url.substr(0, url.indexOf('?'));
    const params = url.substr(base.length + 1).split("&").map(
            function (x) {
                return x.split("=");
            });
    let found = false;
    for (let i in params) {
        if (params[i][0] === p && params[i].length > 1) {
            params[i][1] = encodeURIComponent(v);
            found = true;
        }
    }
    if (!found) {
        params.push([p, encodeURIComponent(v)]);
    }

    return base + '?' + params.map(function (x) {
        return x[0] + '=' + x[1];
    }).join('&');
}

function domReadyMast() {
    if (!window.location.hash) {
        const h = getParameter("h");
        if (h && h !== "") {
            window.location.hash = h;
        } else {
            $("#content").
                    attr("tabindex", 1).
                    focus().
                    css('outline', 'none');
        }
    }
    if (document.annotate) {
        $('a.r').tooltip({
            content: function () {
                const element = $(this);
                const title = element.attr("title") || "";
                const parts = title.split(/<br\/>(?=[a-zA-Z0-9]+:)/g);
                if (parts.length <= 0) {
                    return "";
                }
                const $el = $("<dl>");
                for (let i = 0; i < parts.length; i++) {
                    const definitions = parts[i].split(":");
                    if (definitions.length < 2) {
                        continue;
                    }
                    $("<dt>").text(definitions.shift().trim()).appendTo($el);
                    const $dd = $("<dd>");
                    $.each(definitions.join(":").split("<br/>"), function (i, el) {
                        $dd.append(escapeHtml(el.trim()));
                        $dd.append($("<br/>"));
                    });
                    $dd.appendTo($el);
                }
                return $el;
            }
        });

        $("#toggle-annotate-by-javascript").css('display', 'inline');
        $("#toggle-annotate").hide();
    }
}

function pageReadyMast() {
}

function domReadyMenu(minisearch) {
    if (getCookie('OpenGrokSuggester.enabled') === 'false') {
        console.log('Suggester disabled by a cookie');
        return;
    }

    $.ajax({
        // cannot use "/api/v1/configuration/suggester" because of security
        url: window.contextPath + "/api/v1/suggest/config",
        dataType: "json",
        success: function(config) {
            if (config.enabled) {
                initAutocomplete(config, minisearch);
            }
        },
        error: function(xhr, ajaxOptions, error) {
            console.log('Could not get autocomplete configuration, probably disabled');
        }
    });
}

function initAutocomplete(config, minisearch) {
    if (minisearch) {
        initMinisearchAutocomplete(config);
    } else {
        initAutocompleteForField("full", "full", config);
        initAutocompleteForField("defs", "defs", config);
        initAutocompleteForField("refs", "refs", config);
        initAutocompleteForField("path", "path", config);
        initAutocompleteForField("hist", "hist", config);
    }
}

function initMinisearchAutocomplete(config) {
    if (config.allowedFields && config.allowedFields.indexOf('full') < 0) {
        return;
    }

    let project = '';

    var projectElem = $('#minisearch-project');
    if (projectElem) {
        project = projectElem.val();
    }

    const pathElem = $('#minisearch-path');

    initAutocompleteForField('search', 'full', config, function (input, field) {
        const caretPos = input.caret();
        if (!(typeof caretPos === 'number')) {
            console.error("Suggest: could not get caret position");
            return;
        }
        return {
            projects: [project],
            field: field,
            full: input.val(),
            path: pathElem.is(':checked') ? pathElem.val() : '',
            caret: caretPos
        };
    }, 'search');
}

function initAutocompleteForField(inputId, field, config, dataFunction, errorElemId) {
    if (config.allowedFields && config.allowedFields.indexOf(field) < 0) {
        return;
    }

    let text;
    let identifier;
    let time;
    let partialResult;

    const input = $("#" + inputId);

    if (!dataFunction) {
        dataFunction = getAutocompleteMenuData;
    }
    if (!errorElemId) {
        errorElemId = 'full';
    }
    const errorElem = $('#' + errorElemId);

    input.autocomplete({
        source: function(request, response) {
            $.ajax({
                url: window.contextPath + "/api/v1/suggest",
                dataType: "json",
                data: dataFunction(input, field),
                success: function(data) {
                    hideError(errorElem);

                    text = data.queryText;
                    identifier = data.identifier;
                    time = data.time;
                    partialResult = data.partialResult;

                    response(data.suggestions);
                },
                error: function(xhr, ajaxOptions, error) {
                    input.autocomplete("close");
                    response(undefined); // to remove loading indicator

                    showError(xhr.responseJSON.message, errorElem);
                },
                statusCode: {
                    404: function() {
                        response(); // do not show anything
                    }
                }
            });
        },
        create: function () {
            $(this).data('ui-autocomplete')._renderItem = function (ul, item) {
                const listItem = getSuggestionListItem(item, config);

                return listItem.appendTo(ul);
            };

            $(this).data('ui-autocomplete')._renderMenu = function (ul, items) {
                const _this = this;
                $.each(items, function(index, item) {
                    _this._renderItemData(ul, item);
                });
                if (config.showTime) {
                    $("<li>", {
                        "class": "ui-state-disabled",
                        style: 'padding-left: 5px;',
                        text: time + ' ms'
                    }).appendTo(ul);
                }
                if (partialResult) {
                    $("<li>", {
                        "class": "ui-state-disabled",
                        style: 'padding-left: 5px;',
                        text: 'Partial result due to timeout'
                    }).appendTo(ul);
                }
            };
        },
        focus: function (event, ui) {
            if (ui.item.selectable === false) {
                event.preventDefault();
                return;
            }
            if (event.originalEvent.originalEvent.type.indexOf('key') === 0) { // replace value only on key events
                replaceValueWithSuggestion(input, text, identifier, ui.item.phrase);
            }

            event.preventDefault(); // to prevent the movement of the caret to the end
        },
        select: function (event, ui) {
            replaceValueWithSuggestion(input, text, identifier, ui.item.phrase);

            event.preventDefault(); // to prevent the movement of the caret to the end
        },
        response: function (event, ui) {
            if (!ui.content) {
                // error occurred
                return;
            }
            if (ui.content.length === 0 && !partialResult) {
                const noMatchesFoundResult = {phrase: 'No matches found', selectable: false};
                ui.content.push(noMatchesFoundResult);
            }
        },
        minLength: config.minChars
    }).click(function() {
        $(this).autocomplete('search', $(this).val());
    }).keyup(function(e) {
        if (e.keyCode === 37 || e.keyCode === 39) { // left or right arrow key
            $(this).autocomplete('search', $(this).val());
        }
        // try to refresh on empty input (error might go away) except when pressed esc key
        if (input.val() === "" && e.keyCode !== 27) {
            $(this).autocomplete('search', ' ');
        }
    });
}

function getAutocompleteMenuData(input, field) {
    const caretPos = input.caret();
    if (!Number.isInteger(caretPos)) {
        console.error("Suggest: could not get caret position");
        return;
    }
    return {
        projects: getSelectedProjectNames(),
        field: field,
        full: $('#full').val(),
        defs: $('#defs').val(),
        refs: $('#refs').val(),
        path: $('#path').val(),
        hist: $('#hist').val(),
        type: $('#type').val(),
        caret: caretPos
    };
}

function replaceValueWithSuggestion(input, queryText, identifier, suggestion) {
    const pos = queryText.indexOf(identifier);
    const phrase = escapeLuceneCharacters(suggestion);
    input.val(queryText.replace(identifier, phrase));
    input.caret(pos + phrase.length);
}

function showError(errorText, errorElem) {
    const parent = errorElem.parent();

    parent.css('position', 'relative');

    let span = parent.find('#autocomplete-error')[0];
    if (!span) {
        span = $("<span>", {
            "class": "note-error important-note important-note-rounded",
            style: "right: -10px; position: absolute; top: 0px;",
            text: "!",
            id: 'autocomplete-error'
        });

        span.appendTo(parent);
    } else {
        span = $(span);
        span.off("mouseenter mouseleave");
    }

    span.hover(function() { // mouse in
        $.messagesWindow.empty();
        $.messagesWindow.append(escapeHtml(errorText));
        $.messagesWindow.show();
    }, function() { // mouse out
        $.messagesWindow.hide();
    });
}

function hideError(errorElem) {
    const parent = errorElem.parent();
    const span = parent.find('#autocomplete-error')[0];
    if (span) {
        span.remove();
    }
}

function getSuggestionListItem(itemData, config) {
    if (itemData.selectable === false) {
        return $("<li>", {
            "class": "ui-state-disabled",
            text: itemData.phrase
        });
    }

    const listItem = $("<li>", {
        "class": "ui-menu-item",
        style: "display: block;"
    });
    const listItemChild = $("<div>", {
        "class": "ui-menu-item-wrapper",
        style: "height: 20px; padding: 0;",
        tabindex: "-1"
    });

    listItemChild.appendTo(listItem);

    $("<span>", {
        text: itemData.phrase,
        style: "float: left; padding-left: 5px;"
    }).appendTo(listItemChild);

    let projectInfoText = "";
    if (config.showProjects) {
        if (itemData.projects.length > 1) {
            projectInfoText = 'Found in ' + itemData.projects.length + ' projects';
        } else {
            projectInfoText = itemData.projects[0];
        }
    }

    let score = "";
    if (config.showScores) {
        score = ' (' + itemData.score + ')';
    }
    $("<span>", {
        text: projectInfoText + score,
        style: "float: right; color: #999999; font-style: italic; padding-right: 5px;"
    }).appendTo(listItemChild);

    return listItem;
}

function escapeLuceneCharacters(term) {
    // must escape: + - && || ! ( ) { } [ ] ^ " ~ * ? : \
    const pattern = /([\+\-\!\(\)\{\}\[\]\^\"\~\*\?\:\\]|&&|\|\|)/g;

    return term.replace(pattern, "\\$1");
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
    let link = window.location.pathname + "?a=true";
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
    $.navigateWindow.init();
    if (typeof get_sym_list === 'function') {
        $.navigateWindow.update(get_sym_list());
    }
    $('#navigate').click(function () {
        $.navigateWindow.toggle();
        return false;
    });
}

/**
 * Toggle the display of line numbers.
 */
function lntoggle() {
    $(document.body).toggleClass("lines-hidden");
    $('.fold-space, .fold-icon, .unfold-icon').toggle();
}

function lnshow() {
    $(document.body).removeClass("lines-hidden");
    $('.fold-space, .fold-icon, .unfold-icon').show();
}

/* ------ Highlighting ------ */

/**
 *  Highlight keywords by changing the style of matching tags.
 */
function highlightKeyword(keyword) {
    const high_colors = [ "#ffff66", "#ffcccc", "#ccccff", "#99ff99", "#cc66ff" ];
    const pattern = "a:contains('" + keyword + "')";
    $(pattern).css({
        'text-decoration' : 'underline',
        'background-color' : high_colors[document.highlight_count % high_colors.length],
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
    const tbox = document.getElementById('input_highlight');
    highlightKeyword(tbox.value);
}

function toggle_filelist() {
    const $a = $('div.filelist');
    const $b = $('div.filelist-hidden');
    $a.toggle().toggleClass('filelist').toggleClass('filelist-hidden');
    $b.toggle().toggleClass('filelist').toggleClass('filelist-hidden');
}

function toggle_revtags() {
    const $a = $('tr.revtags, span.revtags');
    const $b = $('tr.revtags-hidden, span.revtags-hidden');
    $a.toggle().toggleClass('revtags').toggleClass('revtags-hidden');
    $b.toggle().toggleClass('revtags').toggleClass('revtags-hidden');
}

/**
 *  Function to toggle message length presentation
 */
function toggleCommon(closestType) {
  $(".rev-toggle-a").click(function() {
    const toggleState = $(this).closest(closestType).attr("data-toggle-state");
    const thisCell = $(this).closest("td");

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
        $("#project option").prop('selected', true);
    }
}

function invertAllProjects() {
    if ($("#project").data(SearchableOptionList.prototype.DATA_KEY)) {
        $("#project").searchableOptionList().invert();
    } else {
        $("#project option").each(function () {
            $(this).prop('selected', !$(this).prop('selected'));
        });
    }
}

function deselectAllProjects() {
    if ($("#project").data(SearchableOptionList.prototype.DATA_KEY)) {
        $("#project").searchableOptionList().deselectAll();
    } else {
        $("#project option").prop('selected', false);
    }
}

function clearSearchFrom() {
    $("#sbox input[type='text']").each(function () {
        $(this).val("");
    });
    $("#type").searchableOptionList().selectRadio("");
}

function getSelectedProjectNames() {
    try {
        return $.map($("#project").searchableOptionList().getSelection().filter("[name='project']"), function (item) {
            return $(item).attr("value");
        });
    } catch (e) { // happens when projects are not enabled
        return [];
    }
}

/**
 * Fold or unfold a function definition.
 */
function fold(id) {
    $('#' + id + '_fold_icon').
            children().
            first().
            toggleClass('unfold-icon').
            toggleClass('fold-icon');
    $('#' + id + '_fold').toggle('fold');
}

let scope_timeout = null;
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
        clearTimeout(scope_timeout);
        scope_timeout = null;
    }
    scope_timeout = setTimeout(function () {
        const y = $('#whole_header').outerHeight() + 2;
        const c = document.elementFromPoint(15, y + 1);

        if ($(c).is('.l, .hl')) {
            const $par = $(c).closest('.scope-body, .scope-head');

            if (!$par.length) {
                return;
            }

            const $head = $par.hasClass('scope-body') ? $par.prev() : $par;
            const $sig = $head.children().first();
            if ($.scopesWindow.initialized) {
                $.scopesWindow.update({
                    'id': $head.attr('id'),
                    'link': $sig.html()
                });
            }
        }
        scope_timeout = null;
    }, 150);
}

/**
 * Determines whether current page is search page = list with queried documents.
 * @returns true if on search page, false otherwise
 */
function isOnSearchPage() {
    return $(document.documentElement).hasClass('search');
}

function checkIsOnSearchPage() {
    if (isOnSearchPage()) {
        $('#xrd').val("1"); // no redirect
        return true;
    }
    return false;
}

/**
 * Preprocess the searched projects in the form with:
 *
 * <ol>
 *  <li>For all project search -> replace the projects with simple searchall parameter</li>
 *  <li>For group search -> replace all projects in a group by the group parameter</li>
 * </ol>
 * @param form the form containing the checkboxes
 */
function preprocess_searched_projects(form) {
    const sol = $('#project').searchableOptionList();

    const $sel = sol.$selectionContainer;

    /*
     * For all project search check if all project checkbox are checked and then uncheck them (they
     * would appear in the url) and add a hidden checkbox with searchall name.
     */
    const allProjectsSearch = $.makeArray($sel.find('.sol-checkbox[name=project]')).every(function (checkbox) {
        return $(checkbox).is(':checked');
    });

    if (allProjectsSearch && $('#search_all_projects').length === 0) {
        $sel.find('.sol-checkbox').prop('checked', false);
        const $input = $('<input>');
        const $all = $input.
            attr({
                id: 'search_all_projects',
                type: 'checkbox',
                value: true,
                name: 'searchall'
            }).
            prop('checked', true).
            css('display', 'none');
        $all.appendTo($(form));
        return;
    }

    /*
     * For selecting groups instead of projects, ommit the "Other" group. Loop over
     * all project checkbox in a group and when all of them are checked, uncheck them (they
     * would appear in the URL) and then check the group checkbox.
     */
    $sel.find('.sol-optiongroup').each(function () {
        const $el = $(this);

        // handle "Other" group for ungrouped projects
        if ($el.find('.sol-optiongroup-label').text() === 'Other') {
            $el.find('.sol-checkbox[name=group]').prop('checked', false);
            return;
        }

        const checkboxs = $el.find('.sol-option .sol-checkbox');
        for (let i = 0; i < checkboxs.length; i++) {
            const checkbox = $(checkboxs[i]);
            if (!checkbox.is(":checked")) {
                return;
            }
        }

        $el.find('.sol-checkbox[name=group]').prop('checked', true);
        for (let j = 0; j < checkboxs.length; j++) {
            const cb = $(checkboxs[j]);
            cb.prop('checked', false);
        }
    });
}

/**
 * Handles submit on search form.
 *
 * If submit was initiated by pressing the return key when focus was
 * in a text field then `si` attribute will be added to the form.
 *
 * @param {HTMLFormElement} form
 */
function searchSubmit(form) {
    let submitInitiator = '';
    if (textInputHasFocus()) {
        submitInitiator = document.activeElement.getAttribute('id');
    }
    if (submitInitiator) {
        const input = document.createElement('INPUT');
        input.setAttribute('name', 'si');
        input.value = submitInitiator;
        input.type = 'hidden';
        form.appendChild(input);
    }

    // replace all projects search with searchall parameter
    // select groups instead of projects if all projects in one group are selected
    preprocess_searched_projects(form);
}

/**
 * Restores focus on page load
 *
 * @see #searchSubmit
 */
function restoreFocusAfterSearchSubmit() {
    const siParam = getParameter('si');
    if (siParam) {
        const $input = $('input[type=text][id="' + siParam + '"]');
        if ($input.length === 1) {
            $input[0].selectionStart = $input.val().length;
            $input[0].selectionEnd = $input[0].selectionStart;
            $input.focus();
        }
    }
}

/**
 * @return {boolean} true if focus is on a input[type=text] element
 */
function textInputHasFocus() {
    return !!document.activeElement &&
        document.activeElement.nodeName === 'INPUT' &&
        document.activeElement.type === 'text';
}

function escapeHtml(string) { // taken from https://stackoverflow.com/questions/24816/escaping-html-strings-with-jquery
    const htmlEscapeMap = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;',
        '/': '&#x2F;',
        '`': '&#x60;',
        '=': '&#x3D;'
    };
    return String(string).replace(/[&<>"'`=\/]/g, function (s) {
        return htmlEscapeMap[s];
    });
}

/**
 * Taken from https://www.w3schools.com/js/js_cookies.asp .
 * @param cname cookie name to retrieve
 * @returns {string} cookie value
 */
function getCookie(cname) {
    const name = cname + "=";
    const decodedCookie = decodeURIComponent(document.cookie);
    const ca = decodedCookie.split(';');
    for (let i = 0; i < ca.length; i++) {
        let c = ca[i];
        while (c.charAt(0) == ' ') {
            c = c.substring(1);
        }
        if (c.indexOf(name) === 0) {
            return c.substring(name.length, c.length);
        }
    }
    return "";
}
