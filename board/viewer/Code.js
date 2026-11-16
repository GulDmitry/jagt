import {useEffect, useRef, useState} from 'preact/hooks';
import {api} from '../core/api.js';
import {t} from '../core/i18n.js';
import {html} from '../components/html.js';
import {reveal, show} from './codemirror.js';

document.head.append(Object.assign(document.createElement('link'), {rel: 'stylesheet', href: new URL('./code.css', import.meta.url).href}));

const firstOf = (trees) => {
  const tree = trees.find((each) => each.files.length);
  if (!tree) return null;
  return {project: tree.project, path: tree.files.find((name) => /^readme/i.test(name)) || tree.files[0], line: 0};
};

const SAID = {BINARY: 'code.binary', TOO_LARGE: 'code.large'};

export const Code = ({task}) => {
  const [trees, setTrees] = useState(null);
  const [picked, setPicked] = useState(null);
  const [file, setFile] = useState(null);
  const [found, setFound] = useState(null);
  const [failure, setFailure] = useState('');
  const host = useRef(null);
  const id = encodeURIComponent(task.id);
  const said = (e) => setFailure(e.message);

  useEffect(() => {
    api(`/api/tasks/code/tree?task=${id}`).then((read) => {
      setTrees(read);
      setPicked(firstOf(read));
    }, said);
  }, [task.id]);

  useEffect(() => {
    if (!picked) return;
    setFile(null);
    api(`/api/tasks/code/file?task=${id}&project=${encodeURIComponent(picked.project)}&path=${encodeURIComponent(picked.path)}`)
      .then((read) => { setFile(read); setFailure(''); }, said);
  }, [picked?.project, picked?.path]);

  useEffect(() => {
    if (file?.kind !== 'TEXT') return undefined;
    let gone = false;
    let stop = null;
    show(host.current, file.text, file.path).then((close) => {
      if (gone) return close();
      stop = close;
      return picked.line ? reveal(host.current, picked.line) : undefined;
    }, said);
    return () => {
      gone = true;
      if (stop) stop();
    };
  }, [file]);

  const search = (event) => {
    event.preventDefault();
    const q = event.target.elements.q.value.trim();
    if (!q) return setFound(null);
    return api(`/api/tasks/code/search?task=${id}&project=${encodeURIComponent(picked?.project || '')}&q=${encodeURIComponent(q)}`)
      .then(setFound, said);
  };

  const project = picked?.project || '';
  return html`<section class="code" role="tabpanel" id="panel-code" aria-labelledby="tab-code">
    <form class="code-search" onSubmit=${search}>
      <input name="q" aria-label=${t('code.search')} placeholder=${t('code.search')} autocomplete="off"/>
    </form>
    ${failure ? html`<p class="detail problem">${failure}</p>` : ''}
    <div class="code-body">
      <div class="code-names">${found
        ? (found.hits.length ? found.hits.map((hit) => html`<button onClick=${() => setPicked({project, path: hit.path, line: hit.line})}
          aria-pressed=${String(picked?.path === hit.path && picked?.line === hit.line)}>${hit.path}:${hit.line}
          <span class="muted">${hit.text.trim()}</span></button>`) : html`<p class="muted">${t('code.no-hits')}</p>`)
        : trees === null ? (failure ? '' : html`<p class="muted">${t('reading')}</p>`)
          : !picked ? html`<p class="muted">${t('code.none')}</p>`
            : trees.flatMap((tree) => tree.files.map((name) => html`<button
              onClick=${() => setPicked({project: tree.project, path: name, line: 0})}
              aria-pressed=${String(picked.project === tree.project && picked.path === name)}>${name}</button>`))}
        ${found?.cut ? html`<p class="muted">${t('code.cut')}</p>` : ''}</div>
      <div class="code-file">
        ${file && file.kind !== 'TEXT' ? html`<p class="muted">${t(SAID[file.kind])}</p>` : ''}
        <div class="code-view" ref=${host}></div>
      </div>
    </div>
  </section>`;
};
