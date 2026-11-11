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
 * Markdown UI regression checks with the same renderer versions as the webapp.
 * Install test dependencies outside the checkout, then run:
 * npm install --prefix /tmp/opengrok-markdown-tests --no-save --ignore-scripts jquery@3.6.4 jsdom@20 showdown@2.1.0 xss@1.0.10
 * NODE_PATH=/tmp/opengrok-markdown-tests/node_modules node opengrok-web/src/test/js/markdown-preview.test.js
 */
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const { JSDOM } = require('jsdom');
const jquery = require('jquery');
const showdown = require('showdown');
const filterXSS = require('xss');
const source = fs.readFileSync(path.join(__dirname, '../../main/webapp/js/utils-0.0.51.js'), 'utf8');
const renderer = source.slice(source.indexOf('let markdownRenderer;'), source.indexOf('\nwindow.onload'));
const markdown = '# Heading\n\n| A | B |\n| --- | --- |\n| one | two |\n\n'
    + '- [x] Done\n- [ ] Pending\n\n~~removed~~\n\n```js\nconst x = 1;\n```\n\n'
    + '![diagram](images/test.png)\n\n[Other](other.md)\n\n'
    + '<img src="x" onerror="alert(1)"><script>alert(1)</script>\n\n'
    + '<a href="javascript:alert(1)">bad link</a>';

