// ==UserScript==
// @name         server:bilibili 移动端
// @namespace    https://github.com/jk278/bilibili-mobile
// @version      5.2.3.1
// @author       jk278
// @description  Safari打开电脑模式，其它浏览器关闭电脑模式修改网站UA，获取舒适的移动端体验。
// @license      MIT
// @icon         https://www.bilibili.com/favicon.ico
// @match        https://*.bilibili.com/*
// @exclude      https://message.bilibili.com/pages/nav/*
// @exclude      https://www.bilibili.com/blackboard/comment-detail.html?*
// @require      https://unpkg.com/js-md5@latest/src/md5.js
// @grant        GM.addElement
// @grant        GM.addStyle
// @grant        GM.deleteValue
// @grant        GM.getResourceUrl
// @grant        GM.getValue
// @grant        GM.info
// @grant        GM.listValues
// @grant        GM.notification
// @grant        GM.openInTab
// @grant        GM.registerMenuCommand
// @grant        GM.setClipboard
// @grant        GM.setValue
// @grant        GM.xmlHttpRequest
// @grant        GM_addElement
// @grant        GM_addStyle
// @grant        GM_addValueChangeListener
// @grant        GM_cookie
// @grant        GM_deleteValue
// @grant        GM_download
// @grant        GM_getResourceText
// @grant        GM_getResourceURL
// @grant        GM_getTab
// @grant        GM_getTabs
// @grant        GM_getValue
// @grant        GM_info
// @grant        GM_listValues
// @grant        GM_log
// @grant        GM_notification
// @grant        GM_openInTab
// @grant        GM_registerMenuCommand
// @grant        GM_removeValueChangeListener
// @grant        GM_saveTab
// @grant        GM_setClipboard
// @grant        GM_setValue
// @grant        GM_unregisterMenuCommand
// @grant        GM_webRequest
// @grant        GM_xmlhttpRequest
// @grant        unsafeWindow
// @grant        window.close
// @grant        window.focus
// @grant        window.onurlchange
// @run-at       document-start
// ==/UserScript==

;(({ entrySrc = `` }) => {
  window.GM;
  const key = `__monkeyWindow-` + new URL(entrySrc).origin;
  document[key] = window;
  console.log(`[vite-plugin-monkey] mount monkeyWindow to document`);
  const entryScript = document.createElement("script");
  entryScript.type = "module";
  entryScript.src = entrySrc;
  document.head.insertBefore(entryScript, document.head.firstChild);
  console.log(`[vite-plugin-monkey] mount entry module to document.head`);
})(...[
  {
    "entrySrc": "http://127.0.0.1:5173/__vite-plugin-monkey.entry.js"
  }
]);

