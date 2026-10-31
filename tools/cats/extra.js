/* Extra cats the app needs beyond the mock: 6 more pack breeds and full colours for the 6 secret cats. */
Object.assign(BREEDS,{
  pickles:{n:'Pickles',b:'Grey Tabby',r:'Common',c:{fur:'#8E8C94',fur2:'#B4B2BA',dark:'#6E6C74',mark:'#4A4850',pattern:'tabby',eyes:'#9BD36A',muzzle:'#EDEBF0'}},
  snowball:{n:'Snowball',b:'White Cat',r:'Common',c:{fur:'#F4F4F8',fur2:'#FFFFFF',dark:'#DCDCE6',eyes:'#6FB7FF',muzzle:'#FFFFFF',nose:'#FF9FB6',whisk:'rgba(90,90,110,.45)'}},
  biscuit:{n:'Biscuit',b:'Munchkin',r:'Uncommon',c:{fur:'#E6B47A',fur2:'#F6D2A2',dark:'#C98F52',mark:'#B07436',pattern:'tabby',round:1,eyes:'#F0A640',muzzle:'#FFF4E4'}},
  pumpkin:{n:'Pumpkin',b:'Exotic Shorthair',r:'Uncommon',c:{fur:'#F0A35E',fur2:'#FFC58C',dark:'#D9823C',mark:'#C2621E',face:'wide',round:1,pattern:'tabby',eyes:'#C9862A',muzzle:'#FFF1E0'}},
  mochi:{n:'Mochi',b:'Japanese Bobtail',r:'Rare',c:{fur:'#FFFFFF',fur2:'#FFFFFF',dark:'#E8893A',earFur:'#2B2730',pattern:'calico',eyes:'#F0A640',muzzle:'#FFFFFF',whisk:'rgba(80,70,80,.5)'}},
  ghost:{n:'Ghost',b:'Turkish Angora',r:'Holo',c:{fur:'#EEF0FA',fur2:'#FFFFFF',dark:'#D8DCEE',fluff:1,fluffC:'#F7F8FF',eyes:'#7CC8FF',glow:1,muzzle:'#FFFFFF',whisk:'rgba(90,90,120,.45)'}},
  cosmo:{n:'Cosmo',b:'Abyssinian',r:'Holo',c:{fur:'#C07A3E',fur2:'#E3A066',dark:'#9A5A28',mark:'#7A4018',ears:'big',face:'narrow',eyes:'#B7E05A',glow:1,muzzle:'#F6D9B8'}},
  ragdoll:{n:'Ragdoll',b:'Ragdoll',r:'Secret',c:{fur:'#DCD6D2',fur2:'#F2EEEA',dark:'#A69A93',fluff:1,fluffC:'#EAE6E2',pattern:'points',softPoints:1,mark:'#76675F',eyes:'#5BB8FF',muzzle:'#FBFAF8',round:1}},
  persian:{n:'Snow Queen',b:'Persian',r:'Secret',c:{fur:'#F2ECF4',fur2:'#FFFFFF',face:'wide',fluff:1,fluffC:'#FFFFFF',round:1,eyes:'#E08A2A',muzzle:'#FFFFFF',nose:'#FF9FB6',whisk:'rgba(90,90,120,.45)',crown:1}},
  savannah:{n:'Zuri',b:'Savannah',r:'Secret',c:{fur:'#DDA070',fur2:'#F5CFAE',dark:'#B97F4C',mark:'#2E1E10',mark2:'#2E1E10',pattern:'spots',ears:'big',face:'narrow',eyes:'#A6D46A',muzzle:'#FFF2E8'}},
  norwegian:{n:'Bjorn',b:'Norwegian Forest',r:'Secret',c:{fur:'#7A6450',fur2:'#A08870',dark:'#5A4434',mark:'#3A2A1E',pattern:'tabby',fluff:1,fluffC:'#E8DCCB',tuft:1,eyes:'#9BD36A',muzzle:'#F4ECE0'}},
  british:{n:'Earl Grey',b:'British Shorthair',r:'Secret',c:{fur:'#6E7686',fur2:'#959DAD',dark:'#596071',face:'wide',round:1,eyes:'#E0782F',muzzle:'#AEB5C2',nose:'#4E5668',monocle:1}},
  lion:{n:'Leo',b:'Lion',r:'Secret',c:{fur:'#D98A4A',fur2:'#F0AE7C',dark:'#C9822E',fluff:1,fluffC:'#9A5420',face:'wide',round:1,eyes:'#E0802F',muzzle:'#FFEEE2',nose:'#6B3A1E',crown:1}},
});
Object.assign(POSE,{
  pickles:{pose:'proud',bg:['#E8F7C8','#A8D86A']},
  snowball:{pose:'loaf',bg:['#D8E8FF','#8AB4FF']},
  biscuit:{pose:'sit',bg:['#FFE9C4','#FFC06B']},
  pumpkin:{pose:'buddha',bg:['#FFE0CC','#FF9F7A']},
  mochi:{pose:'lick',bg:['#FFE3EF','#FF9FC6']},
  ghost:{pose:'sleep',bg:['#3A3F8F','#1B1F52']},
  cosmo:{pose:'proud',bigTail:1,bg:['#2E5A7A','#122438']},
  ragdoll:{pose:'loaf',bg:['#CFCBF2','#5E57A8']},
  persian:{pose:'buddha',bg:['#FFE0F2','#D88BFF']},
  savannah:{pose:'pounce',bg:['#FFD8B8','#FF9F6B']},
  norwegian:{pose:'sit',bg:['#D4F0E4','#4FB894']},
  british:{pose:'tea',bg:['#FFE6D0','#FFB37A']},
  lion:{pose:'proud',bigTail:1,bg:['#FFD8B8','#FF8A3D']},
});
/* App ids for the mock's keys. */
const IDS={mango:'gold',oreo:'ember',patches:'koala',miso:'jade',blue:'tide',dumpling:'flamingo',thor:'wolf',jaguar:'obsidian',dobby:'sphynx',midnight:'aurora',maneki:'phoenix',
  pickles:'pickles',snowball:'snowball',biscuit:'biscuit',pumpkin:'pumpkin',mochi:'mochi',ghost:'ghost',cosmo:'cosmo',
  ragdoll:'ragdoll',persian:'persian',savannah:'savannah',norwegian:'norwegian',british:'british',lion:'lion'};
