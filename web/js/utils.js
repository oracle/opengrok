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

$(document).ready(function () {
    $("#content").scroll(scope_on_scroll);
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

    // starting spaces plugin
    $.spaces.init()

    $.hash.init({parent: "pre"})

    $("#sbox input[type='submit']").click(function (e) {
        $("#footer").not(".main_page").hide(); // footer
        $("#results > p.suggestions").hide(); // suggestions
        $("#results > p.pagetitle").hide(); // description
        $("#results > p.slider").hide(); // pagination
        $("#results > h3").hide(); // error
        $("#results > table, #results > ul").hide(); // results + empty
        $("#results > table + p, #results > ul + p").hide(); // results + empty timing
    })
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

function domReadyMast() {
    if (!window.location.hash) {
        var h = getParameter("h");
        if (h && h !== "") {
            window.location.hash = h;
        } else {
            $('#content').focus();
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
    var projects = document.projects;
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
    $("#revisions input[type=radio]").click(togglediffs);
    togglediffs();
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

function togglediffs() {
    var cr2 = false;
    var cr1 = false;
    $("#revisions input[type=radio]").each(
        function() {
            if (this.name=="r1") {
                if (this.checked) {
                    cr1 = true;
                    return true;
                }
                if (cr2) {
                    this.disabled = ''
                } else {
                    this.disabled = 'true'
                }
            } else if (this.name=="r2") {
                if (this.checked) {
                    cr2=true;
                    return true;
                }
                if (!cr1) {
                    this.disabled = ''
                } else {
                    this.disabled = 'true'
                }
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
    $("#project *").prop("selected", true);
}

function invertAllProjects() {
    $("#project *").each(
        function() {
            if ($(this).prop("selected")) {
                $(this).prop("selected", false);
            } else {
                $(this).prop("selected", true);
            }
        }
    );
}

function goFirstProject(e) {
    e = e || window.event

    var selected = $.map($('#project :selected'), function (e) {
        return $(e).text();
    });

    if ($(e.target).is("select")) {
        window.location = document.xrefPath + '/' + selected[0];
    } else if ($(e.target).is("option")) {
        window.location = document.xrefPath + '/' + selected[0];
    } else if ($(e.target).is("optgroup")) {
        if (!e.shiftKey) {
            $("#project :selected").prop("selected", false).change();
        }
        $(e.target).children().prop("selected", true).change();
    }
}

function clearSearchFrom() {
    $("#sbox input[type='text']").each(function () {
        $(this).val("");
    });
    $("#type :selected").prop("selected", false);
}

function checkEnter(event) {
    concat='';
    $("#sbox input[type='text']").each(
        function() {
                concat+=$.trim($(this).val());
        }
    );
    if (event.keyCode == '13' && concat=='')
    {
        goFirstProject(event);
    } else if (event.keyCode == '13') {
        $("#sbox").submit();
    }
}

// Intelligence Window code starts from here
document.onmousemove = function(event) {
    event = event || window.event; // cover IE
    document.intelliWindowMouseX = event.clientX;
    document.intelliWindowMouseY = event.clientY;
};

$(document).keypress(function(e) {
    if (document.activeElement.id === 'search' ||
        typeof document.intelliWindow === 'undefined') {
        return true;
    }

    if (e.which === 49) { // '1' pressed
        if (document.intelliWindow.className === "intelli_window_style") {
            hideIntelliWindow();
        } else if (document.intelliWindow.className === "intelli_window_style_hide") {
            showIntelliWindow();
        }
    }
    if (e.which === 50) { // '2' pressed
        var symbol = document.intelliWindow.symbol;
        var highlighted_symbols_with_same_name = $("a").filter(function(index) {
            var bgcolor = $(this).css("background-color");
            return $(this).text() === symbol &&
                (bgcolor === "rgb(255, 215, 0)" || bgcolor === "rgb(255,215,0)" || bgcolor === "#ffd700"); // gold.  the last two cover IE
        })
        if (highlighted_symbols_with_same_name.length === 0) {
            highlightSymbol(symbol);
        } else {
            unhighlightSymbol(symbol);
        }
    }
    return true;
});

function onMouseOverSymbol(symbol, symbolType) {
    updateIntelliWindow(symbol, symbolType);
}

function updateIntelliWindow(symbol, symbolType) {
    if (!document.intelliWindow) {
        createIntelliWindow();
    }
    var header = [
        createCapitionHTML(),
        createSymbolHTML(symbol),
        createDescriptionHTML(symbolType),
    ].join("");

    document.intelliWindow.innerHTML = header + createActionHTML(symbol, symbolType);
    document.intelliWindow.symbol = symbol;
}

function showIntelliWindow() {
    var iw = document.intelliWindow;
    iw.className = "intelli_window_style";

    var top;
    var left;
    if (document.intelliWindowMouseY + iw.offsetHeight + 20 > $(window).height()) {
        top = $(window).height() - iw.offsetHeight - 20;
    } else {
        top = document.intelliWindowMouseY;
    }
    if (document.intelliWindowMouseX + iw.offsetWidth + 20 > $(window).width()) {
        left = $(window).width() - iw.offsetWidth - 20;
    } else {
        left = document.intelliWindowMouseX;
    }
    iw.style.top = top + "px";
    iw.style.left = left + "px";
}

function createIntelliWindow() {
    document.intelliWindow = document.createElement("div");
    document.intelliWindow.id = "intelli_win";
    document.body.appendChild(document.intelliWindow);
    hideIntelliWindow();
}

function hideIntelliWindow() {
    document.intelliWindow.className = "intelli_window_style_hide";
}

function createCapitionHTML() {
    return '<a onclick="hideIntelliWindow()">[Close]</a><br/><b>Intelligence Window</b><br/>';
}

function createSymbolHTML(symbol) {
    return "<i><h2>" + symbol + "</h2></i>";
}

function createDescriptionHTML(symbolType) {
    switch (symbolType) {
        case "def":
            return "A declaration or definition.<hr/>";
        case "defined-in-file":
            return "A symbol declared or defined in this file.<hr/>";
        case "undefined-in-file":
            return "A symbol declared or defined elsewhere.<hr/>";
        default:
            // should not happen
            return "Something I have no idea about.<hr/>";
    }
}

function createActionHTML(symbol, symbolType) {
    var escapedSymbol = escapeSingleQuote(symbol);
    var project = $("input[name='project']").val();
    return [
        "In current file:<br/><ul>",
        "<li><a onclick=\"highlightSymbol('", escapedSymbol, "')\">Highlight <b><i>", symbol,
            "</i></b></a>.</li>",
        "<li><a onclick=\"unhighlightSymbol('", escapedSymbol, "')\">Unhighlight <b><i>", symbol,
            "</i></b></a>.</li>",
        "<li><a onclick=\"unhighlightAll()\">Unhighlight all.</li></ul>",
        "In project ", project, ":<br/><ul>",
        "<li><a onclick=\"intelliWindowSearch('defs=', '", escapedSymbol, "', '", symbolType,
            "')\">Search for definitions of <i><b>", symbol,
            "</b></i>.</a></li>",
        "<li><a onclick=\"intelliWindowSearch('refs=', '", escapedSymbol, "', '", symbolType,
            "')\">Search for references of <i><b>", symbol,
            "</b></i>.</a></li>",
        "<li><a onclick=\"intelliWindowSearch('q=', '", escapedSymbol, "', '", symbolType,
            "')\">Do a full search with <i><b>", symbol,
            "</b></i>.</a></li>",
        "<li><a onclick=\"intelliWindowSearch('path=', '", escapedSymbol, "', '", symbolType,
            "')\">Search for file names that contain <i><b>", symbol,
            "</b></i>.</a></li></ul>",
        "<a onclick=\"googleSymbol('", escapedSymbol, "')\">Google <b><i>", symbol, "</i></b>.</a>"
    ].join("");
}

function highlightSymbol(symbol) {
    var symbols_with_same_name = $("a").filter(function(index) {
        return $(this).text() === symbol;
    })
    symbols_with_same_name.css("background-color",  "rgb(255, 215, 0)"); // gold
    return false;
}

function unhighlightSymbol(symbol) {
    var symbols_with_same_name = $("a").filter(function(index) {
        return $(this).text() === symbol;
    })
    symbols_with_same_name.css("background-color", "rgb(255, 255, 255)"); // white
    return false;
}

function unhighlightAll() {
    $("a").filter(function(index) {
        var bgcolor = $(this).css("background-color");
        return bgcolor === "rgb(255, 215, 0)" || bgcolor === "rgb(255,215,0)" || bgcolor === "#ffd700";  // gold.  the last two cover IE
    }).css("background-color", "rgb(255, 255, 255)"); // white
    return false;
}

function intelliWindowSearch(param, symbol, symbolType) {
    var contextPath = $("#contextpath").val();
    var project = $("input[name='project']").val();
    var url = contextPath + "/s?" + param + symbol + "&project=" + project;
    window.open(url, '_blank');
    return false;
}

function googleSymbol(symbol) {
    var url = "https://www.google.com/search?q=" + symbol;
    window.open(url, '_blank');
    return false;
}

function escapeSingleQuote(string) {
    return string.replace("'", "\\'");
}


var scope_visible = 0;
var scope_text = '';

/**
 * Fold or unfold a function definition.
 */
function fold(id) {        
    var i = document.getElementById(id + "_fold_icon").children[0];
    i.className = i.className === 'fold-icon' ? 'unfold-icon' : 'fold-icon';
    $("#" + id + "_fold").toggle('fold');
}

/**
 * Function that is called when the #content div element is scrolled. Checks
 * if the top of the page is inside a function scope. If so, update the
 * scope element to show the name of the function and a link to its definition.
 */
function scope_on_scroll() {
    var cnt = document.getElementById("content");
    var scope_cnt = document.getElementById("scope_content");
    var y = cnt.getBoundingClientRect().top + 2;

    var c = document.elementFromPoint(15, y+1);
    scope_cnt.innerHTML = '';
    if (c.className === "l" || c.className === "hl") {
        prev = c;
        var par = c.parentNode;
        while( par.className !== 'scope-body' && par.className !== 'scope-head' ) {
            par = par.parentNode;
            if (par === null) {
                return ;
            }
        }
        var head = par.className === 'scope-body' ? par.previousSibling : par;
        var sig = head.children[0];
        scope_cnt.innerHTML = '<a href="#' + head.id + '">' + sig.innerHTML + '</a>';
    }
}
