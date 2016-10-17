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
 * Copyright (c) 2009, 2016, Oracle and/or its affiliates. All rights reserved.
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
                if(lines.length > 0) {
                   var line = lines[0] // first line
                   $("#content").animate({
                      scrollTop: $(inner.format(inner.options.linkSelectorTemplate, {
                          parent: inner.options.parent,
                          n: line
                      })).offset().top - $(inner.options.parent).offset().top
                   }, inner.options.autoScrollDuration);
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
 * General window
 */
(function (window, document, $) {
    var window = function () {
        var inner = function (options) {
            var self = this
            // private
            this.initialized = false
            this.$window = undefined
            this.$errors = undefined
            this.active = false
            this.clientX = 0
            this.clientY = 0
            // public
            this.defaults = {
                title: 'Window',
                appendDraggable: '#content',
                draggable: true,
                draggableScript: 'js/jquery-ui-1.12.0-draggable.min.js', // relative to context
                contextPath: window.contextPath,
            }
            this.options = $.extend({}, self.defaults, options);
            // private
            this.determinePosition = function () {
                var position = {}
                var $w = this.$window;
                if (this.clientY + $(window).scrollTop() + $w.height() + 40 > $(window).height()) {
                    position.top = $(window).height() - $w.height() - 40;
                } else {
                    position.top = this.clientY + $(window).scrollTop()
                }
                if (this.clientX + $w.width() + 40 > $(window).width()) {
                    position.left = $(window).width() - $w.width() - 40;
                } else {
                    position.left = this.clientX;
                }
                return position;
            }

            this.makeMeDraggable = function () {
                $.script.loadScript(this.options.draggableScript).done(function () {
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

            this.registerHandlers = function () {
                $(document).mousemove(function (e) {
                    self.clientX = e.clientX;
                    self.clientY = e.clientY;
                    //console.log(self.clientX, self.clientY)
                })
                $(document).keyup(function (e) {
                    var key = e.keyCode
                    switch (key) {
                        case 27: // esc
                            self.$window.hide();
                            break;
                        default:
                    }
                    return true;
                });
            }

            this.createWindow = function () {
                var $window, $top, $close, $controls

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
                    $window.toggle()
                    return false;
                });

                return $window;
            }

            // main
            this.$window = this.$window || this.createWindow()
            this.registerHandlers()

            if (this.options.draggable && this.options.contextPath) {
                this.makeMeDraggable()
            }

            this.$window.error = function (msg) {
                var $span = $("<p class='error'>" + msg + "</p>")
                        .animate({opacity: "0.2"}, 3000)
                $span.hide('slow', function () {
                    $span.remove();
                });
                self.$errors.html($span)
            }

            this.$window.move = function (position) {
                position = position || self.determinePosition()
                return this.css(position)
            };

            this.$window.toggleAndMove = function () {
                return this.toggle().move()
            }

            this.$window.isActive = function () {
                return self.active
            }

            return this.$window;
        };

        this.create = function (options) {
            return new inner(options)
        }
    };
    $.window = new ($.extend(window, $.window ? $.window : {}));
})(window, document, jQuery);

/**
 * Intelligence window plugin.
 * 
 * Reworked to use Jquery in 2016
 */
(function (window, document, $) {
    var intelliWindow = function () {
        var inner = {
            // private
            initialized: false,
            $window: undefined,
            symbol: undefined,
            contextPath: undefined,
            project: undefined,
            $symbols: undefined,
            $current: undefined,
            $last_highlighted_current: $(),
            $search_defs: undefined,
            $search_refs: undefined,
            $search_full: undefined,
            $search_files: undefined,
            $search_google: undefined,
            // public
            defaults: {
                title: 'Intelligence window',
                parent: undefined,
                draggable: true,
                selector: 'a.intelliWindow-symbol',
                google_url: 'https://www.google.com/search?q=',
                contextPath: window.contextPath,
                project: undefined,
            },
            options: {},
            // private
            changeSymbol: function ($el) {
                inner.$current = $el
                inner.$last_highlighted_current = $el.hasClass("symbol-highlighted") ? $el : inner.$last_highlighted_current
                inner.symbol = $el.text()
                inner.place = $el.data("definition-place")
                inner.$window.find('.hidden-on-start').show()
                inner.$window.find(".symbol-name").text(inner.symbol);
                inner.$window.find(".symbol-description").text(inner.getSymbolDescription(inner.place))
                inner.modifyLinks();
            },
            modifyLinks: function () {
                inner.$search_defs = inner.$search_defs || inner.$window.find('.search-defs')
                inner.$search_refs = inner.$search_refs || inner.$window.find('.search-refs')
                inner.$search_full = inner.$search_full || inner.$window.find('.search-full')
                inner.$search_files = inner.$search_files || inner.$window.find('.search-files')
                inner.$search_google = inner.$search_google || inner.$window.find('.search-google')

                inner.$search_defs.attr('href', inner.getSearchLink('defs'));
                inner.$search_refs.attr('href', inner.getSearchLink('refs'));
                inner.$search_full.attr('href', inner.getSearchLink('q'));
                inner.$search_files.attr('href', inner.getSearchLink('path'));
                inner.$search_google.attr('href', inner.options.google_url + inner.symbol)
            },
            getSearchLink: function (query) {
                return inner.options.contextPath + '/search?' + query + '=' + inner.symbol + '&project=' + inner.project;
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
                return (inner.$symbols = inner.$symbols || $(inner.options.selector));
            },
            highlight: function (symbol) {
                if (inner.$current.text() === symbol) {
                    inner.$last_highlighted_current = inner.$current;
                }
                return inner.getSymbols().filter(function () {
                    return $(this).text() === symbol;
                }).addClass('symbol-highlighted')
            },
            unhighlight: function (symbol) {
                if (inner.$last_highlighted_current &&
                        inner.$last_highlighted_current.text() === symbol &&
                        inner.$last_highlighted_current.hasClass('symbol-highlighted')) {
                    var i = inner.getSymbols().index(inner.$last_highlighted_current)
                    inner.$last_highlighted_jump = inner.getSymbols().slice(0, i).filter('.symbol-highlighted').last();
                }
                return inner.getSymbols().filter(".symbol-highlighted").filter(function () {
                    return $(this).text() === symbol;
                }).removeClass('symbol-highlighted')

            },
            unhighlightAll: function () {
                inner.$last_highlighted_current = undefined
                return inner.getSymbols().filter(".symbol-highlighted").removeClass("symbol-highlighted")
            },
            scrollTop: function ($el) {
                if (inner.options.scrollTop) {
                    inner.options.scrollTop($el)
                } else {
                    $("#content").stop().animate({
                        scrollTop: $el.offset().top - $("#src").offset().top
                    }, 500);
                }
            },
            scrollToNextElement: function (direction) {
                var UP = -1;
                var DOWN = 1;
                var $highlighted = inner.getSymbols().filter(".symbol-highlighted");
                var $el = $highlighted.length && inner.$last_highlighted_current
                        ? inner.$last_highlighted_current
                        : inner.$current;
                var indexOfCurrent = inner.getSymbols().index($el);

                switch (direction) {
                    case DOWN:
                        $el = inner.getSymbols().slice(indexOfCurrent + 1);
                        if ($highlighted.length) {
                            $el = $el.filter('.symbol-highlighted')
                        }
                        if (!$el.length) {
                            inner.$window.error("This is the last occurence!")
                            return;
                        }
                        $el = $el.first();
                        break;
                    case UP:
                        $el = inner.getSymbols().slice(0, indexOfCurrent);
                        if ($highlighted.length) {
                            $el = $el.filter('.symbol-highlighted')
                        }
                        if (!$el.length) {
                            inner.$window.error("This is the first occurence!")
                            return;
                        }
                        $el = $el.last();
                        break;
                    default:
                        inner.$window.error("Uknown direction")
                        return;
                }

                inner.scrollTop($el)
                inner.changeSymbol($el)
            },
            toggle: function () {
                return inner.$window.toggle();
            },
            hide: function () {
                return inner.$window.hide();
            },
            show: function () {
                return inner.$window.show();
            },
            registerHandlers: function () {
                $(document).keypress(function (e) {
                    var key = e.which
                    switch (key) {
                        case 49: // 1
                            if (inner.symbol) {
                                inner.$window.toggleAndMove()
                            }
                            break;
                        case 50: // 2
                            if (inner.symbol) {
                                inner.unhighlight(inner.symbol).length === 0 && inner.highlight(inner.symbol)
                            }
                            break;
                        case 51: // 3
                            inner.unhighlightAll()
                            break;
                        case 110: // n
                            inner.scrollToNextElement(1)
                            break;
                        case 98: // b
                            inner.scrollToNextElement(-1)
                            break;
                        default:
                    }
                    return true;
                });
                inner.getSymbols().mouseover(function () {
                    inner.changeSymbol($(this));
                });
            },
            bindOnClick: function ($el, callback, param) {
                $el.click(function (e) {
                    e.preventDefault()
                    callback(param || inner.symbol)
                    return false;
                })
            },
            apply: function ($window) {
                var $highlight, $unhighlight, $unhighlighAll, $prev, $next

                var $firstList = $("<ul>")
                        .append($("<li>").append(
                                $highlight = $("<a href=\"#\" title=\"Highlight\">" +
                                        "<span>Highlight</span> <b class=\"symbol-name\"></b></a>")))
                        .append($("<li>").append(
                                $unhighlight = $("<a href=\"#\" title=\"Unhighlight\">" +
                                        "<span>Unhighlight</span> <b class=\"symbol-name\"></b></a>")))
                        .append($("<li>").append(
                                $unhighlighAll = $("<a href=\"#\" title=\"Unhighlight all\">" +
                                        "<span>Unhighlight all</span></a>")))

                inner.bindOnClick($highlight, inner.highlight)
                inner.bindOnClick($unhighlight, inner.unhighlight)
                inner.bindOnClick($unhighlighAll, inner.unhighlightAll);

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
                        .append(inner.$errors = $("<span class=\"clearfix\">"))

                inner.bindOnClick($next, inner.scrollToNextElement, 1);
                inner.bindOnClick($prev, inner.scrollToNextElement, -1);

                return $window
                        .attr('id', 'intelli_win')
                        .addClass('intelli-window')
                        .append($controls)
                        .append($("<h2>").addClass('symbol-name'))
                        .append($("<span>").addClass('symbol-description'))
                        .append($("<hr>"))
                        .append($("<h5>").text("In current file"))
                        .append($firstList)
                        .append($("<h5>").text("In project \"" + inner.project + "\""))
                        .append($secondList)
                        .append($("<h5>").text("On Google"))
                        .append($thirdList)
            },
            init: function () {
                if (inner.initialized) {
                    return
                }
                inner.initialized = true
                inner.project = inner.options.project || $("input[name='project']").val();

                inner.$window = inner.$window || $.window.create(inner.options)
                inner.$window = inner.apply(inner.$window)
                inner.$window.appendTo(inner.parent ? $(inner.parent) : $("#content"));
                inner.registerHandlers()
            }
        };

        this.init = function (options) {
            inner.options = $.extend({}, inner.defaults, options);
            inner.init();
        }
    };
    $.intelliWindow = new ($.extend(intelliWindow, $.intelliWindow ? $.intelliWindow : {}));
})(window, document, jQuery);

/**
 * Messages window plugin.
 *
 * @author Kryštof Tulinger
 */
(function (window, document, $) {
    var messagesWindow = function () {
        var inner = {
            // private
            initialized: false,
            $window: undefined,
            $messages: $(),
            // public
            defaults: {
                title: 'Messages Window',
                parent: undefined,
                draggable: false,
                contextPath: window.contextPath,
            },
            options: {},
            // private
            registerHandlers: function () {
                inner.$window.mouseenter(function () {
                    inner.$window.show()
                }).mouseleave(function () {
                    inner.$window.hide()
                })
            },
            apply: function ($window) {
                return $window
                        .attr('id', 'messages_win')
                        .addClass('messages-window')
                        .addClass('diff_navigation_style')
                        .css({top: '150px', right: '20px'})
                        //.append($("<h5>").text("System messages"))
                        .append(inner.$messages = $("<div>"))
            },
            init: function () {
                if (inner.initialized) {
                    return
                }
                inner.initialized = true

                inner.$window = inner.$window || $.window.create(inner.options)
                inner.$window = inner.apply(inner.$window)
                inner.$window.appendTo(inner.parent ? $(inner.parent) : $("body"));
                inner.registerHandlers()
            }
        };

        this.toggle = function () {
            return inner.$window.toggle().move()
        }

        this.hide = function () {
            return inner.$window.hide();
        }

        this.update = function (data) {
            inner.$messages.empty()
            for (var i = 0; i < data.length; i++) {
                var tag = data[i]
                if (!tag || tag.messages.length === 0) {
                    continue;
                }
                inner.$messages.append($("<h5>")
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
                inner.$messages.append($ul);
            }
        }

        this.show = function () {
            return inner.$window.show().move()
        }

        this.init = function (options) {
            inner.options = $.extend({}, inner.defaults, options);
            inner.init();
        }
    };
    $.messagesWindow = new ($.extend(messagesWindow, $.messagesWindow ? $.messagesWindow : {}));
})(window, document, jQuery);

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
        events: {
            onInitialized: function () {
                this.$selectionContainer.find("[data-messages]").mouseenter(function () {
                    var data = $(this).data('messages') || []
                    $.messagesWindow.update(data)
                    $.messagesWindow.show()
                }).mouseleave(function (e) {
                    $.messagesWindow.hide()
                })
            },
            // override the default onScroll positioning event if neccessary
            onScroll: function () {

                var posY = this.$input.offset().top - this.config.scrollTarget.scrollTop() + this.$input.outerHeight(),
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

        // set the correct revision for the form submittion
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

$(document).ready(function () {
    /**
     * Initialize scope scroll event to display scope information correctly when
     * the element comes into the viewport.
     */
    $("#content").scroll(scope_on_scroll);

    /**
     * Initialize table sorter on every directory listing.
     */
    init_tablesorter()

    /**
     * Initialize inteligence window plugin. Presence of #contextpath indicates
     * that we use the code view.
     */
    $("#contextpath").each(function () {
        $.intelliWindow.init();
        return false
    })

    /**
     * Initialize the messages plugin to display
     * message onhover on every affected element.
     */
    $.messagesWindow.init();

    /**
     * Attaches a onhover listener to display messages for affected elements.
     */
    $("[data-messages]").mouseenter(function () {
        var data = $(this).data('messages') || []
        $.messagesWindow.update(data)
        $.messagesWindow.show()
    }).mouseleave(function (e) {
        $.messagesWindow.hide()
    })

    /**
     * Initialize spaces plugin which automaticaly inserts a single space between
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
     * Checkboxes are automaticaly covered with a click event and automaticaly
     * colored as disabled or checked.
     *
     * Also works for paging where it stores the actual selected revision range in the
     * pagination links.
     */
    init_history_input()
});

document.pageReady = [];
document.domReady = [];

window.onload = function() {
    for(var i in document.pageReady) {
        document.pageReady[i]();
    }
}

$(document).ready(function() {
    for(var i in this.domReady) {
        document.domReady[i]();
    }
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
                    $.each(definitions.join("").split("<br/>"), function (i, el) {
                        $dd.append(el.trim());
                        $dd.append($("<br/>"));
                    });
                    $dd.appendTo($el);
                }
                return $el;
            },
        })
        //$('a.r').tooltip({ left: 5, showURL: false });
        var toggle_js = document.getElementById('toggle-annotate-by-javascript');
        var toggle_ss = document.getElementById('toggle-annotate');

        toggle_js.style.display = 'inline';
        toggle_ss.style.display = 'none';
    }

    // When we move to a version of XHTML that supports the onscroll
    // attribute in the div element, we should add an onscroll attribute
    // in the generated XHTML in mast.jsp. For now, set it with jQuery.
    $("#content").scroll(scope_on_scroll);
}

function pageReadyMast() {
    document.adjustContent = 0;
    if ($('#whole_header') != null && $('#content') != null) {
        document.adjustContent = 1;
        resizeContent();
    }
    $(window).resize(
        function() {
            resizeContent();
        }
    );
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
}

function get_annotations() {
    var link = window.location.pathname + "?a=true";
    if (document.rev) {
        link += "&r=" + encodeURIComponent(document.rev);
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
    document.sym_div_width = 240;
    document.sym_div_height_max = 480;
    document.sym_div_top = 100;
    document.sym_div_left_margin = 40;
    document.sym_div_height_margin = 40;
    document.highlight_count = 0;
    $(window).resize(function() {
        if (document.sym_div_shown == 1) {
            document.sym_div.style.left = get_sym_div_left() + "px";
            document.sym_div.style.height = get_sym_div_height() + "px";
        }
    });
}

/* ------ Navigation window for definitions ------ */
/**
 * Create the Navigation toggle link as well as its contents.
 */
function get_sym_list_contents() {
    // var contents = "<input id=\"input_highlight\" name=\"input_highlight\"
    // class=\"q\"/>";
    // contents += "&nbsp;&nbsp;";
    // contents += "<b><a href=\"#\" onclick=\"javascript:add_highlight();return
    // false;\" title=\"Add highlight\">Highlight</a></b><br/>";
    var contents =
        "<a href=\"#\" onclick=\"javascript:lsttoggle();\">[Close]</a><br/>"
    if (typeof get_sym_list != 'function') {
        return contents;
    }

    var symbol_classes = get_sym_list();
    for ( var i = 0; i < symbol_classes.length; i++) {
        if (i > 0) {
            contents += "<br/>";
        }
        var symbol_class = symbol_classes[i];
        var class_name = symbol_class[1];
        var symbols = symbol_class[2];
        contents += "<b>" + symbol_class[0] + "</b><br/>";

        for (var j = 0; j < symbols.length; j++) {
            var symbol = symbols[j][0];
            var line = symbols[j][1];
            contents += "<a href=\"#" + line + "\" class=\"" + class_name + "\" onclick=\"lnshow(); return true;\">"
                + escape_html(symbol) + "</a><br/>";
        }
    }

    return contents;
}

function escape_html(string) {
    return string.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
}

function get_sym_div_left() {
    document.sym_div_left = $(window)
        .width() - (document.sym_div_width + document.sym_div_left_margin);
    return document.sym_div_left;
}

function get_sym_div_height() {
    document.sym_div_height = $(window)
        .height() - document.sym_div_top - document.sym_div_height_margin;

    if (document.sym_div_height > document.sym_div_height_max) {
        document.sym_div_height = document.sym_div_height_max;
    }
    return document.sym_div_height;
}

function get_sym_div_top() {
    return document.sym_div_top;
}

function get_sym_div_width() {
    return document.sym_div_width;
}

/**
 * Toggle the display of the 'Navigation' window used to highlight definitions.
 */
function lsttoggle() {
    if (document.sym_div == null) {
        document.sym_div = document.createElement("div");
        document.sym_div.id = "sym_div";

        document.sym_div.className = "sym_list_style";
        document.sym_div.style.margin = "0px auto";
        document.sym_div.style.width = get_sym_div_width() + "px";
        document.sym_div.style.height = get_sym_div_height() + "px";
        document.sym_div.style.top = get_sym_div_top() + "px";
        document.sym_div.style.left = get_sym_div_left() + "px";

        document.sym_div.innerHTML = get_sym_list_contents();

        document.body.appendChild(document.sym_div);
        document.sym_div_shown = 1;
    } else if (document.sym_div_shown == 1) {
        document.sym_div.className = "sym_list_style_hide";
        document.sym_div_shown = 0;
    } else {
        document.sym_div.style.height = get_sym_div_height() + "px";
        document.sym_div.style.width = get_sym_div_width() + "px";
        document.sym_div.style.top = get_sym_div_top() + "px";
        document.sym_div.style.left = get_sym_div_left() + "px";
        document.sym_div.className = "sym_list_style";
        document.sym_div_shown = 1;
    }
}

/**
 * Toggle the display of line numbers.
 */
function lntoggle() {
    $(document.body).toggleClass("lines-hidden");
}

function lnshow() {
    $(document.body).removeClass("lines-hidden");
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
 *  Highlight keywords by changeing the style of matching tags.
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
    $("div").each(
        function() {
            if (this.className == "filelist") {
                this.setAttribute("style", "display: none;");
                this.className = "filelist-hidden";
            } else if (this.className == "filelist-hidden") {
                this.setAttribute("style", "display: inline;");
                this.className = "filelist";
            }
        }
    );
}

function toggle_revtags() {
    $("tr").each(
        function() {
            if (this.className == "revtags") {
                this.setAttribute("style", "display: none;");
                this.className = "revtags-hidden";
            } else if (this.className == "revtags-hidden") {
                this.setAttribute("style", "display: table-row;");
                this.className = "revtags";
            }
        }
    );
    $("span").each(
        function() {
            if (this.className == "revtags") {
                this.setAttribute("style", "display: none;");
                this.className = "revtags-hidden";
            } else if (this.className == "revtags-hidden") {
                this.setAttribute("style", "display: inline;");
                this.className = "revtags";
            }
        }
    );
}

/**
 *  Function to toggle revision message length for long revision messages
 */
function togglerevs() {
  $(".rev-toggle-a").click(function() {
    var toggleState = $(this).closest("p").attr("data-toggle-state");
    var thisCell = $(this).closest("td");

    if (toggleState == "less") {
      $(this).closest("p").attr("data-toggle-state", "more");
      thisCell.find(".rev-message-summary").addClass("rev-message-hidden");
      thisCell.find(".rev-message-full").removeClass("rev-message-hidden");
      $(this).html("... show less");
    }
    else if (toggleState == "more") {
      $(this).closest("p").attr("data-toggle-state", "less");
      thisCell.find(".rev-message-full").addClass("rev-message-hidden");
      thisCell.find(".rev-message-summary").removeClass("rev-message-hidden");
      $(this).html("show more ...");
    }

    return false;
  });
}

function selectAllProjects() {
    $("#project").searchableOptionList().selectAll();
}

function invertAllProjects() {
    $("#project").searchableOptionList().invert();
}

function deselectAllProjects(){
    $("#project").searchableOptionList().deselectAll();
}

function clearSearchFrom() {
    $("#sbox input[type='text']").each(function () {
        $(this).val("");
    });
    $("#type :selected").prop("selected", false);
}

var scope_visible = 0;
var scope_text = '';

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

/**
 * Function that is called when the #content div element is scrolled. Checks
 * if the top of the page is inside a function scope. If so, update the
 * scope element to show the name of the function and a link to its definition.
 */
function scope_on_scroll() {
    var cnt = document.getElementById("content");
    var y = cnt.getBoundingClientRect().top + 2;
    var $scope_cnt_el = $('#scope_content');
    var c = document.elementFromPoint(15, y + 1);

    if ($(c).is('.l, .hl')) {
        var $par = $(c).closest('.scope-body, .scope-head')

        if (!$par.length) {
            return;
        }

        var $head = $par.hasClass('scope-body') ? $par.prev() : $par;
        var $sig = $head.children().first()
        $scope_cnt_el.html('<a href="#' + $head.attr('id') + '">' + $sig.html() + '</a>');
    }
}
