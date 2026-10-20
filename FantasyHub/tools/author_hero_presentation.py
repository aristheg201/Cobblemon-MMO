#!/usr/bin/env python3
"""Original compact skill glyphs and bilingual descriptions, sourced from the gameplay catalog."""
import json,math
from pathlib import Path
from PIL import Image,ImageDraw
root=Path(__file__).resolve().parents[1];assets=root/'client/src/main/resources/assets/fantasyhub';catalog=json.loads((root/'core/src/main/resources/default/heroes.json').read_text())
text={
'SPIDER_MAN':[
('Dash through up to three distinct foes; each hit causes bleeding and slows.','Lướt qua tối đa ba kẻ địch khác nhau; mỗi đòn gây chảy máu và làm chậm.'),
('Swing in an arc and slam the actual landing point, knocking nearby enemies upward.','Đu theo quỹ đạo cong rồi dậm tại điểm đáp, hất tung kẻ địch xung quanh.'),
('Fire eight web shots. Hits stack movement and attack slow; the last shot blinds.','Bắn tám phát tơ. Đòn trúng cộng dồn giảm tốc chạy/đánh; phát cuối gây mù.'),
('Cocoon and stun a target. Recast within three seconds to slam it for area damage.','Bọc kén làm choáng mục tiêu. Bấm lại trong ba giây để quật gây sát thương vùng.'),
('Lift the target and strike five times while both fighters remain airborne.','Hất mục tiêu lên và đánh năm đòn trong khi cả hai được giữ trên không.'),
('Reduce incoming damage by 90% for two seconds. A melee hit triggers one evasive counter and root.','Giảm 90% sát thương trong hai giây. Đòn cận chiến kích hoạt một lần né ra sau và trói chân.'),
('Trap and blind the area, unleash six slashes, then detonate the web with true damage.','Trói chân, gây mù cả vùng, chém sáu nhát rồi kích nổ mạng nhện bằng sát thương chuẩn.')],
'SORCERER_SUPREME':[
('Strike with an eldritch whip; recast within two seconds to pull and slow the same target.','Quất roi ma thuật; bấm lại trong hai giây để kéo và làm chậm cùng mục tiêu.'),
('Conjure two mandalas for three seconds. Block 70% of frontal damage and reflect a magic bolt.','Tạo hai ma trận trong ba giây. Chặn 70% sát thương phía trước và phản đạn ma thuật.'),
('Create a mirror zone for three seconds; damage enemies, seal mobility skills and reverse projectiles.','Tạo vùng gương ba giây: gây sát thương, khóa kỹ năng di chuyển và phản đạn bay.'),
('Leave an invulnerable body while your astral self flies for 3.5 seconds; return and recover 15% Mana.','Để lại thể xác bất tử, bay dưới dạng linh hồn 3,5 giây; nhập lại và hồi 15% Mana.'),
('Open linked portals for six seconds. Allies pass safely; enemies take damage and are stunned.','Mở hai cổng liên kết sáu giây. Đồng minh đi qua an toàn; kẻ địch chịu sát thương và choáng.'),
('Create eight magical images that bind, silence and damage enemies at their center.','Tạo tám ảnh ma thuật, gây sát thương, trói chân và câm lặng kẻ địch ở tâm.'),
('Freeze the area, bind enemies with Crimson Bands, then shatter time with true magic damage.','Đóng băng cả vùng, trói bằng Crimson Bands rồi phá vỡ thời gian bằng sát thương phép chuẩn.')],
'IRON_MAN':[
('Fire a palm repulsor and knock the target back; firing while airborne also recoils the suit.','Bắn xung lòng bàn tay đẩy lùi mục tiêu; khi đang bay, giáp cũng bị giật lùi.'),
('Launch twelve guided missiles into the designated area; explosions slow their victims.','Phóng mười hai tên lửa dẫn đường vào vùng chỉ định; vụ nổ làm chậm mục tiêu.'),
('Cross two targeting lasers, reduce armor and burn for 5% maximum Health over three seconds.','Cắt hai tia laser chữ X, giảm giáp và đốt tổng cộng 5% Máu tối đa trong ba giây.'),
('Dash six blocks, remove slows and gain a three-second shield plus damage reduction.','Lướt sáu ô, xóa làm chậm, nhận khiên và giảm sát thương trong ba giây.'),
('Charge for 0.8 seconds, then pierce enemies along an 18-block chest beam.','Tích lực 0,8 giây rồi bắn pháo ngực xuyên mục tiêu trên đường dài 18 ô.'),
('Call two orbiting sentries for eight seconds. They fire independently and provide 10% damage reduction.','Gọi hai drone bay quanh trong tám giây. Chúng tự bắn mục tiêu và cho 10% giảm sát thương.'),
('Lock an area, fire an orbital true-damage beam, destroy shields and apply an EMP silence.','Khóa vùng, bắn pháo vệ tinh gây sát thương chuẩn, phá khiên và gây câm lặng bằng EMP.')],
'THOR':[
('Throw Stormbreaker outward, stun at the endpoint and strike again on its slowing return path.','Ném Stormbreaker, gây choáng tại điểm cuối rồi đánh thêm và làm chậm trên đường thu rìu.'),
('Leap and slam with thunder, damaging and launching enemies around the landing point.','Nhảy vọt rồi dậm sấm, gây sát thương và hất tung kẻ địch quanh điểm đáp.'),
('Spin Stormbreaker for two seconds, repeatedly strike nearby enemies and intercept incoming projectiles.','Xoay Stormbreaker hai giây, đánh liên tục kẻ địch gần và triệt tiêu đạn bay vào.'),
('Release a lightning cleave that penetrates 50% armor, leaving a three-second charged trail.','Phóng nhát cắt sét xuyên 50% giáp, để lại vệt điện gây sát thương trong ba giây.'),
('Call Bifrost: cleanse and shield allies while damaging and repelling enemies.','Gọi Bifrost: thanh tẩy và tạo khiên cho đồng minh, gây sát thương và đẩy lùi địch.'),
('Awaken for ten seconds: gain damage, attack speed, chain lightning and faster Stormbreaker abilities.','Thức tỉnh mười giây: tăng sát thương, tốc đánh, sét lan và giảm hồi chiêu kỹ năng Stormbreaker.'),
('Leap into a thunderous true-damage impact, followed by four lightning strikes across the area.','Nhảy vào cú dậm sét gây sát thương chuẩn, tiếp nối bằng bốn đợt sét trong vùng.')],
'FLASH':[
('Toggle Super-Speed and Jump Boost III; consumes two Speed Force each second until switched off.','Bật/tắt siêu tốc và Nhảy Cao III; tiêu hao hai Speed Force mỗi giây cho đến khi tắt.'),
('Slow nearby enemies and their attacks for three seconds; player slows respect the PvP cap.','Làm chậm chạy và đánh của địch trong ba giây; làm chậm người chơi chịu giới hạn PvP.'),
('Throw lightning that stuns its first victim and chains to up to two nearby foes.','Phóng sét gây choáng mục tiêu đầu và lan sang tối đa hai kẻ địch gần đó.'),
('Form a damaging vacuum tornado that pulls enemies inward and finally launches them upward.','Tạo lốc chân không gây sát thương, hút địch vào tâm rồi hất tung ở đợt cuối.'),
('Phase through bounded, loaded terrain for two seconds and evade ordinary physical melee hits.','Xuyên địa hình đã tải trong hai giây và né đòn cận chiến vật lý thường.'),
('Cleanse debuffs and steal 30% movement speed from the nearest enemy for five seconds.','Xóa hiệu ứng xấu và hút 30% tốc chạy của kẻ địch gần nhất trong năm giây.'),
('Charge the area, briefly freeze foes, then dash into a true-damage punch and shockwave.','Tích lực trong vùng, đóng băng địch ngắn hạn rồi lao tới đấm sát thương chuẩn và tạo sóng xung kích.')],
'BATMAN':[
('Throw three slowing Batarangs. Recast within two seconds for an explosive interrupting Batarang.','Phi ba Batarang làm chậm. Bấm lại trong hai giây để ném Batarang nổ ngắt chiêu.'),
('Grapple a foe to pull, stun and weaken armor; grapple terrain to travel toward the anchor.','Móc địch để kéo, gây choáng và giảm giáp; móc địa hình để lướt tới điểm bám.'),
('Blind and silence enemies in smoke; become invisible and empower the next normal hit to 250%.','Gây mù và câm lặng địch trong khói; tàng hình và cường hóa đòn thường kế tiếp lên 250%.'),
('Reveal enemies through walls for six seconds and expose them to 20% more incoming damage.','Phát hiện địch xuyên tường sáu giây, khiến chúng chịu thêm 20% sát thương từ mọi nguồn.'),
('Disrupt a selected enemy, silence its skills and destroy defensive shields.','Phá sóng mục tiêu, khóa kỹ năng và phá hủy các lớp khiên.'),
('Sweep a frontal half-circle with the cape, stun foes and strike four times, finishing with a launch.','Quạt áo choàng nửa vòng phía trước, gây choáng rồi đánh bốn đòn, kết thúc bằng hất tung.'),
('Mark and silence an area, call a Batwing bombardment, then stun and scatter surviving enemies.','Đánh dấu, câm lặng cả vùng, gọi Batwing oanh tạc rồi gây choáng và đẩy văng địch còn sống.')]
}
passives_vi={
'SPIDER_MAN':['Phản Xạ Nhện: 20% né đòn; né/lướt tích tối đa năm tầng tăng tốc và chí mạng.','Bám Tường: giữ tiến sát tường để leo; miễn sát thương rơi.'],
'SORCERER_SUPREME':['Áo Choàng Bay: trang bị áo choàng ở ô Back, nhấn đúp Nhảy để bay; tự tạo khiên khi Máu dưới 30%.','Trực Giác Thần Thức: năm kỹ năng trúng cường hóa chiêu kế tiếp và giảm hồi chiêu.'],
'IRON_MAN':['Bay Phản Lực: nhấn đúp Nhảy để bật bay; tiêu hao Arc Power khi bay.','Quá Tải Lõi Arc: dùng chiêu tích nhiệt, đủ nhiệt nhận Overdrive sáu giây.'],
'THOR':['Bay Asgard: đủ bộ Thor và áo choàng ô Back, nhấn đúp Nhảy để bay; vũ khí trở về gây thêm sát thương.','Giáp Sét: đánh thường tích tối đa năm tầng tăng giáp/sát thương sét; phản đòn cận chiến.'],
'FLASH':['Speed Force: di chuyển tăng tối đa năm cấp tốc độ/sát thương; hồi Máu gấp bốn, kháng độc, Wither và buồn nôn.','Bắt Đạn: từ cấp tốc độ bốn, tự triệt tiêu đạn đang bay tới trong ba ô.'],
'BATMAN':['Freeflow: tối đa mười tầng tăng tốc đánh/sát thương chí mạng; 15% đỡ đòn cận chiến phía trước.','Áo Choàng Lượn: giữ Nhảy để lượn; tự thoát bằng khói khi Máu dưới 25%.']}
lookup={};icons={}
langs={lang:json.loads((assets/f'lang/{lang}.json').read_text()) for lang in ['en_us','vi_vn']}
for hero in catalog['heroes']:
 hid=hero['id'];key='fantasyhub.hero.name.'+hid.lower();lookup[hero['name']]=key;langs['en_us'][key]=hero['name'];langs['vi_vn'][key]=hero['name_vi']
 for i,passive in enumerate(hero['passives']):
  key='fantasyhub.hero.passive.'+hid.lower()+'.'+str(i+1);lookup[passive]=key;langs['en_us'][key]=passive;langs['vi_vn'][key]=passives_vi[hid][i]
 for a,(en,vi) in zip(hero['abilities'],text[hid],strict=True):
  key='fantasyhub.skill.'+a['id'].lower();langs['en_us'][key]=a['name'];langs['vi_vn'][key]=a['name_vi'];lookup[a['name']]=key
  for lang,desc in [('en_us',en),('vi_vn',vi)]:langs[lang]['fantasyhub.hero.description.'+a['id'].lower()]=desc
 for lang in langs:langs[lang]['fantasyhub.hero.resource.'+hid.lower()]=hero['resource_name_vi'] if lang=='vi_vn' else hero['resource_name']
for suffix,en,vi in [('heat','Heat %s%%','Nhiệt %s%%'),('speed','Speed %s / 5','Tốc độ %s / 5'),('instinct','Instinct %s / 5','Phản xạ %s / 5'),('mystic','Mystic %s / 5','Thần thức %s / 5'),('static','Static %s / 5','Điện tích %s / 5'),('combo','Combo %s / 10','Combo %s / 10'),('flight','Flight','Đang bay'),('awakened','Awakened','Thức tỉnh'),('phasing','Phasing','Xuyên thể')]:
 langs['en_us']['fantasyhub.hero.hud.'+suffix]=en;langs['vi_vn']['fantasyhub.hero.hud.'+suffix]=vi
for lang,data in langs.items():
 data['item.fantasyhub.stormbreaker']='Stormbreaker'
 (assets/f'lang/{lang}.json').write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n')
(assets/'heroes-text-lookup.json').write_text(json.dumps(lookup,indent=2,ensure_ascii=False)+'\n')
print('Bilingual names/descriptions: 42 skills, 6 heroes, 12 passives.')