/* Ragdoll: a lighter colour-point mask centred on the face (the mock's sat under the chin and smudged the muzzle).
   Earl Grey: a silver monocle on his right eye. */
const _cat=cat;
cat=function(o,sil){
  let s=_cat(o,sil);
  if(sil)return s;
  if(o.softPoints)s=s.replace('cy=".62" r=".42"','cy=".46" r=".36"').replace('stop-opacity=".95"','stop-opacity=".7"').replace('stop-opacity=".55"','stop-opacity=".35"');
  if(o.monocle)s=s.slice(0,-'</g>'.length)+'<path d="M72 58Q82 72 74 86" stroke="rgba(233,237,245,.8)" stroke-width="1.2" fill="none" stroke-linecap="round"/><circle cx="63" cy="55" r="9.4" fill="#fff" fill-opacity=".12" stroke="#F4F6FA" stroke-width="2.4"/><path d="M57 50Q59.5 46.5 63.5 46" stroke="rgba(255,255,255,.8)" stroke-width="1.4" fill="none" stroke-linecap="round"/></g>';
  return s;
};
/* Earl Grey's own pose: sitting up with a cup of tea (not Blue's box). */
const _body=body;
body=function(k,o,c,sil){
  if(o.pose!=='tea')return _body(k,o,c,sil);
  const s=_body(k,{...o,pose:'sit'},c,sil);
  if(sil)return s;
  const cup='<ellipse cx="50" cy="89" rx="13" ry="2.6" fill="#F4F6FA" stroke="#C9D1E0" stroke-width=".6"/>'
    +'<path d="M58.5 80Q64.5 79.5 64 83.5Q63.5 87 58 86" stroke="#F4F6FA" stroke-width="1.8" fill="none" stroke-linecap="round"/>'
    +'<path d="M41 78H59Q58.5 88 50 88.4Q41.5 88 41 78Z" fill="#fff" stroke="#C9D1E0" stroke-width=".6"/>'
    +'<path d="M41.6 82.2H58.4" stroke="#FF8FB8" stroke-width="1.4"/>'
    +'<ellipse cx="50" cy="78" rx="8.4" ry="1.5" fill="#9A5A34"/>'
    +'<g opacity=".7"><path d="M47 75Q45 72.5 47 70Q49 67.5 47 65M53 74Q51 71.5 53 69Q55 66.5 53 64" stroke="#fff" stroke-width="1.1" fill="none" stroke-linecap="round"/></g>';
  // Inside the breathing group, so the cup rises and falls with him.
  return s.slice(0,-'</g>'.length)+cup+'</g>';
};
