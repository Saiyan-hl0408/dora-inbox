/* Uses the official page's already loaded request module and its session/signing.
 * No credentials or private payloads are written to the console. */
(function () {
  if (window.__parcelJdl) return;
  const state = window.__parcelJdl = { events: [], started: false };
  const emit = event => state.events.push(event);
  function method(module, marker) {
    const matches = Object.values(module).filter(x => typeof x === 'function' && Function.prototype.toString.call(x).includes(marker));
    if (matches.length !== 1) throw Error('MODULE_CHANGED');
    return matches[0];
  }
  state.sdk = async function () {
    const resource = performance.getEntriesByType('resource').map(x => x.name).find(x => /\/actions-[\w-]+\.js(?:\?|$)/.test(x));
    if (!resource) throw Error('NOT_READY');
    const url = new URL(resource);
    if (url.protocol !== 'https:' || !url.hostname.endsWith('.jdcloud-oss.com')) throw Error('MODULE_CHANGED');
    const module = await import(url.href);
    let traceModule;
    async function trace(number) {
      try {
        if (!traceModule) {
          const resources = performance.getEntriesByType('resource').map(x => x.name);
          let traceUrl = resources.find(x => /\/orderTrace-[\w-]+\.js(?:\?|$)/.test(x));
          if (!traceUrl) {
            const routerUrl = resources.find(x => /\/traceId-[\w-]+\.js(?:\?|$)/.test(x));
            if (!routerUrl) return [];
            const code = await (await fetch(routerUrl)).text();
            const name = code.match(/orderTrace-[\w-]+\.js/)?.[0];
            if (!name) return [];
            traceUrl = new URL(name, url.href).href;
          }
          const parsed = new URL(traceUrl);
          if (parsed.origin !== url.origin) return [];
          traceModule = await import(parsed.href);
        }
        return await method(traceModule, '/order/queryExpressTraceGroupPublicNew')(number);
      } catch (_) { return []; }
    }
    return {
      trace,
      accounts: method(module, '/relatives/queryRelativeIncludeSelfList'),
      list: method(module, '/order/searchOrderEnhance'),
      listParams: method(module, 'baseCondition:'),
      detail: method(module, '/search/getOrderEnhancePublic'),
      detailParams: method(module, 'querySceneId:')
    };
  };
  const bounded = async (promise, ms = 18000) => {
    let timer;
    try { return await Promise.race([promise, new Promise((_, reject) => {timer = setTimeout(() => reject(Error('REQUEST_TIMEOUT')), ms);})]); }
    finally { clearTimeout(timer); }
  };
  function normalizeList(response) {
    const data=response?.receiveData;
    const empty=data && typeof data==='object' && !Array.isArray(data) && Object.keys(data).length===0;
    const raw=empty?0:data?.total, total=typeof raw==='string'&&/^\d+$/.test(raw)?Number(raw):raw;
    const orders=data?.orderInfos==null && total===0?[]:data?.orderInfos;
    let detail='';
    if(!data || typeof data!=='object' || Array.isArray(data))detail='RECEIVE_DATA_MISSING';
    else if(Number.isSafeInteger(total)&&total<0)detail='TOTAL_NEGATIVE';
    else if(!Array.isArray(orders))detail='ORDERS_MISSING';
    else if(!Number.isSafeInteger(total))detail='TOTAL_INVALID';
    if(detail){const error=Error('LIST_FAILED');error.detail=detail;throw error;}
    return {orders,total,cusPageInfo:data.cusPageInfo};
  }
  state.check = async function () {
    if(state.valid || state.checking || state.checkError)return;
    state.checking=true;
    try {
      const sdk=await state.sdk(), accounts=await bounded(sdk.accounts());
      if(!Array.isArray(accounts) || !accounts.some(a=>Number(a.id)===-1))throw Error('SESSION_INVALID');
      const self=accounts.find(a=>Number(a.id)===-1);
      normalizeList(await bounded(sdk.list(sdk.listParams({relativeIds:[self.id],searchTypeList:[1],pageNum:1,pageSize:10,cusOrderStatuss:[]}))));
      state.valid=true;
    } catch(error) {
      const known=['SESSION_INVALID','LIST_FAILED','MODULE_CHANGED','REQUEST_TIMEOUT'];
      state.checkError=error?.message==='NOT_READY'?'':known.includes(error?.message)?error.message:'OFFICIAL_REQUEST_FAILED';
    } finally {state.checking=false;}
  };
  const offset = 8 * 3600000;
  const dayFloor = at => Math.floor((at + offset) / 86400000) * 86400000 - offset;
  function parseTime(value) {
    if (typeof value === 'number' || /^\d+$/.test(String(value || ''))) {
      const n = Number(value); return n > 946684800000 ? n : n > 946684800 ? n * 1000 : 0;
    }
    let raw = String(value || '').replace(/\//g, '-').replace(' ', 'T');
    if (/^\d{4}-\d\d-\d\d$/.test(raw)) raw += 'T00:00:00';
    if (/^\d{4}-\d\d-\d\dT\d\d:\d\d(?::\d\d)?$/.test(raw)) raw += '+08:00';
    return Date.parse(raw) || 0;
  }
  state.run = async function (config) {
    if (state.started) return;
    state.started = true;
    let stage = 'sdk', failureDetail = '', pass = '', accountIndex = 0;
    const errorEvent = error => {
      const reasons = ['SESSION_INVALID','MODULE_CHANGED','NOT_READY','LIST_FAILED','PAGING_STALLED','PAGING_INCOMPLETE','REQUEST_TIMEOUT','DETAIL_FAILED','HISTORY_DATE_MISSING'];
      const kinds = ['TypeError','SyntaxError','ReferenceError','SecurityError','AbortError'];
      const net = window.__parcelJdlTransport?.[stage];
      return {type:'error',stage,detail:failureDetail || (['RECEIVE_DATA_MISSING','ORDERS_MISSING','TOTAL_INVALID','TOTAL_NEGATIVE'].includes(error?.detail)?error.detail:''),pass,account:accountIndex+1,
        http:Number.isInteger(net?.http) && net.http>=0 && net.http<=599 ? net.http:0,
        transport:['pending','returned','network_error'].includes(net?.result)?net.result:'',
        kind:kinds.includes(error?.name)?error.name:'',
        reason:reasons.includes(error?.message)?error.message:'OFFICIAL_REQUEST_FAILED'};
    };
    try {
      const sdk = await state.sdk(); stage = 'accounts';
      const accounts = config.metadataNumbers?.length ? [{id:-1}] : await bounded(sdk.accounts());
      if (!Array.isArray(accounts) || !accounts.some(a=>Number(a.id)===-1) || accounts.some(a=>a.id==null)) throw Error('SESSION_INVALID');
      const ids = new Set();
      const verified = accounts.filter(a=>!ids.has(String(a.id)) && ids.add(String(a.id))).sort((a,b)=>Number(Number(b.id)===-1)-Number(Number(a.id)===-1));
      emit({type:'accounts',count:verified.length});
      const local = new Date(config.now + offset), year=local.getUTCFullYear(), month=local.getUTCMonth()-6;
      const monthEnd = new Date(Date.UTC(year,month+1,0)).getUTCDate();
      const sixMonths = Date.UTC(year,month,Math.min(local.getUTCDate(),monthEnd))-offset;
      const lower = Math.max(dayFloor(config.since),sixMonths);
      const failures=[], fetched=new Map(), attempted=new Set(), seen=new Set();
      const cached=new Map((config.cached||[]).map(item=>[item.number,item]));
      const unfinished=new Set(config.refresh||[]);
      let activeListsComplete=true;
      function reusable(number,summary) {
        const item=cached.get(number), base=summary?.baseOrderInfo;
        if(!item || !base || String(base.cusBaseInfo?.orderStatus)!==item.status)return false;
        const version=parseTime(base.lastOperatorTime), age=config.now-item.checkedAt;
        if(version>0 && version!==item.version)return false;
        return (version>0 && item.complete) || (age>=0 && age<15*60000);
      }
      let accountsCompleted=0;
      function receiptTime(order,trace) {
        const points=(trace||[]).flatMap(g=>g.waybillTraceList||[]).filter(p=>String(p.hideStatus)!=='1');
        const signed=points.filter(p=>/已送达|已签收|签收成功/.test(p.descr||'') && !/待自提|待取件|请上门提货|驿站签收/.test(p.descr||''));
        return Math.max(0,...signed.map(p=>parseTime(p.operateTime)));
      }
      async function readDetail(number,account,summary) {
        if (attempted.has(number)) return fetched.get(number);
        attempted.add(number); stage='detail'; failureDetail='';
        try {
          const rawDetail=await bounded(sdk.detail(sdk.detailParams({waybillNo:number,initiatorType:5})));
          if (!rawDetail || rawDetail.baseOrderInfo?.customOrderNo!==number) throw Error('DETAIL_FAILED');
          const detail={...rawDetail,baseOrderInfo:{...rawDetail.baseOrderInfo,
            receivedTime:rawDetail.baseOrderInfo.receivedTime || summary?.baseOrderInfo?.receivedTime,
            lastOperatorTime:rawDetail.baseOrderInfo.lastOperatorTime || summary?.baseOrderInfo?.lastOperatorTime}};
          const rawTrace=config.metadataNumbers?.length?[]:await bounded(sdk.trace(number)).catch(()=>[]);
          const trace=Array.isArray(rawTrace)?rawTrace:[];
          const result={order:detail,trace,at:receiptTime(detail,trace)}; fetched.set(number,result);
          emit({type:config.metadataNumbers?.length?'metadata':'item',order:detail,trace,summary,listStatus:String((summary||detail).baseOrderInfo?.cusBaseInfo?.orderStatus||''),listVersion:parseTime(summary?.baseOrderInfo?.lastOperatorTime),tail:String(account.encryptMobile||'').match(/\d{4}$/)?.[0]||''});
          return result;
        } catch(error) {const issue=errorEvent(error);failures.push(issue);emit({type:'partial',reason:issue.reason});return null;}
      }
      function finish() {
        if(failures.length)emit({...failures[0],accounts:verified.length,accountsCompleted,failureCount:failures.length});
        else emit({type:'done',complete:true,since:lower,limited:config.since<sixMonths,accounts:verified.length,accountsCompleted});
      }
      if(config.metadataNumbers?.length) {for(const number of new Set(config.metadataNumbers))if(typeof number==='string'&&/^[A-Za-z0-9]{8,32}$/.test(number))await readDetail(number,{},null);finish();return;}
      if(config.onlyNumber) {await readDetail(config.onlyNumber,{},null);finish();return;}
      // Use the same status filters exposed by the official list. No API date
      // conditions: locally inspect every normal page, including an empty page.
      const passes=[{name:'history',statuses:[4]},{name:'pickup',statuses:[9]},{name:'transit',statuses:[1,2,3,8]}];
      for(accountIndex=0;accountIndex<verified.length;accountIndex++) {
        const account=verified[accountIndex], before=failures.length;
        let accountRows=0;
        // The website's default list has no status filter. Cross-check every
        // empty recipient against that same list before accepting zero results.
        for(const selection of [...passes,{name:'official',statuses:[]}]) {
          if(selection.name==='official' && accountRows>0)continue;
          pass=selection.name; let read=0,cusPageInfo,dateMissing=false;
          const signatures=new Set();
          try {
            for(let pageNum=1;pageNum<=1000;pageNum++) {
              stage='list';failureDetail='';
              emit({type:'progress',account:accountIndex+1,accounts:verified.length,page:pageNum,pass});
              const params=sdk.listParams({relativeIds:[account.id],searchTypeList:[1],pageNum,pageSize:10,cusPageInfo,cusOrderStatuss:selection.statuses});
              const {orders,total,cusPageInfo:nextPage}=normalizeList(await bounded(sdk.list(params)));
              accountRows+=orders.length;
              emit({type:'list_diagnostic',account:accountIndex+1,pass,page:pageNum,rows:orders.length,total,searchType:1,statuses:selection.statuses.join(','),since:lower});
              emit({type:'progress',account:accountIndex+1,accounts:verified.length,page:pageNum,pass});
              const signature=orders.map(o=>o.baseOrderInfo?.customOrderNo).join('|');
              if(orders.length&&signatures.has(signature))throw Error('PAGING_STALLED');signatures.add(signature);
              const times=[];
              for(const order of orders) {
                const number=order.baseOrderInfo?.customOrderNo;
                if(typeof number!=='string'||!/^[A-Za-z0-9]{8,32}$/.test(number)) {
                  stage='detail';failures.push(errorEvent(Error('DETAIL_FAILED')));continue;
                }
                seen.add(number);
                const summaryTime=parseTime(order.baseOrderInfo?.lastOperatorTime);
                const historical=pass==='history' || (pass==='official' && Number(order.baseOrderInfo?.cusBaseInfo?.orderStatus)===4);
                // A completed row older than the boundary needs no detail query.
                if(historical && summaryTime>0 && summaryTime<lower && !unfinished.has(number)) {times.push(summaryTime);continue;}
                if(reusable(number,order)) {
                  emit({type:'cached',number,summary:order});
                  if(pass==='history')times.push(cached.get(number).at || summaryTime);
                } else {
                  const detail=await readDetail(number,account,order);
                  if(pass==='history')times.push(detail?.at || summaryTime);
                }
              }
              stage='list';failureDetail='';read+=orders.length;cusPageInfo=nextPage;
              if(pass==='history' && times.some(t=>!t))dateMissing=true;
              // Only completed history has a local date boundary. Pending and
              // transit never stop at an old date or another parcel's signature.
              const boundary=pass==='history' && times.length===orders.length && times.length>0 && times.every(t=>t>0 && t<lower);
              if(read>=total || !orders.length || boundary) {
                if(!orders.length && read<total)throw Error('PAGING_INCOMPLETE');
                if(pass==='history'&&dateMissing)throw Error('HISTORY_DATE_MISSING');
                break;
              }
              if(pageNum===1000)throw Error('PAGING_INCOMPLETE');
            }
          } catch(error) {if(pass!=='history')activeListsComplete=false;failures.push(errorEvent(error));}
        }
        if(failures.length===before)accountsCompleted++;
        emit({type:'account_done',account:accountIndex+1,accounts:verified.length,complete:failures.length===before});
      }
      pass='refresh';
      if(activeListsComplete)for(const number of config.refresh||[])if(!seen.has(number))await readDetail(number,{},null);
      finish();
    } catch(error) {emit(errorEvent(error));}
  };
})();
