(() => {
  const text = s => new Text(s);

  const dom = (tag, attrs, contents) => {
    const el = document.createElement(tag);
    for (const attr in attrs) {
      if (attr.startsWith('on')) {
        el.addEventListener(attr.slice(2), attrs[attr]);
      } else {
        el.setAttribute(attr, attrs[attr]);
      }
    }
    contents.forEach(c => el.appendChild((c instanceof Node || c instanceof Element) ? c : text(c)));
    return el;
  }

  const supify = s => {
    if (s.match(/\^/)) {
      const el = dom('span', {}, []);
      for (const match of s.matchAll(/\^(\d+) ([^^]*)/g)) {
        el.appendChild(dom('sup', {}, [text(match[1])]));
        el.appendChild(text(match[2]));
      }
      return el;
    } else {
      return text(s);
    }
  }

  const createTag = ([tag, contents, opts]) => {
    const domContents = (contents || []).map(content =>
      Array.isArray(content) ? createTag(content) : supify(content));
    return tag === 'h1' ? dom('h1', {onclick: () => matchingId(opts)}, domContents)
      : tag === 'a' ? dom('a', {href: opts.startsWith('http') ? opts : null, onclick: e => opts.startsWith('http') ? null : [e.preventDefault(), matchingId(opts)]}, domContents)
      : tag === 'tags' ? dom('p', {}, contents.map(tag => dom('a', {'class': 'tag', onclick: () => matchingTag(tag)}, [text(tag)])))
      : dom(tag, {}, domContents)
  }

  const createCard = card =>
    dom('section', {'class': 'truncated'},
      card.map(c => createTag(c)).concat([
        dom('a', {
          class: 'btn-link show-more',
          onclick: e => e.target.parentNode.classList.remove('truncated')
        }, ['show more']),
        dom('a', {
          class: 'btn-link show-less',
          onclick: e => e.target.parentNode.classList.add('truncated')
        }, ['show less'])]));

  const history = [];

  const setResults = cards => {
    history.push(cards);
    document.getElementById('results').replaceChildren(...cards.slice(0, 20).map(createCard));
    window.scroll(0, 0);
  }

  window.previous = () => {
    history.pop();
    const cards = history[history.length - 1];
    if (cards) {
      document.getElementById('results').replaceChildren(...cards.map(createCard));
    } else {
      document.getElementById('results').replaceChildren();
    }
  };

  const hasTag = (contents, tag) =>
    contents.some(c => Array.isArray(c) && c[0] === 'tags' && c[1].some(t => t === tag));

  let cards = (x => x ? JSON.parse(x) : null)(localStorage.getItem('zettelkasten'));

  const matchingId = id => setResults([cards[id]]);

  const matchingTag = tag =>
    setResults(Object.values(cards).filter(c => hasTag(c, tag)));

  window.matchingText = q => {
    const regexes = q.split(/\s+/).map(w => new RegExp(w, 'i'));
    setResults(Object.values(cards).filter(c => regexes.every(r => r.test(c))));
  }

  window.reset = () => {
    const input = document.querySelector('#controls input');
    input.value = null;
    input.focus();
    setResults([]);
  }

  window.load = () =>
    fetch('/cards.json')
      .then(r => r.json())
      .then(cs => {
        localStorage.setItem('zettelkasten', JSON.stringify(cs));
        cards = cs;
      });

  if (cards === null) window.load();
})();
