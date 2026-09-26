"""Source-level UI guardrails. Complements (not a substitute for) emulator/phone tests."""
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
j=root/'app/src/main/java/app/mdreader/mobile'
main=(j/'MainActivity.java').read_text()
assert 'private void searchReplaceDialog(){openSearch(true);}' in main
assert 'new FindReplaceBar' in main and 'ResponsiveSheet.show' in main
assert 'SearchMatchState.scan' in main
assert 'editor.requestFocus();editor.setSelection(idx' not in main
assert 'FlingEditText' in main and 'setFlingSpeedPercent' in main
assert 'findPanel.setSideBySide(split)' in main and 'root.setOrientation(orientation)' in main
assert 'homeMode||focusSearch||tight' in main
assert '!homeMode&&editing&&!searching&&!tight' in main
for name in ('homePage','showDocument','safeHtmlTools','titledCodeBlockTool','GitHubMarkdownSource.download','speechMenu','translationSettings','undo','redo'):
    assert name in main,name
sheet=(j/'ResponsiveSheet.java').read_text()
assert 'WindowInsets.Type.ime()' in sheet
assert 'ReaderUi.touch(c)' in sheet and 'CappedColumn' in sheet
assert 'new ScrollView' in sheet
for path in (root/'app/src/main').rglob('*.xml'):ET.parse(path)
assert "versionCode 16" in (root/'app/build.gradle').read_text()
assert 'md-reader-release' not in '\n'.join(str(x) for x in root.glob('**/*.jks'))
print('UI source contracts passed.')
