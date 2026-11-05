// Mobile nav toggle, BBCode toolbar and confirm dialogs.
document.addEventListener('click', function (e) {
  var t = e.target.closest('[data-toggle="nav"]');
  if (t) { document.getElementById('main-nav').classList.toggle('open'); return; }
  var bb = e.target.closest('[data-bbcode]');
  if (bb) {
    e.preventDefault();
    var ta = document.getElementById(bb.getAttribute('data-target') || 'message');
    if (!ta) return;
    var tag = bb.getAttribute('data-bbcode');
    var open = '[' + tag + ']', close = '[/' + tag.split('=')[0] + ']';
    if (tag === 'smilie') { open = bb.getAttribute('data-code') + ' '; close = ''; }
    var s = ta.selectionStart, en = ta.selectionEnd, v = ta.value;
    ta.value = v.substring(0, s) + open + v.substring(s, en) + close + v.substring(en);
    ta.focus();
    ta.selectionStart = ta.selectionEnd = en + open.length + close.length;
    return;
  }
  var c = e.target.closest('[data-confirm]');
  if (c && !window.confirm(c.getAttribute('data-confirm'))) { e.preventDefault(); }
});
