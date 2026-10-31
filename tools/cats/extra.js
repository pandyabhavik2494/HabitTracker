/* Extra cats the app needs beyond the mock: 6 more pack breeds and full colours for the 6 secret cats. */
Object.assign(BREEDS,{
  pickles:{n:'Pickles',b:'Grey Tabby',r:'Common',c:{fur:'#8E8C94',fur2:'#B4B2BA',dark:'#6E6C74',mark:'#4A4850',pattern:'tabby',eyes:'#9BD36A',muzzle:'#EDEBF0'}},
  snowball:{n:'Snowball',b:'White Cat',r:'Common',c:{fur:'#F4F4F8',fur2:'#FFFFFF',dark:'#DCDCE6',eyes:'#6FB7FF',muzzle:'#FFFFFF',nose:'#FF9FB6',whisk:'rgba(90,90,110,.45)'}},
  biscuit:{n:'Biscuit',b:'Munchkin',r:'Uncommon',c:{fur:'#E6B47A',fur2:'#F6D2A2',dark:'#C98F52',mark:'#B07436',pattern:'tabby',round:1,eyes:'#F0A640',muzzle:'#FFF4E4'}},
  pumpkin:{n:'Pumpkin',b:'Exotic Shorthair',r:'Uncommon',c:{fur:'#F0A35E',fur2:'#FFC58C',dark:'#D9823C',mark:'#C2621E',face:'wide',round:1,pattern:'tabby',eyes:'#C9862A',muzzle:'#FFF1E0'}},
  mochi:{n:'Mochi',b:'Japanese Bobtail',r:'Rare',c:{fur:'#FFFFFF',fur2:'#FFFFFF',dark:'#E8893A',earFur:'#2B2730',pattern:'calico',eyes:'#F0A640',muzzle:'#FFFFFF',whisk:'rgba(80,70,80,.5)'}},
  ghost:{n:'Ghost',b:'Turkish Angora',r:'Holo',c:{fur:'#EEF0FA',fur2:'#FFFFFF',dark:'#D8DCEE',fluff:1,fluffC:'#F7F8FF',eyes:'#7CC8FF',glow:1,muzzle:'#FFFFFF',whisk:'rgba(90,90,120,.45)'}},
  cosmo:{n:'Cosmo',b:'Abyssinian',r:'Holo',c:{fur:'#C07A3E',fur2:'#E3A066',dark:'#9A5A28',mark:'#7A4018',ears:'big',face:'narrow',eyes:'#B7E05A',glow:1,muzzle:'#F6D9B8'}},
  ragdoll:{n:'Ragdoll',b:'Ragdoll',r:'Secret',c:{fur:'#F3EADB',fur2:'#FFF8EC',fluff:1,fluffC:'#FFF6E8',pattern:'points',mark:'#6B5444',eyes:'#5BB8FF',muzzle:'#FFF8EC',round:1}},
  persian:{n:'Snow Queen',b:'Persian',r:'Secret',c:{fur:'#F2ECF4',fur2:'#FFFFFF',face:'wide',fluff:1,fluffC:'#FFFFFF',round:1,eyes:'#E08A2A',muzzle:'#FFFFFF',nose:'#FF9FB6',whisk:'rgba(90,90,120,.45)',crown:1}},
  savannah:{n:'Zuri',b:'Savannah',r:'Secret',c:{fur:'#E8C07A',fur2:'#F6DCA6',dark:'#C99A4E',mark:'#2E1E10',mark2:'#2E1E10',pattern:'spots',ears:'big',face:'narrow',eyes:'#C9D45A',muzzle:'#FFF4E0'}},
  norwegian:{n:'Bjorn',b:'Norwegian Forest',r:'Secret',c:{fur:'#7A6450',fur2:'#A08870',dark:'#5A4434',mark:'#3A2A1E',pattern:'tabby',fluff:1,fluffC:'#E8DCCB',tuft:1,eyes:'#9BD36A',muzzle:'#F4ECE0'}},
  british:{n:'Earl Grey',b:'British Shorthair',r:'Secret',c:{fur:'#7E8AA0',fur2:'#A4AEC2',dark:'#68748A',face:'wide',round:1,eyes:'#F0A640',muzzle:'#B6C0D4',nose:'#5E6B82'}},
  lion:{n:'Leo',b:'Lion',r:'Secret',c:{fur:'#E2A24E',fur2:'#F4C57E',dark:'#C9822E',fluff:1,fluffC:'#9A5420',face:'wide',round:1,eyes:'#E0A030',muzzle:'#FFF0D8',nose:'#6B3A1E',crown:1}},
});
Object.assign(POSE,{
  pickles:{pose:'proud',bg:['#E8F7C8','#A8D86A']},
  snowball:{pose:'loaf',bg:['#D8E8FF','#8AB4FF']},
  biscuit:{pose:'sit',bg:['#FFE9C4','#FFC06B']},
  pumpkin:{pose:'buddha',bg:['#FFE0CC','#FF9F7A']},
  mochi:{pose:'lick',bg:['#FFE3EF','#FF9FC6']},
  ghost:{pose:'sleep',bg:['#3A3F8F','#1B1F52']},
  cosmo:{pose:'proud',bigTail:1,bg:['#2E5A7A','#122438']},
  ragdoll:{pose:'loaf',bg:['#D8E4FF','#7F9CFF']},
  persian:{pose:'buddha',bg:['#FFE0F2','#D88BFF']},
  savannah:{pose:'pounce',bg:['#FFF0C8','#F2B84E']},
  norwegian:{pose:'sit',bg:['#D4F0E4','#4FB894']},
  british:{pose:'box',bg:['#FFE6D0','#FFB37A']},
  lion:{pose:'proud',bigTail:1,bg:['#FFE7A8','#FF8A3D']},
});
/* App ids for the mock's keys. */
const IDS={mango:'gold',oreo:'ember',patches:'koala',miso:'jade',blue:'tide',dumpling:'flamingo',thor:'wolf',jaguar:'obsidian',dobby:'sphynx',midnight:'aurora',maneki:'phoenix',
  pickles:'pickles',snowball:'snowball',biscuit:'biscuit',pumpkin:'pumpkin',mochi:'mochi',ghost:'ghost',cosmo:'cosmo',
  ragdoll:'ragdoll',persian:'persian',savannah:'savannah',norwegian:'norwegian',british:'british',lion:'lion'};
