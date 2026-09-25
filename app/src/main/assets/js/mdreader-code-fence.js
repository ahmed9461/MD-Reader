(function(root,factory){
  'use strict';

  const api=factory();
  if(typeof module==='object'&&module.exports)module.exports=api;
  if(root)root.MDReaderCodeFence=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
  'use strict';

  const MAX_LANGUAGE_LENGTH=40;
  const MAX_TITLE_LENGTH=160;

  function cleanLanguage(value){
    return String(value||'')
      .trim()
      .replace(/[^A-Za-z0-9_+.-]/g,'')
      .slice(0,MAX_LANGUAGE_LENGTH);
  }

  function cleanTitle(value){
    return String(value||'')
      .replace(/[\u0000-\u001F\u007F]/g,' ')
      .replace(/\s+/g,' ')
      .trim()
      .slice(0,MAX_TITLE_LENGTH);
  }

  function parseInfo(info){
    const raw=String(info||'').trim();
    if(!raw)return {language:'',title:''};

    const titleStart=raw.match(/(?:^|\s)title\s*=\s*/i);
    let explicitTitle='';
    if(titleStart){
      const rest=raw.slice(titleStart.index+titleStart[0].length);
      const quote=rest.charAt(0);
      if(quote==='"'||quote==="'"){
        let out='';let escaped=false;let closed=false;
        for(let i=1;i<rest.length;i++){
          const ch=rest.charAt(i);
          if(ch==='\r'||ch==='\n')break;
          if(escaped){out+=ch;escaped=false;continue;}
          if(ch==='\\'){escaped=true;continue;}
          if(ch===quote){closed=true;break;}
          out+=ch;
        }
        if(closed)explicitTitle=out;
      }else{
        const plain=rest.match(/^([^\s{}]+)/);
        if(plain)explicitTitle=plain[1];
      }
    }

    let first=(raw.split(/\s+/)[0]||'').trim();
    if(/^title\s*=/i.test(first))first='';

    let language=first;
    let shorthandTitle='';
    const colon=first.indexOf(':');
    if(colon>0){
      language=first.slice(0,colon);
      shorthandTitle=first.slice(colon+1);
    }

    return {
      language:cleanLanguage(language),
      title:cleanTitle(explicitTitle||shorthandTitle)
    };
  }

  return {
    parseInfo:parseInfo,
    cleanLanguage:cleanLanguage,
    cleanTitle:cleanTitle
  };
});
