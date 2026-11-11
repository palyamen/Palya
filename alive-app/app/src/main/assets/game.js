(()=>{'use strict';
const canvas=document.getElementById('world'),ctx=canvas.getContext('2d'),$=id=>document.getElementById(id);
const ui={day:$('day'),goal:$('goal'),people:$('people'),resources:$('resources'),law:$('law'),log:$('log'),pause:$('pause'),speed:$('speed'),save:$('save'),load:$('load'),reset:$('reset'),build:$('build'),farm:$('farm'),craft:$('craft'),lawBtn:$('lawBtn'),inspect:$('inspect')};
const T={GRASS:0,FOREST:1,WATER:2,ROCK:3,BERRY:4,DIRT:5,CROP:6};
const NAMES_M=['Илья','Марк','Егор','Лев','Тимур','Роман','Даня','Миша','Артур','Кирилл'],NAMES_F=['Мира','Ася','Лада','Ева','Ника','Соня','Вера','Лиза','Арина','Тася'];
const W=72,H=52,S=32;
let world,terrain=[],objects=[],animals=[],logs=[],selected=null,running=true,speed=1,tick=0,last=0,camera={x:0,y:0},drag=null,weather='Ясно',season=0,toast='',toastUntil=0;
const rand=n=>Math.floor(Math.random()*n),chance=n=>Math.random()<n,choose=a=>a[rand(a.length)],clamp=(v,a,b)=>Math.max(a,Math.min(b,v)),dist=(a,b)=>Math.abs(a.x-b.x)+Math.abs(a.y-b.y),uid=()=>Math.random().toString(36).slice(2,10);
function say(s){logs.unshift('День '+world.day+' · '+s);logs=logs.slice(0,70);toast=s;toastUntil=Date.now()+2600}
function tile(x,y){return x>=0&&y>=0&&x<W&&y<H?terrain[y][x]:T.WATER}
function makeTerrain(){
 terrain=Array.from({length:H},()=>Array.from({length:W},()=>T.GRASS));
 for(let y=0;y<H;y++)for(let x=0;x<W;x++){let n=Math.random();terrain[y][x]=n<.09?T.WATER:n<.29?T.FOREST:n<.34?T.ROCK:T.GRASS}
 for(let k=0;k<9;k++){let x=rand(W),y=rand(H),r=3+rand(5);for(let yy=-r;yy<=r;yy++)for(let xx=-r;xx<=r;xx++)if(x+xx>=0&&y+yy>=0&&x+xx<W&&y+yy<H&&xx*xx+yy*yy<r*r)terrain[y+yy][x+xx]=T.WATER}
 for(let k=0;k<150;k++){let x=rand(W),y=rand(H);if(terrain[y][x]===T.GRASS)terrain[y][x]=T.BERRY}
}
function makePerson(name,sex,x,y,age){return{id:uid(),name,sex,x,y,age:age===undefined?20:age,hp:100,hunger:8,thirst:6,energy:95,warmth:90,mood:70,skill:rand(3),traits:choose(['добрый','смелый','осторожный','вспыльчивый','трудолюбивый','любознательный']),partner:null,parents:[],children:[],home:null,job:choose(['собиратель','строитель','фермер','исследователь']),goal:'Осматривает окрестности',state:'idle',cool:0,anger:0,pregnant:0,inventory:{food:0,wood:0,stone:0,herb:0},alive:true,walk:0,dir:1,ageStage:'adult'}}
function newWorld(){
 world={day:1,hour:8,wood:24,food:28,water:30,stone:10,herbs:4,houses:[],fields:[],camp:{x:W/2|0,y:H/2|0},law:'Совет старейшин',population:[],tech:{farming:0,building:0,medicine:0},generation:1,dead:0,season:0};
 makeTerrain();objects=[];animals=[];logs=[];selected=null;tick=0;camera.x=world.camp.x*S- canvas.clientWidth/2;camera.y=world.camp.y*S-canvas.clientHeight/2;
 const cx=world.camp.x,cy=world.camp.y;
 world.population.push(makePerson('Адам','М',cx-2,cy,26),makePerson('Ева','Ж',cx+2,cy,24),makePerson(choose(NAMES_M),'М',cx,cy+2,19),makePerson(choose(NAMES_F),'Ж',cx,cy-2,21));
 for(let i=0;i<9;i++){let x=clamp(cx+rand(17)-8,2,W-3),y=clamp(cy+rand(17)-8,2,H-3);if(tile(x,y)===T.GRASS)animals.push({id:uid(),type:choose(['заяц','олень','лиса']),x,y,hp:30,dir:1})}
 say('Четверо первопоселенцев основали лагерь. Начинается новая история.');renderUI();draw()
}
function nearest(p,types){let best=null,bd=1e9;for(let y=0;y<H;y++)for(let x=0;x<W;x++){let t=terrain[y][x];if(types.includes(t)){let d=Math.abs(p.x-x)+Math.abs(p.y-y);if(d<bd){bd=d;best={x,y,d}}}}return best}
function walkTo(p,t){if(!t)return;let dx=Math.sign(t.x-p.x),dy=Math.sign(t.y-p.y);let opts=chance(.5)?[[dx,0],[0,dy]]:[[0,dy],[dx,0]];for(let a of opts){let nx=p.x+a[0],ny=p.y+a[1];if((a[0]||a[1])&&tile(nx,ny)!==T.WATER&&!objects.some(o=>o.x===nx&&o.y===ny)){p.x=nx;p.y=ny;p.dir=a[0]||p.dir;p.walk++;return}}}
function makeHouse(p){if(world.wood<12||world.stone<3)return false;world.wood-=12;world.stone-=3;let h={id:uid(),x:p.x,y:p.y,owner:p.id,level:1};world.houses.push(h);p.home=h.id;world.tech.building++;say(p.name+' построил(а) тёплый дом.');return true}
function harvest(p,type){if(type===T.BERRY){world.food+=3;terrain[p.y][p.x]=T.GRASS;p.inventory.food++;p.goal='Собирает ягоды';}else if(type===T.FOREST){world.wood+=2;terrain[p.y][p.x]=T.GRASS;p.inventory.wood++;p.goal='Заготавливает древесину';}else if(type===T.ROCK){world.stone+=2;terrain[p.y][p.x]=T.GRASS;p.inventory.stone++;p.goal='Добывает камень';}}
function pair(a,b){a.partner=b.id;b.partner=a.id;a.mood=100;b.mood=100;say(a.name+' и '+b.name+' создали семью.')}
function giveBirth(m){let dad=world.population.find(q=>q.id===m.partner);if(!dad||!dad.alive||world.population.length>35)return;m.pregnant=0;let sex=chance(.5)?'М':'Ж',b=makePerson(choose(sex==='М'?NAMES_M:NAMES_F),sex,m.x,m.y,0);b.parents=[m.id,dad.id];m.children.push(b.id);dad.children.push(b.id);world.population.push(b);world.generation++;say('В семье '+m.name+' и '+dad.name+' родился ребёнок '+b.name+'.')}
function conflict(a,b){if(!a.alive||!b.alive)return;a.anger=0;b.anger=0;a.mood-=18;b.mood-=22;b.hp-=12+rand(16);a.goal='Ссорится с '+b.name;b.goal='Защищается от '+a.name;say(a.name+' и '+b.name+' подрались.');if(b.hp<=0){b.alive=false;world.dead++;say(b.name+' погиб(ла) после драки. Совет расследует конфликт.')}else if(world.law==='Жёсткий закон'&&b.hp<20&&chance(.1)){b.alive=false;world.dead++;say('Тяжёлое преступление закончилось гибелью '+b.name+'.')} }
function ai(p){
 if(!p.alive)return;p.age+=.00018;p.hunger=clamp(p.hunger+.22,0,100);p.thirst=clamp(p.thirst+.31,0,100);p.energy=clamp(p.energy-.10,0,100);p.warmth=clamp(p.warmth-(weather==='Дождь'?.2:.05),0,100);p.cool=Math.max(0,p.cool-1);
 if(p.hunger>85||p.thirst>88||p.warmth<10)p.hp-=.7;if(p.hp<=0){p.alive=false;world.dead++;say(p.name+' умер(ла) от истощения.');return}
 if(p.age<13)p.ageStage='child';else if(p.age<18)p.ageStage='teen';else p.ageStage='adult';
 if(p.ageStage==='child'){p.goal='Играет возле дома';p.energy=clamp(p.energy+.5,0,100);return}
 if(p.ageStage==='teen'&&chance(.05)){p.goal='Учится у взрослых';return}
 if(p.thirst>35&&world.water>0){world.water--;p.thirst=clamp(p.thirst-32,0,100);p.goal='Пьёт воду';return}
 if(p.hunger>35&&world.food>0){world.food--;p.hunger=clamp(p.hunger-28,0,100);p.goal='Ест у костра';return}
 if(p.energy<15){p.energy=clamp(p.energy+3,0,100);p.goal='Отдыхает';return}
 if(p.warmth<35&&world.wood>0){world.wood--;p.warmth=clamp(p.warmth+20,0,100);p.goal='Греется у костра';return}
 if(p.pregnant>0){p.pregnant++;p.goal='Ждёт ребёнка';if(p.pregnant>90)giveBirth(p);return}
 let close=world.population.filter(q=>q.alive&&q.id!==p.id&&dist(p,q)<=2);
 if(close.length){let q=close[0];if(p.partner===q.id){p.goal='Общается с '+q.name;p.mood=clamp(p.mood+1,0,100);if(p.sex==='Ж'&&p.age>18&&p.age<42&&q.sex==='М'&&chance(.002)&&p.children.length<4){p.pregnant=1;say(p.name+' ждёт ребёнка.')}return}
 if(p.anger>40||q.anger>65){conflict(p,q);return}
 if(!p.partner&&!q.partner&&p.age>=18&&q.age>=18&&p.sex!==q.sex&&chance(.012))pair(p,q);
 else if(chance(.08)){p.goal='Разговаривает с '+q.name;p.mood=clamp(p.mood+4,0,100);q.mood=clamp(q.mood+3,0,100);return}
 }
 if(!p.home&&world.houses.length<Math.ceil(world.population.filter(q=>q.alive).length/3)&&world.wood>=12&&world.stone>=3){let camp=world.camp;if(dist(p,camp)<4){makeHouse(p);return}}
 let target,type;
 if(world.food<25||p.hunger>30){target=nearest(p,[T.BERRY]);type=T.BERRY}
 else if(world.water<18||p.thirst>28){target=nearest(p,[T.WATER]);type=T.WATER}
 else if(world.wood<35){target=nearest(p,[T.FOREST]);type=T.FOREST}
 else if(world.stone<18){target=nearest(p,[T.ROCK]);type=T.ROCK}
 else if(world.fields.length<3&&world.food>20&&p.job==='фермер'){world.fields.push({x:p.x,y:p.y,progress:0});say(p.name+' разбил(а) огород.');return}
 else{p.goal='Исследует окрестности';walkTo(p,{x:rand(W),y:rand(H)});return}
 if(!target){walkTo(p,{x:rand(W),y:rand(H)});return}
 if(type===T.WATER){if(target.d<=1){world.water+=4;p.goal='Набирает воду'}else{p.goal='Ищет воду';walkTo(p,target)}}
 else if(target.d<=1)harvest(p,type);else{p.goal=type===T.BERRY?'Ищет ягоды':type===T.FOREST?'Рубит деревья':'Добывает камень';walkTo(p,target)}
}
function step(){
 tick++;world.hour+=.18;if(world.hour>=24){world.hour-=24;world.day++;if(world.day%12===0){world.season=(world.season+1)%4;weather=choose(['Ясно','Дождь','Ветер','Туман']);say('Наступил сезон: '+['Весна','Лето','Осень','Зима'][world.season]+'. Погода: '+weather+'.')}
 for(let p of world.population.filter(q=>q.alive)){if(p.age>70&&chance(.008)){p.alive=false;world.dead++;say(p.name+' умер(ла) от старости.')}if(p.partner&&!world.population.some(q=>q.id===p.partner&&q.alive))p.partner=null}
 }
 for(let p of world.population.filter(q=>q.alive))ai(p);
 for(let f of world.fields){f.progress++;if(f.progress>120){world.food+=8;f.progress=0;say('На огороде созрел урожай. Собрано 8 порций еды.')}}
 if(tick%40===0){for(let a of animals){if(chance(.45)){let nx=clamp(a.x+rand(3)-1,1,W-2),ny=clamp(a.y+rand(3)-1,1,H-2);if(tile(nx,ny)!==T.WATER){a.x=nx;a.y=ny}}}if(chance(.12)){let p=choose(world.population.filter(q=>q.alive));if(p){p.anger+=30;say(p.name+' раздражён(а): назревает спор.')}}}
 if(tick%20===0)renderUI();
}
function drawTree(x,y){ctx.fillStyle='#5b3d29';ctx.fillRect(x+13,y+17,6,15);ctx.fillStyle='#173e2c';ctx.beginPath();ctx.arc(x+16,y+13,13,0,7);ctx.fill();ctx.fillStyle='#2c6740';ctx.beginPath();ctx.arc(x+10,y+10,8,0,7);ctx.fill();ctx.fillStyle='rgba(255,255,255,.08)';ctx.beginPath();ctx.arc(x+11,y+7,4,0,7);ctx.fill()}
function drawPerson(p,px,py){
 let age=p.ageStage==='child'?.66:p.ageStage==='teen'?.84:1,sz=age*1;
 let bob=Math.sin(p.walk*.8+tick*.2)*1.5,body=p.sex==='Ж'?'#b85d83':'#d3a14e',hair=p.sex==='Ж'?'#352331':'#493327';
 ctx.fillStyle='rgba(0,0,0,.28)';ctx.beginPath();ctx.ellipse(px,py+9*sz,9*sz,3*sz,0,0,7);ctx.fill();
 ctx.lineCap='round';ctx.strokeStyle='#30251f';ctx.lineWidth=3*sz;ctx.beginPath();ctx.moveTo(px-3*sz,py+3*sz+bob);ctx.lineTo(px-4*sz,py+10*sz);ctx.moveTo(px+3*sz,py+3*sz+bob);ctx.lineTo(px+4*sz,py+10*sz);ctx.stroke();
 ctx.fillStyle=body;ctx.beginPath();ctx.roundRect(px-6*sz,py-5*sz+bob,12*sz,12*sz,3);ctx.fill();
 ctx.strokeStyle='#d7b18b';ctx.lineWidth=2*sz;ctx.beginPath();ctx.moveTo(px-5*sz,py-2*sz+bob);ctx.lineTo(px-9*sz,py+4*sz+bob);ctx.moveTo(px+5*sz,py-2*sz+bob);ctx.lineTo(px+9*sz,py+3*sz+bob);ctx.stroke();
 ctx.fillStyle='#efc99e';ctx.beginPath();ctx.arc(px,py-11*sz+bob,5.5*sz,0,7);ctx.fill();
 ctx.fillStyle=hair;ctx.beginPath();ctx.arc(px,py-13*sz+bob,5.8*sz,Math.PI,Math.PI*2);ctx.fill();ctx.fillRect(px-5.5*sz,py-14*sz+bob,2*sz,5*sz);
 ctx.fillStyle='#2b211e';ctx.beginPath();ctx.arc(px+2*sz,py-11*sz+bob,.8*sz,0,7);ctx.fill();
 if(p.partner){ctx.fillStyle='#ff9dbb';ctx.font='11px sans-serif';ctx.fillText('♥',px+7,py-17)}
 if(p.id===selected){ctx.strokeStyle='#ffe28b';ctx.lineWidth=2;ctx.beginPath();ctx.ellipse(px,py,13*sz,19*sz,0,0,7);ctx.stroke();ctx.fillStyle='#fff4c7';ctx.font='bold 10px system-ui';ctx.textAlign='center';ctx.fillText(p.name,px,py-23*sz)}
}
function draw(){
 let cw=canvas.clientWidth,ch=canvas.clientHeight,dpr=Math.min(devicePixelRatio||1,2);if(canvas.width!==Math.floor(cw*dpr)||canvas.height!==Math.floor(ch*dpr)){canvas.width=Math.floor(cw*dpr);canvas.height=Math.floor(ch*dpr)}
 ctx.setTransform(dpr,0,0,dpr,0,0);ctx.clearRect(0,0,cw,ch);
 let startX=Math.floor(camera.x/S),startY=Math.floor(camera.y/S),offX=-(camera.x%S),offY=-(camera.y%S);
 for(let sy=0;sy<Math.ceil(ch/S)+2;sy++)for(let sx=0;sx<Math.ceil(cw/S)+2;sx++){let x=startX+sx,y=startY+sy;if(x<0||y<0||x>=W||y>=H)continue;let px=offX+sx*S,py=offY+sy*S,t=tile(x,y);
 ctx.fillStyle=t===T.WATER?'#286e83':t===T.ROCK?'#777c70':t===T.FOREST?'#315d39':t===T.BERRY?'#4b7a42':'#557c45';ctx.fillRect(px,py,S+1,S+1);
 ctx.fillStyle='rgba(255,255,255,.035)';ctx.fillRect(px+rand(1),py+rand(1),S,S);
 if(t===T.WATER){ctx.strokeStyle='rgba(173,225,235,.3)';ctx.lineWidth=1;ctx.beginPath();ctx.moveTo(px+6,py+14+Math.sin(tick*.1+x)*3);ctx.lineTo(px+25,py+14);ctx.stroke()}
 if(t===T.FOREST)drawTree(px,py);
 if(t===T.BERRY){ctx.fillStyle='#315a32';ctx.beginPath();ctx.arc(px+16,py+17,9,0,7);ctx.fill();for(let i=0;i<5;i++){ctx.fillStyle='#dc5b76';ctx.beginPath();ctx.arc(px+10+(i%3)*5,py+13+(i%2)*7,2,0,7);ctx.fill()}}
 if(t===T.ROCK){ctx.fillStyle='#a1a69b';ctx.beginPath();ctx.moveTo(px+6,py+22);ctx.lineTo(px+12,py+9);ctx.lineTo(px+25,py+13);ctx.lineTo(px+28,py+24);ctx.closePath();ctx.fill()}
 }
 for(let h of world.houses){let px=h.x*S-camera.x,py=h.y*S-camera.y;ctx.fillStyle='#9a7045';ctx.fillRect(px+4,py+12,24,17);ctx.fillStyle='#59382d';ctx.beginPath();ctx.moveTo(px+1,py+14);ctx.lineTo(px+16,py+1);ctx.lineTo(px+31,py+14);ctx.closePath();ctx.fill();ctx.fillStyle='#e5c38c';ctx.fillRect(px+8,py+18,6,6);ctx.fillRect(px+20,py+18,5,5);ctx.fillStyle='#422d25';ctx.fillRect(px+14,py+22,6,7)}
 for(let f of world.fields){let px=f.x*S-camera.x,py=f.y*S-camera.y;ctx.fillStyle='#805a38';ctx.fillRect(px+3,py+4,26,24);for(let i=0;i<4;i++){ctx.fillStyle='#71a84d';ctx.fillRect(px+5+i*6,py+8,3,16)}}
 for(let a of animals){let px=a.x*S-camera.x+16,py=a.y*S-camera.y+18;ctx.fillStyle=a.type==='олень'?'#b18a59':a.type==='лиса'?'#bf7442':'#b8b4a8';ctx.beginPath();ctx.ellipse(px,py,7,5,0,0,7);ctx.fill();ctx.beginPath();ctx.arc(px+5,py-4,3,0,7);ctx.fill()}
 for(let p of world.population.filter(q=>q.alive)){let px=p.x*S-camera.x+16,py=p.y*S-camera.y+16;drawPerson(p,px,py)}
 let hour=world.hour,night=hour<6||hour>19,alpha=night?.42:hour<8||hour>17?.18:0;if(alpha){ctx.fillStyle='rgba(8,15,38,'+alpha+')';ctx.fillRect(0,0,cw,ch)}
 if(weather==='Дождь'){ctx.strokeStyle='rgba(190,220,255,.4)';ctx.lineWidth=1;for(let i=0;i<45;i++){let x=(i*47+tick*3)%cw,y=(i*29+tick*5)%ch;ctx.beginPath();ctx.moveTo(x,y);ctx.lineTo(x-4,y+9);ctx.stroke()}}
 ctx.fillStyle='rgba(13,25,17,.72)';ctx.beginPath();ctx.roundRect(9,9,190,26,8);ctx.fill();ctx.fillStyle='#f1f3df';ctx.font='bold 12px system-ui';ctx.fillText('🌿 ALIVE · '+['Весна','Лето','Осень','Зима'][world.season]+' · '+weather,17,27);
 if(toastUntil>Date.now()){ctx.fillStyle='rgba(15,25,18,.88)';ctx.beginPath();ctx.roundRect(10,ch-42,Math.min(cw-20,420),30,8);ctx.fill();ctx.fillStyle='#fff1c4';ctx.font='12px system-ui';ctx.fillText(toast,18,ch-22,Math.min(cw-40,400))}
}
function renderUI(){
 let live=world.population.filter(p=>p.alive);ui.day.textContent='День '+world.day+' · '+String(Math.floor(world.hour)).padStart(2,'0')+':00 · '+['Весна','Лето','Осень','Зима'][world.season];
 ui.goal.textContent='Живы: '+live.length+' · поколение: '+world.generation+' · погибло: '+world.dead;
 ui.people.innerHTML=world.population.slice().sort((a,b)=>b.alive-a.alive).map(p=>'<div class="person" data-id="'+p.id+'">'+(p.alive?'🟢':'⚫')+' <b>'+p.name+'</b> · '+(p.sex==='Ж'?'♀':'♂')+' · '+Math.floor(p.age)+' лет<br><span class="muted">'+(p.alive?p.goal:'умер(ла)')+' · здоровье '+Math.max(0,Math.floor(p.hp))+'%</span></div>').join('');
 ui.resources.innerHTML='🪵 '+world.wood+' дерева　🪨 '+world.stone+' камня<br>🍓 '+world.food+' еды　💧 '+world.water+' воды<br>🏠 '+world.houses.length+' домов　🌱 '+world.fields.length+' огородов<br>🌿 '+world.herbs+' трав　🧬 '+world.tech.medicine+' медицина';
 ui.law.textContent='Порядок: '+world.law+' · погибших: '+world.dead;
 ui.log.innerHTML=logs.map(s=>'<div>'+s+'</div>').join('');
 if(selected){let p=world.population.find(q=>q.id===selected);ui.inspect.textContent=p?(p.name+' · '+p.goal+' · характер: '+p.traits+' · профессия: '+p.job):'Выбери жителя на карте';}else ui.inspect.textContent='Нажми на жителя или его имя, чтобы увидеть подробности.';
}
function save(){localStorage.setItem('alive-save-1',JSON.stringify({world,terrain,animals,logs,tick,weather}));say('Мир сохранён на телефоне.')}
function load(){try{let d=JSON.parse(localStorage.getItem('alive-save-1'));if(!d)throw 0;world=d.world;terrain=d.terrain;animals=d.animals||[];logs=d.logs||[];tick=d.tick||0;weather=d.weather||'Ясно';say('Сохранённый мир восстановлен.')}catch(e){say('Сохранение не найдено. Сначала создай мир.')}renderUI()}
function actButton(fn){fn();renderUI()}
function resize(){draw()}
canvas.addEventListener('pointerdown',e=>{drag={x:e.clientX,y:e.clientY,cx:camera.x,cy:camera.y,moved:false};canvas.setPointerCapture(e.pointerId)});
canvas.addEventListener('pointermove',e=>{if(!drag)return;let dx=e.clientX-drag.x,dy=e.clientY-drag.y;if(Math.abs(dx)+Math.abs(dy)>5)drag.moved=true;if(drag.moved){camera.x=clamp(drag.cx-dx,0,W*S-canvas.clientWidth);camera.y=clamp(drag.cy-dy,0,H*S-canvas.clientHeight)}});
canvas.addEventListener('pointerup',e=>{if(!drag)return;if(!drag.moved){let x=Math.floor((e.offsetX+camera.x)/S),y=Math.floor((e.offsetY+camera.y)/S),p=world.population.find(q=>q.alive&&q.x===x&&q.y===y);if(p){selected=p.id;renderUI()}else if(x>=0&&y>=0&&x<W&&y<H&&tile(x,y)!==T.WATER){selected=null}}drag=null});
ui.people.addEventListener('click',e=>{let el=e.target.closest('[data-id]');if(el){selected=el.dataset.id;let p=world.population.find(q=>q.id===selected);if(p){camera.x=clamp(p.x*S-canvas.clientWidth/2,0,W*S-canvas.clientWidth);camera.y=clamp(p.y*S-canvas.clientHeight/2,0,H*S-canvas.clientHeight)}renderUI()}});
ui.pause.onclick=()=>{running=!running;ui.pause.textContent=running?'Пауза':'Продолжить'};
ui.speed.onclick=()=>{speed=speed===1?2:speed===2?4:1;ui.speed.textContent='Скорость ×'+speed};
ui.save.onclick=save;ui.load.onclick=load;ui.reset.onclick=()=>{if(confirm('Начать новый мир? Старое сохранение останется.'))newWorld()};
ui.build.onclick=()=>actButton(()=>{let p=world.population.find(q=>q.id===selected&&q.alive)||world.population.find(q=>q.alive);if(p&&makeHouse(p)){}else say('Для дома нужно 12 дерева и 3 камня.')});
ui.farm.onclick=()=>actButton(()=>{if(world.food<8){say('Нужно 8 еды, чтобы разбить огород.');return}world.food-=8;world.fields.push({x:world.camp.x+rand(5)-2,y:world.camp.y+rand(5)-2,progress:0});say('Поселенцы разбили огород. Урожай появится через несколько игровых дней.')});
ui.craft.onclick=()=>actButton(()=>{if(world.wood>=4&&world.stone>=2){world.wood-=4;world.stone-=2;world.tech.medicine++;say('Созданы инструменты и аптечные принадлежности. Навык поселения растёт.')}else say('Для инструментов нужно 4 дерева и 2 камня.')});
ui.lawBtn.onclick=()=>actButton(()=>{world.law=world.law==='Совет старейшин'?'Жёсткий закон':'Совет старейшин';say('Новый порядок: '+world.law+'.')});
window.addEventListener('resize',resize);
function loop(){let now=Date.now();if(running){let n=speed;while(n--)step()}draw();if(tick-last>=5){renderUI();last=tick}requestAnimationFrame(loop)}
newWorld();requestAnimationFrame(loop);
})();