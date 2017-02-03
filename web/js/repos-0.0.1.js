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
 * This file contains JavaScript code used by repos.jspf.
 */

(function ($) {
    var accordion = function ($parent, options) {
        var inner = {
            initialized: false,
            options: {},
            defaults: {
                "showAllSelector": ".accordion_show_all",
                "hideAllSelector": ".accordion_hide_all"
            },
            $pannels: [],
            determineButtonsVisibility: function() {
                var decision = inner.$pannels.filter(":visible").length === inner.$pannels.length;
                return {
                    hide: decision ? inner.options.showAllSelector : inner.options.hideAllSelector,
                    show: decision ? inner.options.hideAllSelector : inner.options.showAllSelector,
                };
            },
            init: function () {
                inner.$pannels = inner.options.parent.find(".panel-body-accordion");

                inner.options.parent.find(".panel-heading-accordion").click(function (e) {
                    $(this).parent().find(".panel-body-accordion").each(function () {
                        if ($(this).data("accordion-visible")) {
                            $(this).hide()
                                    .data("accordion-visible", false)
                                    .parent()
                                    .find(".panel-heading-accordion .fold")
                                        .removeClass('fold-up')
                                        .addClass('fold-down')
                        } else {
                            $(this).show()
                                    .data("accordion-visible", true)
                                    .parent()
                                    .find(".panel-heading-accordion .fold")
                                        .removeClass('fold-down')
                                        .addClass('fold-up')
                        }
                    });

                    var btn = inner.determineButtonsVisibility();
                    inner.options.parent.find(btn.hide).hide();
                    inner.options.parent.find(btn.show).show();
                    return false
                });
                
                inner.options.parent.find(inner.options.showAllSelector).click(function (e) {
                    inner.$pannels
                            .data("accordion-visible", true)
                            .show()
                            .parent()
                            .find(".panel-heading-accordion .fold")
                                .removeClass('fold-down')
                                .addClass('fold-up')
                    inner.options.parent.find(inner.options.hideAllSelector).show()
                    inner.options.parent.find(inner.options.showAllSelector).hide()
                    return false;
                });

                inner.options.parent.find(inner.options.hideAllSelector).click(function (e) {
                    inner.$pannels
                            .data("accordion-visible", false)
                            .hide()
                            .parent()
                            .find(".panel-heading-accordion .fold")
                                .removeClass('fold-up')
                                .addClass('fold-down')
                    inner.options.parent.find(inner.options.hideAllSelector).hide()
                    inner.options.parent.find(inner.options.showAllSelector).show()
                    return false;
                });
                
                
                var btn = inner.determineButtonsVisibility();
                inner.options.parent.find(btn.hide).hide();
                inner.options.parent.find(btn.show).show();

                inner.initialized = true;
            }
        }

        var init = (function ($parent, options) {
            if (inner.initialized)
                return
            inner.options = $.extend({}, {parent: $parent}, inner.defaults, options)
            inner.init();
        })($parent, options);
    };

    $.fn.accordion = function (options) {
        return this.each(function () {
            options = options || {}
            new accordion($(this), options);
        });
    };
})(jQuery);

$(document).ready(function () {
    $("#footer").addClass("main_page");
    
    $(".projects").accordion();

    $(".projects_select_all").click(function (e) {
        $("#project .name")

        if (!e.ctrlKey) {
            $("#project").searchableOptionList().deselectAll()
        }
        $(this).closest('.panel').find('.name').each(function(){
          $("#project").searchableOptionList().selectAll($(this).text())  
        })

        return false;
    });
    domReadyHistory();
});
