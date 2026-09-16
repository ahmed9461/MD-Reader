from pathlib import Path

java = Path('app/src/main/java/app/mdreader/mobile/MainActivity.java')
s = java.read_text()


def one(old: str, new: str, label: str) -> None:
    global s
    if new in s:
        return
    if old not in s:
        raise SystemExit(f'missing source block: {label}')
    s = s.replace(old, new, 1)


one(
    '    private static final int PREVIEW_CHUNK_CHARS=90000;\n',
    '    private static final int PREVIEW_CHUNK_CHARS=90000;\n'
    '    private static final int LARGE_EDITOR_DEBOUNCE_CHARS=120000, LARGE_HISTORY_LIMIT=20;\n',
    'large editor constants',
)

one(
    '    private Runnable saveThen,draftPending,renderPending;\n',
    '    private Runnable saveThen,draftPending,renderPending,editorMaintenancePending;\n',
    'editor maintenance state',
)

old = '''    private void configureEditor(){
        history.reset(""); editor.addTextChangedListener(new Watcher(){@Override public void afterTextChanged(Editable s){ if(suppress)return; String t=s.toString(); dirty=!t.equals(savedText); history.schedule(t); scheduleDraft(t); updateTitle(); if(searchBar.getVisibility()==View.VISIBLE) editorSearch(searchInput.getText().toString()); }});
    }

'''
new = '''    private void configureEditor(){
        history.reset(""); editor.addTextChangedListener(new Watcher(){@Override public void afterTextChanged(Editable s){
            if(suppress)return;
            if(s.length()>=LARGE_EDITOR_DEBOUNCE_CHARS){
                dirty=true;updateTitle();scheduleEditorMaintenance();
            }else{
                String t=s.toString();dirty=!t.equals(savedText);history.schedule(t);scheduleDraft(t);updateTitle();
                if(searchBar.getVisibility()==View.VISIBLE)editorSearch(searchInput.getText().toString());
            }
        }});
    }

    private void scheduleEditorMaintenance(){
        if(editorMaintenancePending!=null)main.removeCallbacks(editorMaintenancePending);
        editorMaintenancePending=()->{
            editorMaintenancePending=null;
            if(suppress||editor==null)return;
            String t=editor.getText().toString();
            dirty=!t.equals(savedText);history.schedule(t);scheduleDraft(t);updateTitle();
            if(searchBar.getVisibility()==View.VISIBLE)editorSearch(searchInput.getText().toString());
        };
        main.postDelayed(editorMaintenancePending,220);
    }

'''
one(old, new, 'configure editor debounce')

one(
    '    private void editorSearch(String q){if(q==null||q.isEmpty()){searchCount.setText("0/0");return;}int count=SearchReplaceEngine.count(txt(),q,false);int current=SearchReplaceEngine.ordinalAt(txt(),q,editor.getSelectionStart(),false);searchCount.setText(count==0?"0/0":Math.max(1,current)+"/"+count);}\n',
    '    private void editorSearch(String q){if(q==null||q.isEmpty()){searchCount.setText("0/0");return;}String source=txt();int count=SearchReplaceEngine.count(source,q,false);int current=SearchReplaceEngine.ordinalAt(source,q,editor.getSelectionStart(),false);searchCount.setText(count==0?"0/0":Math.max(1,current)+"/"+count);}\n',
    'single editor search snapshot',
)

one(
    'void checkpoint(String s){cancel();if(s==null)s="";if(s.equals(cur))return;undo.add(cur);if(undo.size()>80)undo.remove(0);cur=s;redo.clear();}',
    'void checkpoint(String s){cancel();if(s==null)s="";if(s.equals(cur))return;undo.add(cur);int limit=s.length()>=LARGE_EDITOR_DEBOUNCE_CHARS?LARGE_HISTORY_LIMIT:80;while(undo.size()>limit)undo.remove(0);cur=s;redo.clear();}',
    'large history cap',
)

one(
    '    @Override protected void onDestroy(){if(draftPending!=null)main.removeCallbacks(draftPending);if(renderPending!=null)main.removeCallbacks(renderPending);renderToken++;previewIo.shutdownNow();io.shutdownNow();',
    '    @Override protected void onDestroy(){if(draftPending!=null)main.removeCallbacks(draftPending);if(renderPending!=null)main.removeCallbacks(renderPending);if(editorMaintenancePending!=null)main.removeCallbacks(editorMaintenancePending);renderToken++;previewIo.shutdownNow();io.shutdownNow();',
    'editor maintenance cleanup',
)

if 'LARGE_EDITOR_DEBOUNCE_CHARS=120000' not in s or 'main.postDelayed(editorMaintenancePending,220)' not in s:
    raise SystemExit('large editor performance assertions failed')
java.write_text(s)

reader = Path('app/src/main/assets/reader.html')
h = reader.read_text()
old_threshold = '  const LARGE_DOCUMENT_CHARS = 400000;'
new_threshold = '  const LARGE_DOCUMENT_CHARS = 180000;'
if new_threshold not in h:
    if old_threshold not in h:
        raise SystemExit('large document threshold marker missing')
    h = h.replace(old_threshold, new_threshold, 1)
reader.write_text(h)

print('v0.10 large-document performance polish applied.')
