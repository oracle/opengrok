/*
 * SOL - Searchable Option List jQuery plugin
 * Version 2.0.2
 * https://pbauerochse.github.io/searchable-option-list/
 *
 * Copyright 2015, Patrick Bauerochse
 *
 * Licensed under the MIT license:
 * http://www.opensource.org/licenses/MIT
 *
 */

/*
 * Original based on SOL v2.0.2
 * Modified by Krystof Tulinger 2016
 */

/*jslint nomen: true */
;
(function ($, window, document) {
    'use strict';

    // constructor
    var SearchableOptionList = function ($element, options) {
        this.$originalElement = $element;
        this.options = options;

        // allow setting options as data attribute
        // e.g. <select data-sol-options="{'allowNullSelection':true}">
        this.metadata = this.$originalElement.data('sol-options');
    };

    // plugin prototype
    SearchableOptionList.prototype = {

        SOL_OPTION_FORMAT: {
            type:     'option',        // fixed
            value:    undefined,       // value that will be submitted
            selected: false,           // boolean selected state
            disabled: false,           // boolean disabled state
            label:    undefined,       // label string
            tooltip:  undefined,       // tooltip string
            cssClass: ''               // custom css class for container
        },
        SOL_OPTIONGROUP_FORMAT: {
            type:     'optiongroup',    // fixed
            label:    undefined,        // label string
            tooltip:  undefined,        // tooltip string
            disabled: false,            // all children disabled boolean property
            children: undefined         // array of SOL_OPTION_FORMAT objects
        },

        DATA_KEY: 'sol-element',
        WINDOW_EVENTS_KEY: 'sol-window-events',

        // default option values
        defaults: {
            data: undefined,
            name: undefined,           // name attribute, can also be set as name="" attribute on original element or data-sol-name=""

            texts: {
                noItemsAvailable: 'No entries found',
                selectAll: 'Select all',
                selectNone: 'Select none',
                quickDelete: '&times;',
                searchplaceholder: 'Click here to search',
                loadingData: 'Still loading data...',
                /*
                 * Modified 2016
                 */
                itemsSelected: '{$a} more items selected'
            },

            events: {
                onInitialized: undefined,
                onRendered: undefined,
                onOpen: undefined,
                onClose: undefined,
                onChange: undefined,
                onScroll: function () {

                    var selectionContainerYPos = this.$input.offset().top - this.config.scrollTarget.scrollTop() + this.$input.outerHeight(false),
                        selectionContainerHeight = this.$selectionContainer.outerHeight(false),
                        selectionContainerBottom = selectionContainerYPos + selectionContainerHeight,
                        displayContainerAboveInput = this.config.displayContainerAboveInput || document.documentElement.clientHeight - this.config.scrollTarget.scrollTop() < selectionContainerBottom,
                        selectionContainerWidth = this.$innerContainer.outerWidth(false) - parseInt(this.$selectionContainer.css('border-left-width'), 10) - parseInt(this.$selectionContainer.css('border-right-width'), 10);

                    if (displayContainerAboveInput) {
                        // position the popup above the input
                        selectionContainerYPos = this.$input.offset().top - selectionContainerHeight - this.config.scrollTarget.scrollTop() + parseInt(this.$selectionContainer.css('border-bottom-width'), 10);
                        this.$container
                            .removeClass('sol-selection-bottom')
                            .addClass('sol-selection-top');
                    } else {
                        this.$container
                            .removeClass('sol-selection-top')
                            .addClass('sol-selection-bottom');
                    }

                    if (this.$innerContainer.css('display') !== 'block') {
                        // container has a certain width
                        // make selection container a bit wider
                        selectionContainerWidth = selectionContainerWidth * 1.2;
                    } else {

                        var borderRadiusSelector = displayContainerAboveInput ? 'border-bottom-right-radius' : 'border-top-right-radius';

                        // no border radius on top
                        this.$selectionContainer
                            .css(borderRadiusSelector, 'initial');

                        if (this.$actionButtons) {
                            this.$actionButtons
                                .css(borderRadiusSelector, 'initial');
                        }
                    }

                    this.$selectionContainer
                        .css('top', Math.floor(selectionContainerYPos))
                        .css('left', Math.floor(this.$container.offset().left))
                        .css('width', selectionContainerWidth);

                    // remember the position
                    this.config.displayContainerAboveInput = displayContainerAboveInput;
                }
            },

            selectAllMaxItemsThreshold: 30,
            showSelectAll: function () {
                return this.config.multiple && this.config.selectAllMaxItemsThreshold && this.items && this.items.length <= this.config.selectAllMaxItemsThreshold;
            },

            useBracketParameters: false,
            multiple: undefined,
            /*
             * Modified 2016
             */
            resultsContainer: undefined,
            closeOnClick: false,
            showSelectionBelowList: false,
            allowNullSelection: false,
            scrollTarget: undefined,
            maxHeight: undefined,
            converter: undefined,
            asyncBatchSize: 300,
            searchTimeout: 300,
            maxShow: 0
        },

        // initialize the plugin
        init: function () {
            this.config = $.extend(true, {}, this.defaults, this.options, this.metadata);

            var originalName = this._getNameAttribute(),
                sol = this;

            if (!originalName) {
                this._showErrorLabel('name attribute is required');
                return;
            }

            // old IE does not support trim
            if (typeof String.prototype.trim !== 'function') {
                String.prototype.trim = function () {
                    return this.replace(/^\s+|\s+$/g, '');
                }
            }

            this.config.multiple = this.config.multiple || this.$originalElement.attr('multiple');

            if (!this.config.scrollTarget) {
                this.config.scrollTarget = $(window);
            }

            this._registerWindowEventsIfNeccessary();
            this._initializeUiElements();
            this._initializeInputEvents();

            setTimeout(function () {
                sol._initializeData();

                // take original form element out of form submission
                // by removing the name attribute
                sol.$originalElement
                    .data(sol.DATA_KEY, sol)
                    .removeAttr('name')
                    .data('sol-name', originalName);
            }, 0);

            this.$originalElement.hide();
            this.$container
                .css('visibility', 'initial')
                .show();

            return this;
        },

        _getNameAttribute: function () {
            return this.config.name || this.$originalElement.data('sol-name') || this.$originalElement.attr('name');
        },

        // shows an error label
        _showErrorLabel: function (message) {
            var $errorMessage = $('<div style="color: red; font-weight: bold;" />').html(message);
            if (!this.$container) {
                $errorMessage.insertAfter(this.$originalElement);
            } else {
                this.$container.append($errorMessage);
            }
        },

        // register click handler to determine when to trigger the close event
        _registerWindowEventsIfNeccessary: function () {
            if (!window[this.WINDOW_EVENTS_KEY]) {
                $(document).click(function (event) {
                    // if clicked inside a sol element close all others
                    // else close all sol containers

                    var $clickedElement = $(event.target),
                        $closestSelectionContainer = $clickedElement.closest('.sol-selection-container'),
                        $closestInnerContainer = $clickedElement.closest('.sol-inner-container'),
                        $clickedWithinThisSolContainer;

                    if ($closestInnerContainer.length) {
                        $clickedWithinThisSolContainer = $closestInnerContainer.first().parent('.sol-container');
                    } else if ($closestSelectionContainer.length) {
                        $clickedWithinThisSolContainer = $closestSelectionContainer.first().parent('.sol-container');
                    }

                    $('.sol-active')
                        .not($clickedWithinThisSolContainer)
                        .each(function (index, item) {
                            $(item)
                                .data(SearchableOptionList.prototype.DATA_KEY)
                                .close();
                        });
                });

                // remember we already registered the global events
                window[this.WINDOW_EVENTS_KEY] = true;
            }
        },

        // add sol ui elements
        _initializeUiElements: function () {
            var self = this;

            this.internalScrollWrapper = function () {
                if ($.isFunction(self.config.events.onScroll)) {
                    self.config.events.onScroll.call(self);
                }
            };

            this.$input = $('<input type="text"/>')
                .attr('placeholder', this.config.texts.searchplaceholder);
                
            this.$noResultsItem = $('<div class="sol-no-results"/>').html(this.config.texts.noItemsAvailable).hide();
            this.$loadingData = $('<div class="sol-loading-data"/>').html(this.config.texts.loadingData);
            this.$xItemsSelected = $('<div class="sol-results-count"/>');

            this.$caret = $('<div class="sol-caret-container"><b class="sol-caret"/></div>').click(function (e) {
                self.toggle();
                e.preventDefault();
                return false;
            });

            var $inputContainer = $('<div class="sol-input-container"/>').append(this.$input);
            this.$innerContainer = $('<div class="sol-inner-container"/>').append($inputContainer).append(this.$caret);
            this.$selection = $('<div class="sol-selection"/>');
            this.$selectionContainer = $('<div class="sol-selection-container"/>')
                .append(this.$noResultsItem)
                .append(this.$loadingData)
                .append(this.$selection);

            this.$container = $('<div class="sol-container"/>')
                .hide()
                /*
                 * Modified 2016
                 */
                .keydown(function (e) {
                    if (e.keyCode == 13) {
                        var concat = '';
                        $("#sbox #qtbl input[type='text']").each(function () {
                            concat += $.trim($(this).val());
                        });
                        if (e.keyCode == 13 && concat === '') {
                            // follow the project user's typed (may not exist)
                            if(self.$input.val() !== '') {
                                window.location = document.xrefPath + '/' + self.$input.val();
                                return false;
                            }
                            var $el = $(".keyboard-selection").first().find(".sol-checkbox")
                            // follow the actual project
                            if($el.length && $el.data('sol-item') &&
                                    $el.data('sol-item').label) {
                                window.location = document.xrefPath + 
                                                    '/' + 
                                                    $el.data('sol-item').label;
                                return false;
                            }
                            // follow first selected project
                            $el = $(".sol-selected-display-item").first()
                            if($el.length && $el.data('label')) {
                               window.location = document.xrefPath + '/' + $el.data('label');
                                return false;
                            }
                            return false;
                        }
                        return true;
                    }
                })
                .data(this.DATA_KEY, this)
                .append(this.$selectionContainer)
                .append(this.$innerContainer)
                .insertBefore(this.$originalElement);

            // add selected items display container
            this.$showSelectionContainer = $('<div class="sol-current-selection"/>');

            /*
             * Modified 2016
             */
            var $el = this.config.resultsContainer || this.$innerContainer
            if (this.config.resultsContainer) {
                this.$showSelectionContainer.appendTo($el);
            } else {
                if (this.config.showSelectionBelowList) {
                    this.$showSelectionContainer.insertAfter($el);
                } else {
                    this.$showSelectionContainer.insertBefore($el);
                }
            }
            
            // dimensions
            if (this.config.maxHeight) {
                this.$selection.css('max-height', this.config.maxHeight);
            }

            // detect inline css classes and styles
            var cssClassesAsString = this.$originalElement.attr('class'),
                cssStylesAsString = this.$originalElement.attr('style'),
                cssClassList = [],
                stylesList = [];

            if (cssClassesAsString && cssClassesAsString.length > 0) {
                cssClassList = cssClassesAsString.split(/\s+/);

                // apply css classes to $container
                for (var i = 0; i < cssClassList.length; i++) {
                    this.$container.addClass(cssClassList[i]);
                }
            }

            if (cssStylesAsString && cssStylesAsString.length > 0) {
                stylesList = cssStylesAsString.split(/\;/);

                // apply css inline styles to $container
                for (var i = 0; i < stylesList.length; i++) {
                    var splitted = stylesList[i].split(/\s*\:\s*/g);

                    if (splitted.length === 2) {

                        if (splitted[0].toLowerCase().indexOf('height') >= 0) {
                            // height property, apply to innerContainer instead of outer
                            this.$innerContainer.css(splitted[0].trim(), splitted[1].trim());
                        } else {
                            this.$container.css(splitted[0].trim(), splitted[1].trim());
                        }
                    }
                }
            }

            if (this.$originalElement.css('display') !== 'block') {
                this.$container.css('width', this._getActualCssPropertyValue(this.$originalElement, 'width'));
            }

            if ($.isFunction(this.config.events.onRendered)) {
                this.config.events.onRendered.call(this, this);
            }
        },

        _getActualCssPropertyValue: function ($element, property) {

            var domElement = $element.get(0),
                originalDisplayProperty = $element.css('display');

            // set invisible to get original width setting instead of translated to px
            // see https://bugzilla.mozilla.org/show_bug.cgi?id=707691#c7
            $element.css('display', 'none');

            if (domElement.currentStyle) {
                return domElement.currentStyle[property];
            } else if (window.getComputedStyle) {
                return document.defaultView.getComputedStyle(domElement, null).getPropertyValue(property);
            }

            $element.css('display', originalDisplayProperty);

            return $element.css(property);
        },

        _initializeInputEvents: function () {
            // form event
            var self = this,
                $form = this.$input.parents('form').first();
        
            if ($form && $form.length === 1 && !$form.data(this.WINDOW_EVENTS_KEY)) {
                var resetFunction = function () {
                    var $changedItems = [];

                    $form.find('.sol-option input').each(function (index, item) {
                        var $item = $(item),
                            initialState = $item.data('sol-item').selected;

                        if ($item.prop('checked') !== initialState) {
                            $item
                                .prop('checked', initialState)
                                .trigger('sol-change', true);
                            $changedItems.push($item);
                        }
                    });

                    if ($changedItems.length > 0 && $.isFunction(self.config.events.onChange)) {
                        self.config.events.onChange.call(self, self, $changedItems);
                    }
                };

                $form.on('reset', function (event) {
                    // unfortunately the reset event gets fired _before_
                    // the inputs are actually reset. The only possibility
                    // to overcome this is to set an interval to execute
                    // own scripts some time after the actual reset event

                    // before fields are actually reset by the browser
                    // needed to reset newly checked fields
                    resetFunction.call(self);

                    // timeout for selection after form reset
                    // needed to reset previously checked fields
                    setTimeout(function () {
                        resetFunction.call(self);
                    }, 100);
                });

                $form.data(this.WINDOW_EVENTS_KEY, true);
            }

            // text input events
            this.$input
                .focus(function () {
                    self.open();
                })
                .on('propertychange input', function (e) {
                    var valueChanged = true;
                    if (e.type=='propertychange') {
                        valueChanged = e.originalEvent.propertyName.toLowerCase()=='value';
                    }
                    if (valueChanged) {
                        if ($(this).data('timeout')) {
                            clearTimeout($(this).data('timeout'));
                        }
                        $(this).data('timeout', setTimeout(function () {
                            self._applySearchTermFilter();
                        }, self.config.searchTimeout))

                    }
                });

            // keyboard navigation
            this.$container
                .on('keydown', function (e) {
                    var keyCode = e.keyCode;

                    // event handling for keyboard navigation
                    // only when there are results to be shown
                    if (!self.$noResultsItem.is(':visible')) {

                        var $currentHighlightedOption,
                            $nextHighlightedOption,
                            directionValue,
                            preventDefault = false,
                            $allVisibleOptions = self.$selection.find('.sol-option:visible');

                        if (keyCode === 40 || keyCode === 38) {
                            // arrow up or down to select an item
                            self._setKeyBoardNavigationMode(true);
                            /*
                             * Modified 2016
                             */
                            $currentHighlightedOption = self.$selection.find('.sol-option.keyboard-selection')
                            $currentHighlightedOption.find("input[type='checkbox']").blur();
                            directionValue = (keyCode === 38) ? -1 : 1;   // negative for up, positive for down

                            var indexOfNextHighlightedOption = $allVisibleOptions.index($currentHighlightedOption) + directionValue;
                            if (indexOfNextHighlightedOption < 0) {
                                indexOfNextHighlightedOption = $allVisibleOptions.length - 1;
                            } else if (indexOfNextHighlightedOption >= $allVisibleOptions.length) {
                                indexOfNextHighlightedOption = 0;
                            }

                            $currentHighlightedOption.removeClass('keyboard-selection');
                            $nextHighlightedOption = $($allVisibleOptions[indexOfNextHighlightedOption])
                                .addClass('keyboard-selection');
                            /*
                             * Modified 2016
                             */
                            $nextHighlightedOption.find("input[type='checkbox']").focus()
                            
                            /*
                             * Modified 2016
                             */
                            //self.$selection.scrollTop(self.$selection.scrollTop() + $nextHighlightedOption.position().top);

                            preventDefault = true;
                        } else if (self.keyboardNavigationMode === true && keyCode === 32) {
                            // toggle current selected item with space bar
                            $currentHighlightedOption = self.$selection.find('.sol-option.keyboard-selection input');
                            $currentHighlightedOption
                                .trigger('change');
                            
                            preventDefault = true;
                        }

                        if (preventDefault) {
                            // dont trigger any events in the input
                            e.preventDefault();
                            return false;
                        }
                    }
                })
                .on('keyup', function (e) {
                    var keyCode = e.keyCode;

                    if (keyCode === 27) {
                        // escape key
                        if (self.keyboardNavigationMode === true) {
                            self._setKeyBoardNavigationMode(false);
                        } else if (self.$input.val() === '') {
                            // trigger closing of container
                            self.$caret.trigger('click');
                            self.$input.trigger('blur');
                        } else {
                            // reset input and result filter
                            self.$input.val('').trigger('input');
                        }
                    } else if (keyCode === 16 || keyCode === 17 || keyCode === 18 || keyCode === 20) {
                        // special events like shift and control
                        return;
                    }
                });
        },

        _setKeyBoardNavigationMode: function (keyboardNavigationOn) {

            if (keyboardNavigationOn) {
                // on
                this.keyboardNavigationMode = true;
                this.$selection.addClass('sol-keyboard-navigation');
            } else {
                // off
                this.keyboardNavigationMode = false;
                this.$selection.find('.sol-option.keyboard-selection')
                this.$selection.removeClass('sol-keyboard-navigation');
                this.$selectionContainer.find('.sol-option.keyboard-selection').removeClass('keyboard-selection');
                this.$selection.scrollTop(0);
            }
        },

        _applySearchTermFilter: function () {
            if (!this.items || this.items.length === 0) {
                return;
            }

            var searchTerm = this.$input.val(),
                lowerCased = (searchTerm || '').toLowerCase();

            // show previously filtered elements again
            this.$selectionContainer.find('.sol-filtered-search').removeClass('sol-filtered-search');
            this._setNoResultsItemVisible(false);

            if (lowerCased.trim().length > 0) {
                this._findTerms(this.items, lowerCased);
            }

            // call onScroll to position the popup again
            // important if showing popup above list
            if ($.isFunction(this.config.events.onScroll)) {
                this.config.events.onScroll.call(this);
            }
        },

        _findTerms: function (dataArray, searchTerm) {
            if (!dataArray || !$.isArray(dataArray) || dataArray.length === 0) {
                return;
            }

            var self = this,
                    amountOfUnfilteredItems = dataArray.length

            // reset keyboard navigation mode when applying new filter
            this._setKeyBoardNavigationMode(false);

            /*
             * Modified 2016
             * recursion was very slow (however good lookin')
             */
            for (var itemIndex = 0; itemIndex < dataArray.length; itemIndex++) {
                var item = dataArray[itemIndex];
                if (item.type === 'option') {
                    var $element = item.displayElement,
                            elementSearchableTerms = (item.label + ' ' + item.tooltip).trim().toLowerCase();

                    if (elementSearchableTerms.indexOf(searchTerm) === -1) {
                        $element.addClass('sol-filtered-search');
                        amountOfUnfilteredItems--;
                    }
                } else {
                    var amountOfUnfilteredChildren = item.children.length
                    for (var childrenIndex = 0; childrenIndex < item.children.length; childrenIndex++) {
                        var child = item.children[childrenIndex];
                        if (child.type === 'option') {
                            var $element = child.displayElement,
                                    elementSearchableTerms = (child.label + ' ' + child.tooltip).trim().toLowerCase();

                            if (elementSearchableTerms.indexOf(searchTerm) === -1) {
                                $element.addClass('sol-filtered-search');
                                amountOfUnfilteredChildren--;
                            }
                        }
                    }

                    if (amountOfUnfilteredChildren === 0) {
                        item.displayElement.addClass('sol-filtered-search');
                        amountOfUnfilteredItems--;
                    }
                }
            }

            this._setNoResultsItemVisible(amountOfUnfilteredItems === 0);
        },

        _initializeData: function () {
            if (!this.config.data) {
                this.items = this._detectDataFromOriginalElement();
            } else if ($.isFunction(this.config.data)) {
                this.items = this._fetchDataFromFunction(this.config.data);
            } else if ($.isArray(this.config.data)) {
                this.items = this._fetchDataFromArray(this.config.data);
            } else if (typeof this.config.data === (typeof 'a string')) {
                this._loadItemsFromUrl(this.config.data);
            } else {
                this._showErrorLabel('Unknown data type');
            }

            if (this.items) {
                // done right away -> invoke postprocessing
                this._processDataItems(this.items);
            }
        },

        _detectDataFromOriginalElement: function () {
            if (this.$originalElement.prop('tagName').toLowerCase() === 'select') {
                var self = this,
                    solData = [];

                $.each(this.$originalElement.children(), function (index, item) {
                    var $item = $(item),
                        itemTagName = $item.prop('tagName').toLowerCase(),
                        solDataItem;

                    if (itemTagName === 'option') {
                        solDataItem = self._processSelectOption($item);
                        if (solDataItem) {
                            solData.push(solDataItem);
                        }
                    } else if (itemTagName === 'optgroup') {
                        solDataItem = self._processSelectOptgroup($item);
                        if (solDataItem) {
                            solData.push(solDataItem);
                        }
                    } else {
                        self._showErrorLabel('Invalid element found in select: ' + itemTagName + '. Only option and optgroup are allowed');
                    }
                });
                return this._invokeConverterIfNeccessary(solData);
            } else if (this.$originalElement.data('sol-data')) {
                var solDataAttributeValue = this.$originalElement.data('sol-data');
                return this._invokeConverterIfNeccessary(solDataAttributeValue);
            } else {
                this._showErrorLabel('Could not determine data from original element. Must be a select or data must be provided as data-sol-data="" attribute');
            }
        },

        _processSelectOption: function ($option) {
            return $.extend({}, this.SOL_OPTION_FORMAT, {
                value: $option.val(),
                selected: $option.prop('selected'),
                disabled: $option.prop('disabled'),
                cssClass: $option.attr('class'),
                label: $option.html(),
                tooltip: $option.attr('title'),
                element: $option
            });
        },

        _processSelectOptgroup: function ($optgroup) {
            var self = this,
                solOptiongroup = $.extend({}, this.SOL_OPTIONGROUP_FORMAT, {
                    label: $optgroup.attr('label'),
                    tooltip: $optgroup.attr('title'),
                    disabled: $optgroup.prop('disabled'),
                    children: []
                }),
                optgroupChildren = $optgroup.children('option');

            $.each(optgroupChildren, function (index, item) {
                var $child = $(item),
                    solOption = self._processSelectOption($child);

                // explicitly disable children when optgroup is disabled
                if (solOptiongroup.disabled) {
                    solOption.disabled = true;
                }

                solOptiongroup.children.push(solOption);
            });

            return solOptiongroup;
        },

        _fetchDataFromFunction: function (dataFunction) {
            return this._invokeConverterIfNeccessary(dataFunction(this));
        },

        _fetchDataFromArray: function (dataArray) {
            return this._invokeConverterIfNeccessary(dataArray);
        },

        _loadItemsFromUrl: function (url) {
            var self = this;
            $.ajax(url, {
                success: function (actualData) {
                    self.items = self._invokeConverterIfNeccessary(actualData);
                    if (self.items) {
                        self._processDataItems(self.items);
                    }
                },
                error: function (xhr, status, message) {
                    self._showErrorLabel('Error loading from url ' + url + ': ' + message);
                },
                dataType: 'json'
            });
        },

        _invokeConverterIfNeccessary: function (dataItems) {
            if ($.isFunction(this.config.converter)) {
                return this.config.converter.call(this, this, dataItems);
            }
            return dataItems;
        },

        _processDataItems: function (solItems) {
            if (!solItems) {
                this._showErrorLabel('Data items not present. Maybe the converter did not return any values');
                return;
            }

            if (solItems.length === 0) {
                this._setNoResultsItemVisible(true);
                this.$loadingData.remove();
                return;
            }

            var self = this,
                nextIndex = 0,
                dataProcessedFunction = function () {
                    // hide "loading data"
                    this.$loadingData.remove();
                    this._initializeSelectAll();

                    if ($.isFunction(this.config.events.onInitialized)) {
                        this.config.events.onInitialized.call(this, this, solItems);
                    }
                },
                loopFunction = function () {

                    var currentBatch = 0,
                        item;
                    
                    while (currentBatch++ < self.config.asyncBatchSize && nextIndex < solItems.length) {
                        item = solItems[nextIndex++];
                        if (item.type === self.SOL_OPTION_FORMAT.type) {
                            self._renderOption(item);
                        } else if (item.type === self.SOL_OPTIONGROUP_FORMAT.type) {
                            self._renderOptiongroup(item);
                        } else {
                            self._showErrorLabel('Invalid item type found ' + item.type);
                            return;
                        }
                    }

                    if (nextIndex >= solItems.length) {
                        dataProcessedFunction.call(self);
                    } else {
                        setTimeout(loopFunction, 0);
                    }
                };

            // start async rendering of html elements
            loopFunction.call(this);
        },

        _renderOption: function (solOption, $optionalTargetContainer) {
            var self = this,
                $actualTargetContainer = $optionalTargetContainer || this.$selection,
                $inputElement,
               /*
                * Modified 2016
                */
                $labelText = $('<div class="sol-label-text"/>')
                        .html(solOption.label.trim().length === 0 ? '&nbsp;' : solOption.label)
                    .addClass(solOption.cssClass),
                $label,
                $displayElement,
                inputName = this._getNameAttribute();
            /*
             * Modified 2016
             */
            var data = $(solOption.element).data('messages');
            if (data && data.length) {
                $labelText.append(
                        $('<span>')
                        .addClass('pull-right important-note important-note-rounded')
                        .data("messages", data)
                        .attr('data-messages', '')
                        .text('!')
                        );
            }

            if (this.config.multiple) {
                // use checkboxes
                $inputElement = $('<input type="checkbox" class="sol-checkbox"/>');

                if (this.config.useBracketParameters) {
                    inputName += '[]';
                }
            } else {
                // use radio buttons
                $inputElement = $('<input type="radio" class="sol-radio"/>')
                    .on('change', function () {
                        // when selected notify all others of being deselected
                        self.$selectionContainer.find('input[type="radio"][name="' + inputName + '"]').not($(this)).trigger('sol-deselect');
                    })
                    .on('sol-deselect', function () {
                        // remove display selection item
                        // TODO also better show it inline instead of above or below to save space
                        self._removeSelectionDisplayItem($(this));
                    });
            }

            $inputElement
                .on('change', function (event, skipCallback) {
                    $(this).trigger('sol-change', skipCallback);
                })
                .on('sol-change', function (event, skipCallback) {
                    /*
                     * Modified 2016
                     */
                    var $closestOption = $(this).closest('.sol-option')
                    self._setKeyBoardNavigationMode(true)
                    self.$selection
                            .find('.sol-option.keyboard-selection')
                            .removeClass("keyboard-selection")

                    $closestOption.addClass('keyboard-selection')
                    //self.$selection.scrollTop(self.$selection.scrollTop() + $closestOption.position().top)
                    
                    self._selectionChange($(this), skipCallback);
                })
                .data('sol-item', solOption)
                .prop('checked', solOption.selected)
                .prop('disabled', solOption.disabled)
                .attr('name', inputName)
                .val(solOption.value);

            $label = $('<label class="sol-label"/>')
                .attr('title', solOption.tooltip)
                .append($inputElement)
                .append($labelText);
            /*
             * Modified 2016
             */
            $displayElement = $('<div class="sol-option"/>').dblclick(function (e) {
                var $el = $(this).find('.sol-checkbox');
                if ($el.length && $el.data('sol-item') && $el.data('sol-item').label) {
                    // go first project
                    window.location = document.xrefPath + '/' + $(this).find('.sol-checkbox').data('sol-item').label;
                }
            }).append($label);
            /*
             * Modified 2016
             */
            $inputElement.data('messages-available', data && data.length);

            solOption.displayElement = $displayElement;

            $actualTargetContainer.append($displayElement);
            
            if (solOption.selected) {
                this._addSelectionDisplayItem($inputElement);
            }
        },

        _renderOptiongroup: function (solOptiongroup) {
            var self = this,
                $groupCaption = $('<div class="sol-optiongroup-label"/>')
                    .attr('title', solOptiongroup.tooltip)
                    .html(solOptiongroup.label),
                $groupItem = $('<div class="sol-optiongroup"/>').append($groupCaption);

            if (solOptiongroup.disabled) {
                $groupItem.addClass('disabled');
            }
            /*
             * Modified 2016, 2017
             */
            $groupCaption.click(function (e) {
                // select all group
                if (self.config.multiple) {
                    if (!e.ctrlKey) {
                        self.deselectAll();
                    }
                    self.selectAll($(this).text())
                    self.$selection.scrollTop(self.$selection.scrollTop() + $(this).position().top)
                }
            });
            
            /*
             * Modified 2016
             */
            this.$selection.append($groupItem);

            if ($.isArray(solOptiongroup.children)) {
                $.each(solOptiongroup.children, function (index, item) {
                    self._renderOption(item, $groupItem);
                });
            }

            solOptiongroup.displayElement = $groupItem;
        },

        _initializeSelectAll: function () {
            // multiple values selectable
            if (this.config.showSelectAll === true || ($.isFunction(this.config.showSelectAll) && this.config.showSelectAll.call(this))) {
                // buttons for (de-)select all
                var self = this,
                    $deselectAllButton = $('<a href="#" class="sol-deselect-all"/>').html(this.config.texts.selectNone).click(function (e) {
                        self.deselectAll();
                        e.preventDefault();
                        return false;
                    }),
                    $selectAllButton = $('<a href="#" class="sol-select-all"/>').html(this.config.texts.selectAll).click(function (e) {
                        self.selectAll();
                        e.preventDefault();
                        return false;
                    });

                this.$actionButtons = $('<div class="sol-action-buttons"/>').append($selectAllButton).append($deselectAllButton).append('<div class="sol-clearfix"/>');
                this.$selectionContainer.prepend(this.$actionButtons);
            }
        },

        _selectionChange: function ($changeItem, skipCallback) {

            // apply state to original select if neccessary
            // helps to keep old legacy code running which depends
            // on retrieving the value via jQuery option selectors
            // e.g. $('#myPreviousSelectWhichNowIsSol').val()
            if (this.$originalElement && this.$originalElement.prop('tagName').toLowerCase() === 'select') {
                var self = this;
                this.$originalElement.find('option').each(function (index, item) {
                    var $currentOriginalOption = $(item);
                    if ($currentOriginalOption.val() === $changeItem.val()) {
                        $currentOriginalOption.prop('selected', $changeItem.prop('checked'));
                        self.$originalElement.trigger('change');
                        return;
                    }
                });
            }

            if ($changeItem.prop('checked')) {
                this._addSelectionDisplayItem($changeItem);
            } else {
                this._removeSelectionDisplayItem($changeItem);
            }

            if (this.config.multiple) {
                // update position of selection container
                // to allow selecting more entries
                this.config.scrollTarget.trigger('scroll');
            } else {
                // only one option selectable
                // close selection container
                this.close();
            }

            if (!skipCallback && $.isFunction(this.config.events.onChange)) {
                this.config.events.onChange.call(this, this, $changeItem);
            }
        },

        _addSelectionDisplayItem: function ($changedItem) {
            var solOptionItem = $changedItem.data('sol-item'),
                $existingDisplayItem = solOptionItem.displaySelectionItem,
                $displayItemText;

            if (!$existingDisplayItem) {
                /*
                 * Modified 2016
                 */
                var selected = this.$showSelectionContainer.children('.sol-selected-display-item');
                var label = solOptionItem.label
                if ($changedItem.data('messages-available')) {
                    label += ' <span class="important-note important-note-rounded" ';
                    label += 'title="Some message is important in this project.';
                    label += ' Find more info in the project list below.">!</span>'
                }

                $displayItemText = $('<span class="sol-selected-display-item-text" />').html(label);
                $existingDisplayItem = $('<div class="sol-selected-display-item"/>')
                    .append($displayItemText)
                    .attr('title', solOptionItem.tooltip)
                    .data('label', solOptionItem.label)
                    .appendTo(this.$showSelectionContainer);

                // show remove button on display items if not disabled and null selection allowed
                if ((this.config.multiple || this.config.allowNullSelection) && !$changedItem.prop('disabled')) {
                    $('<span class="sol-quick-delete"/>')
                        .html(this.config.texts.quickDelete)
                        .click(function () {
                            $changedItem
                                .prop('checked', false)
                                .trigger('change');
                        })
                        .prependTo($existingDisplayItem);
                }
                /*
                 * Modified 2016
                 */
                if (this.config.maxShow != 0 && selected.length + 1 > this.config.maxShow) {
                    var xitemstext = this.config.texts.itemsSelected.replace('{$a}', selected.length + 1 - this.config.maxShow);
                    this.$xItemsSelected.html('<div class="sol-selected-display-item-text">' + xitemstext + '<div>');
                    this.$showSelectionContainer.append(this.$xItemsSelected);
                    this.$xItemsSelected.show();
                    $existingDisplayItem.hide();
                }
                
                solOptionItem.displaySelectionItem = $existingDisplayItem;
            }
        },

        _removeSelectionDisplayItem: function ($changedItem) {
            var solOptionItem = $changedItem.data('sol-item'),
                $myDisplayItem = solOptionItem.displaySelectionItem;

            if ($myDisplayItem) {
                /*
                 * Modified 2016
                 */
                var selected = this.$showSelectionContainer.children('.sol-selected-display-item');
                if (this.config.maxShow != 0 && selected.length - 1 > this.config.maxShow) {
                    var xitemstext = this.config.texts.itemsSelected.replace('{$a}', selected.length - 1 - this.config.maxShow);
                    this.$xItemsSelected.html('<div class="sol-selected-display-item-text">' + xitemstext + '<div>');
                    this.$showSelectionContainer.append(this.$xItemsSelected);
                    this.$xItemsSelected.show();
                } else {
                    this.$xItemsSelected.hide();
                }

                if ($myDisplayItem.is(":visible")) {
                    $myDisplayItem
                            .siblings('.sol-selected-display-item')
                            .not(":visible")
                            .not(this.$xItemsSelected)
                            .first()
                            .show();
                }
                
                $myDisplayItem.remove();
                solOptionItem.displaySelectionItem = undefined;
            }
        },

        _setNoResultsItemVisible: function (visible) {
            if (visible) {
                this.$noResultsItem.show();
                this.$selection.hide();

                if (this.$actionButtons) {
                    this.$actionButtons.hide();
                }
            } else {
                this.$noResultsItem.hide();
                this.$selection.show();

                if (this.$actionButtons) {
                    this.$actionButtons.show();
                }
            }
        },

        isOpen: function () {
            return this.$container.hasClass('sol-active');
        },

        isClosed: function () {
            return !this.isOpen();
        },

        toggle: function () {
            if (this.isOpen()) {
                this.close();
            } else {
                this.open();
            }
        },

        open: function () {
            if (this.isClosed()) {
                this.$container.addClass('sol-active');
                this.config.scrollTarget.bind('scroll', this.internalScrollWrapper).trigger('scroll');
                $(window).on('resize', this.internalScrollWrapper);

                if ($.isFunction(this.config.events.onOpen)) {
                    this.config.events.onOpen.call(this, this);
                }
            }
        },

        close: function () {
            if (this.isOpen()) {
                this._setKeyBoardNavigationMode(false);


                this.$container.removeClass('sol-active');
                this.config.scrollTarget.unbind('scroll', this.internalScrollWrapper);
                $(window).off('resize');

                // reset search on close
                this.$input.val('');
                this._applySearchTermFilter();

                // clear to recalculate position again the next time sol is opened
                this.config.displayContainerAboveInput = undefined;

                if ($.isFunction(this.config.events.onClose)) {
                    this.config.events.onClose.call(this, this);
                }
            }
        },
        /*
         * Modified 2016
         */
        selectAll: function (/* string or undefined */optgroup) {
            if (this.config.multiple) {
                var $changedInputs = !optgroup ? this.$selectionContainer
                        : this.$selectionContainer
                        .find(".sol-optiongroup-label")
                        .filter(function () {
                            return $(this).text() === optgroup;
                        }).closest('.sol-optiongroup')

                $changedInputs = $changedInputs.find('input[type="checkbox"]:not([disabled], :checked)')
                            .prop('checked', true)
                            .trigger('change', true);

                this.config.closeOnClick && this.close();

                if ($.isFunction(this.config.events.onChange)) {
                    this.config.events.onChange.call(this, this, $changedInputs);
                }
            }
        },
        /*
         * Modified 2016
         */
        invert: function() {
            if (this.config.multiple) {
                var $closedInputs = this.$selectionContainer
                    .find('input[type="checkbox"]:not([disabled], :checked)')
                var $openedInputs = this.$selectionContainer
                    .find('input[type="checkbox"]').filter('[disabled], :checked')
            
                $openedInputs.prop('checked', false)
                             .trigger('change', true);
                $closedInputs.prop('checked', true)
                             .trigger('change', true)

                this.config.closeOnClick && this.close();

                if ($.isFunction(this.config.events.onChange)) {
                    this.config.events.onChange.call(this, this, $openedInputs.add($closedInputs));
                }
            }
        },
        /*
         * Modified 2016
         */
        deselectAll: function ( /* string or undefined */ optgroup) {
            if (this.config.multiple) {
                var $changedInputs = !optgroup ? this.$selectionContainer
                        : this.$selectionContainer
                        .find(".sol-optiongroup-label")
                        .filter(function () {
                            return $(this).text() === optgroup;
                        }).closest('.sol-optiongroup')

                $changedInputs = $changedInputs.find('.sol-option input[type="checkbox"]:not([disabled]):checked')
                            .prop('checked', false)
                            .trigger('change', true);

                this.options.closeOnClick && this.close();

                if ($.isFunction(this.config.events.onChange)) {
                    this.config.events.onChange.call(this, this, $changedInputs);
                }
            }
        },

        getSelection: function () {
            return this.$selection.find('input:checked');
        }
    };

    // jquery plugin boiler plate code
    SearchableOptionList.defaults = SearchableOptionList.prototype.defaults;
    window.SearchableOptionList = SearchableOptionList;

    $.fn.searchableOptionList = function (options) {
        var result = [];
        this.each(function () {
            var $this = $(this),
                $alreadyInitializedSol = $this.data(SearchableOptionList.prototype.DATA_KEY);

            if ($alreadyInitializedSol) {
                result.push($alreadyInitializedSol);
            } else {
                var newSol = new SearchableOptionList($this, options);
                result.push(newSol);

                setTimeout(function() {
                    newSol.init();
                }, 0);
            }
        });

        if (result.length === 1) {
            return result[0];
        }

        return result;
    };

}(jQuery, window, document));
