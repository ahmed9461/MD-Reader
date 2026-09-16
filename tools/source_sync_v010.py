from pathlib import Path

path = Path('app/src/main/assets/reader.html')
s = path.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global s
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'Expected renderer block not found: {label}')
    s = s.replace(old, new, 1)


replace_once(
    '.task-item.task-checked{background:var(--success-bg);font-weight:700;color:var(--success);box-shadow:inset 4px 0 0 var(--success-border)}\n'
    '[dir="rtl"] .task-item.task-checked{box-shadow:inset -4px 0 0 var(--success-border)}\n',
    '.task-item.task-checked{background:var(--success-bg);font-weight:700;color:var(--success);border-inline-start:4px solid var(--success-border)}\n',
    'checked task directional border',
)

replace_once(
    '.task-item.correct-answer{border-inline-start:0;padding:.18em .35em}\n',
    '.task-item.correct-answer{padding:.18em .35em}\n',
    'checked task correct-answer style',
)

replace_once(
    '  let renderGeneration = 0;\n',
    '  let renderGeneration = 0;\n  let chunkedMarkdown = null;\n',
    'chunked renderer state',
)

marker = '  window.renderMarkdown = async function(encoded,theme,fontSize){\n'
chunk_api = '''  window.beginChunkedMarkdown = function(theme,fontSize){
    chunkedMarkdown={theme:theme||'light',fontSize:fontSize||17,parts:[]};
  };

  window.appendMarkdownChunk = function(encodedPart){
    if(!chunkedMarkdown)return;
    chunkedMarkdown.parts.push(String(encodedPart||''));
  };

  window.finishChunkedMarkdown = function(){
    const pending=chunkedMarkdown;
    chunkedMarkdown=null;
    if(!pending)return;
    const encoded=pending.parts.join('');
    pending.parts.length=0;
    window.renderMarkdown(encoded,pending.theme,pending.fontSize);
  };

'''
if 'window.beginChunkedMarkdown' not in s:
    if marker not in s:
        raise SystemExit('Expected renderMarkdown marker not found')
    s = s.replace(marker, chunk_api + marker, 1)

path.write_text(s)
print('v0.10 reader chunk transport migration applied.')