function setup(html, failFirstScript = false) {
    if (!html.includes('id="bar"')) {
        html = '<div id="bar"><ul><li><a href="/source/"><span id="home"></span>Home</a></li></ul></div>' + html;
    }
    const dom = new JSDOM(html, { url: 'https://example.org/source/xref/project/docs/guide.md?r=abc',
        runScripts: 'outside-only' });
    const { window } = dom;
    const $ = jquery(window);
    const requests = [];
    const scripts = [];
    window.$ = $;
    window.showdown = showdown;
    window.filterXSS = filterXSS;
    window.jQuery = $;
    window.contextPath = '/source';
    $.ajax = options => {
        const deferred = $.Deferred();
        if (options.dataType === 'script') {
            scripts.push(options.url);
            window.setTimeout(() => {
                if (failFirstScript) {
                    failFirstScript = false;
                    deferred.reject();
                } else {
                    deferred.resolve();
                }
            }, 0);
        } else {
            requests.push({ options, deferred });
        }
        return deferred.promise();
    };
    const loaderStart = source.indexOf('(function (window, document, $) {',
        source.indexOf('* General on-demand script downloader'));
    const loaderEnd = source.indexOf('})(window, document, jQuery);', loaderStart)
        + '})(window, document, jQuery);'.length;
    window.eval(source.slice(loaderStart, loaderEnd));
    window.eval(renderer);
    window.init_markdown_converter();
    window.init_file_preview();
    return { dom, $, requests, scripts };
}
async function until(predicate) {
    for (let i = 0; i < 100; i++) {
        if (predicate()) { return; }
        await new Promise(resolve => setTimeout(resolve, 5));
    }
    throw new Error('Timed out waiting for Markdown UI');
}
function checkContent($, selector) {
    const $content = $(selector);
    assert.strictEqual($content.find('h1').text(), 'Heading');
    assert.strictEqual($content.find('table td').length, 2);
    assert.strictEqual($content.find('del').text(), 'removed');
    assert($content.find('pre code').text().includes('const x = 1;'));
    assert.strictEqual($content.find('input[type="checkbox"]').length, 2);
    assert($content.find('input').toArray().every(input => input.disabled));
    assert($content.find('input').first().prop('checked'));
    assert.strictEqual($content.find('script, [onerror], [onclick]').length, 0);
    assert(!$content.find('a').toArray().some(a => /^javascript:/i.test(a.getAttribute('href') || '')));
    assert.strictEqual($content.find('a').filter((i, a) => a.textContent === 'Other').attr('href'), './other.md');
}
async function test() {
    const ui = setup('<div id="bar"><ul><li><a href="/source/"><span id="home"></span>Home</a></li>'
        + '<li><a id="raw-link" href="/source/raw/project/docs/guide.md?r=abc">Raw</a></li></ul></div>'
        + '<div id="file-preview-tabs" hidden>'
        + '<button id="file-preview-tab" data-preview-download="/source/download/project/docs/guide.md?r=abc">'
        + '</button></div><div id="src"><pre>original source</pre></div>'
        + '<div data-markdown><div class="markdown-content" data-markdown-download="/source/download/project/docs/README.md">'
        + '</div><pre data-markdown-original>fallback</pre></div>');
    const { $, requests, scripts } = ui;
    assert.strictEqual($('#file-preview-tabs').prop('hidden'), false);
    assert.strictEqual($('#file-preview').prop('hidden'), true);
    assert.strictEqual($('#file-preview-menu .preview-home').attr('href'), '/source/');
    assert.strictEqual($('#file-preview-menu #file-source-tab').length, 1);
    await until(() => requests.length === 1);
    requests[0].deferred.resolve(markdown);
    await until(() => $('[data-markdown]').hasClass('markdown'));
    checkContent($, '[data-markdown] .markdown-content');
    assert.strictEqual($('[data-markdown-original]').css('display'), 'none');
    assert.strictEqual($('[data-markdown] img').first().attr('src'),
        'https://example.org/source/download/project/docs/images/test.png');
    $('#file-preview-tab').trigger('click').trigger('click');
    await until(() => requests.length === 2);
    assert.strictEqual($('#src').css('display'), 'none');
    assert.strictEqual($('#bar > ul').first().prop('hidden'), true);
    assert.strictEqual($('#file-preview-menu').prop('hidden'), false);
    assert.deepStrictEqual($('#file-preview-menu a').toArray().map(a => a.textContent), ['Home', '|Source']);
    assert.strictEqual(requests[1].options.url, '/source/download/project/docs/guide.md?r=abc');
    $('#file-source-tab').trigger('click');
    requests[1].deferred.resolve(markdown);
    await until(() => $('#file-preview .markdown-content h1').length === 1);
    assert.strictEqual($('#file-preview').prop('hidden'), true);
    assert.notStrictEqual($('#src').css('display'), 'none');
    assert.strictEqual($('#bar > ul').first().prop('hidden'), false);
    assert.strictEqual($('#file-preview-menu').prop('hidden'), true);
    $('#file-preview-tab').trigger('click');
    checkContent($, '#file-preview');
    assert($('#file-preview').hasClass('markdown'));
    assert.strictEqual($('#file-preview .markdown-content pre').length, 1);
    assert.strictEqual($('#file-preview img').first().attr('src'),
        'https://example.org/source/download/project/docs/images/test.png?r=abc');
    assert.strictEqual($('#file-preview-tab').attr('aria-pressed'), 'true');
    assert.strictEqual($('#src pre').text(), 'original source');
    assert.strictEqual($('#raw-link').attr('href'), '/source/raw/project/docs/guide.md?r=abc');
    assert.strictEqual($._data($('#raw-link')[0], 'events'), undefined);
    assert.strictEqual(requests.length, 2);
    assert.strictEqual(scripts.length, 2);
    ui.dom.window.close();

    const failure = setup('<div id="file-preview-tabs">'
        + '<button id="file-preview-tab" data-preview-download="/source/download/project/guide.md"></button>'
        + '</div><div id="src">original</div><div data-markdown><div class="markdown-content" '
        + 'data-markdown-download="/source/download/project/README.md"></div>'
        + '<pre data-markdown-original>README fallback</pre></div>');
    await until(() => failure.requests.length === 1);
    failure.requests[0].deferred.reject();
    failure.$('#file-preview-tab').trigger('click');
    await until(() => failure.requests.length === 2);
    failure.requests[1].deferred.reject();
    await until(() => failure.$('[role="status"]').text().includes('Unable'));
    assert.notStrictEqual(failure.$('[data-markdown-original]').css('display'), 'none');
    failure.$('[role="status"] a').trigger('click');
    await until(() => failure.requests.length === 3);
    failure.requests[2].deferred.resolve('# Retried');
    await until(() => failure.$('#file-preview h1').text() === 'Retried');
    failure.$('#file-source-tab').trigger('click');
    assert.strictEqual(failure.$('#file-preview').prop('hidden'), true);
    failure.dom.window.close();
    const ordinary = setup('<div id="file-preview-tabs" hidden><a id="file-preview-tab"'
        + ' data-preview-download="/source/download/project/code.java">Preview</a></div>'
        + '<div id="src">plain source</div>');
    assert.strictEqual(ordinary.$('#file-preview-tabs').prop('hidden'), true);
    assert.strictEqual(ordinary.$('#file-preview').length, 0);
    assert.strictEqual(ordinary.scripts.length, 0);
    ordinary.dom.window.close();

    const scriptFailure = setup('<div id="file-preview-tabs">'
        + '<button id="file-preview-tab" data-preview-download="/source/download/guide.md"></button>'
        + '</div><div id="src">original</div>', true);
    scriptFailure.$('#file-preview-tab').trigger('click');
    await until(() => scriptFailure.$('[role="status"]').text().includes('Unable'));
    scriptFailure.$('[role="status"] a').trigger('click');
    await until(() => scriptFailure.requests.length === 1);
    scriptFailure.requests[0].deferred.resolve('# Library retry');
    await until(() => scriptFailure.$('#file-preview h1').text() === 'Library retry');
    assert.strictEqual(scriptFailure.scripts.length, 3);
    scriptFailure.dom.window.close();
    // The real JSP keeps existing actions in place and appends Preview after Download.
    const menu = fs.readFileSync(path.join(__dirname, '../../main/webapp/minisearch.jspf'), 'utf8');
    assert(menu.indexOf('id="raw"') < menu.indexOf('id="download"'));
    assert(menu.indexOf('id="download"') < menu.indexOf('id="file-preview-tabs"'));

    for (const extension of ['html', 'HTM', 'HTML']) {
        const url = '/source/download/project/docs/page.' + extension + '?r=abc';
        const html = setup('<div id="file-preview-tabs" hidden><a id="file-preview-tab"'
            + ' data-preview-download="' + url + '">Preview</a></div><div id="src">HTML source</div>');
        const $h = html.$;
        assert.strictEqual($h('#file-preview-tabs').prop('hidden'), false);
        $h('#file-preview-tab').trigger('click').trigger('click');
        assert.strictEqual(html.requests.length, 1);
        assert.strictEqual(html.requests[0].options.url, url);
        assert.strictEqual($h('#file-preview-menu').prop('hidden'), false);
        assert.strictEqual($h('#file-preview-menu .preview-home').attr('href'), '/source/');
        html.requests[0].deferred.reject();
        await until(() => $h('[role="status"]').text().includes('Unable'));
        $h('[role="status"] a').trigger('click');
        assert.strictEqual(html.requests.length, 2);
        html.requests[1].deferred.resolve('<!doctype html><html><head><base href="https://other.example/">'
            + '<style>h1 { color: red; }</style></head><body><h1>HTML heading</h1>'
            + '<img src="images/test.png"><script>parent.document.body.remove()</script></body></html>');
        await until(() => $h('iframe').length === 1);
        const frame = $h('iframe')[0];
        assert.strictEqual(frame.getAttribute('sandbox'), '');
        assert.strictEqual(frame.title, 'HTML preview');
        const rendered = new JSDOM(frame.srcdoc);
        assert.strictEqual(rendered.window.document.querySelectorAll('base').length, 1);
        assert.strictEqual(rendered.window.document.querySelector('base').href, 'https://example.org' + url);
        assert.strictEqual(rendered.window.document.querySelector('h1').textContent, 'HTML heading');
        assert.strictEqual(rendered.window.document.querySelector('img').src,
            'https://example.org/source/download/project/docs/images/test.png');
        assert.strictEqual($h('#src').text(), 'HTML source');
        assert.strictEqual($h('#file-preview > h1, #file-preview script').length, 0);
        $h('#file-source-tab').trigger('click');
        assert.strictEqual($h('#file-preview').prop('hidden'), true);
        assert.strictEqual($h('#bar > ul').first().prop('hidden'), false);
        $h('#file-preview-tab').trigger('click');
        assert.strictEqual(html.requests.length, 2);
        assert.strictEqual(html.scripts.length, 0);
        rendered.window.close();
        html.dom.window.close();
    }
    console.log('File preview checks passed (Markdown, sandboxed HTML, menu order, supported types, revisions, caching and retry).');
}
test().catch(error => { console.error(error); process.exitCode = 1; });
