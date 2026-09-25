'use strict';

const assert=require('assert');
const meta=require('../app/src/main/assets/js/mdreader-code-fence.js');

assert.deepStrictEqual(
  meta.parseInfo('python title="app.py"'),
  {language:'python',title:'app.py'}
);
assert.deepStrictEqual(
  meta.parseInfo('title="طالب 1"'),
  {language:'',title:'طالب 1'}
);
assert.deepStrictEqual(
  meta.parseInfo('text title="شرح \\"المثال الأول\\""'),
  {language:'text',title:'شرح "المثال الأول"'}
);
assert.deepStrictEqual(
  meta.parseInfo("javascript title='main file.js'"),
  {language:'javascript',title:'main file.js'}
);
assert.deepStrictEqual(
  meta.parseInfo('json title=data.json'),
  {language:'json',title:'data.json'}
);
assert.deepStrictEqual(
  meta.parseInfo('python:app.py'),
  {language:'python',title:'app.py'}
);
assert.deepStrictEqual(
  meta.parseInfo('c++:main.cpp'),
  {language:'c++',title:'main.cpp'}
);
assert.deepStrictEqual(
  meta.parseInfo('python'),
  {language:'python',title:''}
);
assert.deepStrictEqual(
  meta.parseInfo(''),
  {language:'',title:''}
);
assert.strictEqual(meta.parseInfo('python title="hello\nworld.py"').title,'');
assert.strictEqual(meta.cleanTitle('  app\tname.py  '),'app name.py');
assert.strictEqual(meta.cleanLanguage('py<script>'),'pyscript');

console.log('Code fence metadata smoke tests passed.');
