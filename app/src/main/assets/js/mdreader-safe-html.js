(function(){
  'use strict';

  const ALLOWED_TAGS = [
    'a','abbr','b','blockquote','br','cite','code','dd','del','details','div','dl','dt','em',
    'figcaption','figure','h1','h2','h3','h4','h5','h6','hr','i','img','ins','kbd','li','mark',
    'ol','p','pre','q','s','small','span','strong','sub','summary','sup','table','tbody','td',
    'tfoot','th','thead','tr','u','ul'
  ];
  const ALLOWED_ATTR = [
    'alt','class','colspan','dir','height','href','open','reversed','rowspan','src','start','style',
    'title','width'
  ];
  const FORBID_TAGS = [
    'script','style','iframe','object','embed','form','input','button','textarea','select','option',
    'meta','link','base','svg','math','video','audio','canvas'
  ];
  const NAMED_COLORS = new Set([
    'black','white','gray','grey','red','green','blue','yellow','orange','purple','pink','brown',
    'cyan','magenta','teal','navy','maroon','olive','lime','aqua','silver','transparent','currentcolor'
  ]);
  const SAFE_STYLE_PROPERTIES = new Set([
    'color','background-color','text-align','font-weight','font-style','text-decoration','font-size','display'
  ]);

  function escapeHtml(value){
    return String(value||'').replace(/[&<>"']/g,function(ch){
      return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch];
    });
  }

  function safeColor(value){
    const v=String(value||'').trim().toLowerCase();
    if(/^(?:#[0-9a-f]{3}|#[0-9a-f]{4}|#[0-9a-f]{6}|#[0-9a-f]{8})$/i.test(v))return v;
    if(/^rgba?\(\s*[\d.%\s,]+\)$/i.test(v))return v;
    if(/^hsla?\(\s*[-\d.%\s,]+\)$/i.test(v))return v;
    return NAMED_COLORS.has(v)?v:'';
  }

  function safeFontSize(value){
    const v=String(value||'').trim().toLowerCase();
    const m=v.match(/^(\d+(?:\.\d+)?)(px|em|rem|%)$/);
    if(!m)return '';
    const n=Number(m[1]);
    if(!Number.isFinite(n)||n<=0)return '';
    if(m[2]==='px'&&n>96)return '';
    if((m[2]==='em'||m[2]==='rem')&&n>6)return '';
    if(m[2]==='%'&&n>400)return '';
    return v;
  }

  function safeStyleValue(property,value){
    const v=String(value||'').trim();
    if(!v||/[{}<>]|url\s*\(|expression\s*\(|javascript\s*:/i.test(v))return '';
    switch(property){
      case 'color':
      case 'background-color': return safeColor(v);
      case 'text-align': return /^(left|right|center|justify|start|end)$/i.test(v)?v.toLowerCase():'';
      case 'font-weight': return /^(normal|bold|[1-9]00)$/i.test(v)?v.toLowerCase():'';
      case 'font-style': return /^(normal|italic|oblique)$/i.test(v)?v.toLowerCase():'';
      case 'text-decoration': return /^(?:none|underline|line-through|overline)(?:\s+(?:underline|line-through|overline))*$/i.test(v)?v.toLowerCase():'';
      case 'font-size': return safeFontSize(v);
      case 'display': return /^(block|inline|inline-block)$/i.test(v)?v.toLowerCase():'';
      default: return '';
    }
  }

  function sanitizeStyle(style){
    const out=[];
    String(style||'').split(';').forEach(function(part){
      const idx=part.indexOf(':');
      if(idx<1)return;
      const property=part.slice(0,idx).trim().toLowerCase();
      if(!SAFE_STYLE_PROPERTIES.has(property))return;
      const value=safeStyleValue(property,part.slice(idx+1));
      if(value)out.push(property+':'+value);
    });
    return out.join(';');
  }

  function sanitizeClass(value){
    return String(value||'').split(/\s+/).filter(function(token){
      return /^(?:language|lang)-[a-z0-9_+.-]+$/i.test(token);
    }).join(' ');
  }

  function numericAttribute(value,min,max){
    if(!/^\d+$/.test(String(value||'').trim()))return '';
    const n=Number(value);
    return n>=min&&n<=max?String(n):'';
  }

  function installHooks(){
    if(!window.DOMPurify||window.__mdReaderSafeHtmlHooksInstalled)return;
    window.__mdReaderSafeHtmlHooksInstalled=true;
    DOMPurify.addHook('uponSanitizeAttribute',function(node,data){
      const name=String(data.attrName||'').toLowerCase();
      if(name==='style'){
        const cleaned=sanitizeStyle(data.attrValue);
        if(cleaned)data.attrValue=cleaned;else data.keepAttr=false;
      }else if(name==='class'){
        const cleaned=sanitizeClass(data.attrValue);
        if(cleaned)data.attrValue=cleaned;else data.keepAttr=false;
      }else if(name==='dir'){
        if(!/^(rtl|ltr|auto)$/i.test(String(data.attrValue||'')))data.keepAttr=false;
      }else if(name==='width'||name==='height'){
        const cleaned=numericAttribute(data.attrValue,1,4096);
        if(cleaned)data.attrValue=cleaned;else data.keepAttr=false;
      }else if(name==='colspan'||name==='rowspan'){
        const cleaned=numericAttribute(data.attrValue,1,100);
        if(cleaned)data.attrValue=cleaned;else data.keepAttr=false;
      }else if(name==='start'){
        if(!/^-?\d{1,6}$/.test(String(data.attrValue||'').trim()))data.keepAttr=false;
      }
    });
  }

  function sanitize(html){
    if(!window.DOMPurify)return escapeHtml(html);
    installHooks();
    return DOMPurify.sanitize(String(html||''),{
      ALLOWED_TAGS:ALLOWED_TAGS,
      ALLOWED_ATTR:ALLOWED_ATTR,
      FORBID_TAGS:FORBID_TAGS,
      ALLOW_ARIA_ATTR:false,
      ALLOW_DATA_ATTR:false,
      ALLOW_UNKNOWN_PROTOCOLS:false,
      KEEP_CONTENT:true,
      RETURN_TRUSTED_TYPE:false
    });
  }

  window.MDReaderSafeHtml={
    sanitize:sanitize,
    sanitizeStyle:sanitizeStyle,
    safeColor:safeColor,
    allowedTags:ALLOWED_TAGS.slice()
  };
})();
